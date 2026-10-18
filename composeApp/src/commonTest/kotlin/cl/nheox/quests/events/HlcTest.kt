package cl.nheox.quests.events

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property tests for the hybrid logical clock (§8.4).
 *
 * The property that matters is a *total* order whose lexicographic encoding
 * agrees with it, because replay determinism — and therefore sync correctness —
 * is built directly on top of it.
 */
class HlcTest {

    @Test
    fun `encoding round-trips`() {
        val clocks = listOf(
            Hlc(0, 0, "a"),
            Hlc(1, 2, "macbook"),
            Hlc(1_760_000_000_000, 99_999, "ubuntu"),
        )
        clocks.forEach { clock ->
            assertEquals(clock, Hlc.decode(clock.encode()), "round trip of $clock")
        }
    }

    @Test
    fun `malformed clocks decode to null rather than throwing`() {
        // A synced folder is a hostile environment; a hand-edited line must cost
        // one line, not the whole history.
        listOf("", "nonsense", "1:2", "a:b:c", "123:abc:device", "123:456:").forEach { bad ->
            assertNull(Hlc.decode(bad), "should not decode: '$bad'")
        }
        assertNotNull(Hlc.decode("0000000000123:00004:device"))
    }

    @Test
    fun `lexicographic order agrees with logical order`() {
        val random = Random(42)
        val clocks = List(400) {
            Hlc(
                wallMillis = random.nextLong(0, 2_000_000_000_000),
                counter = random.nextInt(0, 100_000),
                nodeId = listOf("macbook", "ubuntu", "desktop", "a", "zzz").random(random),
            )
        }

        clocks.forEach { left ->
            clocks.forEach { right ->
                val logical = left.compareTo(right)
                val lexical = left.encode().compareTo(right.encode())
                assertEquals(
                    logical.coerceIn(-1, 1),
                    lexical.coerceIn(-1, 1),
                    "disagreement between $left and $right",
                )
            }
        }
    }

    @Test
    fun `the order is total`() {
        // Two clocks are equal only if they are the same clock, which is what
        // makes a deterministic sort possible at all.
        val left = Hlc(10, 1, "a")
        val right = Hlc(10, 1, "b")
        assertTrue(left < right)
        assertTrue(right > left)
        assertEquals(0, left.compareTo(Hlc(10, 1, "a")))
    }

    @Test
    fun `successive stamps strictly increase`() {
        val generator = HlcGenerator("node", physicalNow = { 1_000 })
        var previous = generator.next()
        repeat(500) {
            val next = generator.next()
            assertTrue(next > previous, "$next should follow $previous")
            previous = next
        }
    }

    @Test
    fun `a frozen wall clock advances the counter`() {
        val generator = HlcGenerator("node", physicalNow = { 5_000 })
        val first = generator.next()
        val second = generator.next()
        assertEquals(first.wallMillis, second.wallMillis)
        assertEquals(first.counter + 1, second.counter)
    }

    @Test
    fun `a wall clock that jumps backwards does not produce a clock that goes backwards`() {
        // The whole reason an HLC exists: laptops sleep, NTP corrects, time
        // zones change. None of that may reorder history.
        var now = 10_000L
        val generator = HlcGenerator("node", physicalNow = { now })
        val before = generator.next()
        now = 1_000L
        val after = generator.next()
        assertTrue(after > before, "$after must still follow $before")
        assertEquals(before.wallMillis, after.wallMillis)
    }

    @Test
    fun `counter overflow borrows a millisecond instead of wrapping`() {
        // Wrapping would emit a clock that sorts before its own predecessor and
        // silently corrupt the ordering the sync design rests on.
        val generator = HlcGenerator("node", physicalNow = { 7_000 }, initial = Hlc(7_000, Hlc.MAX_COUNTER, "node"))
        val next = generator.next()
        assertEquals(7_001, next.wallMillis)
        assertEquals(0, next.counter)
        assertTrue(next > Hlc(7_000, Hlc.MAX_COUNTER, "node"))
    }

    @Test
    fun `observing a remote clock pushes the local one past it`() {
        val generator = HlcGenerator("local", physicalNow = { 1_000 })
        val remote = Hlc(50_000, 7, "remote")
        val merged = generator.observe(remote)
        assertTrue(merged > remote, "$merged must follow the remote $remote")
        assertEquals("local", merged.nodeId)

        // And everything written afterwards stays ahead of it.
        val afterwards = generator.next()
        assertTrue(afterwards > remote)
    }

    @Test
    fun `observing an older remote clock does not rewind the local one`() {
        val generator = HlcGenerator("local", physicalNow = { 100_000 })
        val ahead = generator.next()
        val merged = generator.observe(Hlc(10, 0, "remote"))
        assertTrue(merged > ahead)
    }

    @Test
    fun `two devices interleave into one consistent order`() {
        // Simulates two machines exchanging logs: each folds in what it sees,
        // and both must agree on the resulting sequence.
        var macTime = 1_000L
        var ubuntuTime = 900L
        val mac = HlcGenerator("macbook", physicalNow = { macTime })
        val ubuntu = HlcGenerator("ubuntu", physicalNow = { ubuntuTime })

        val stamps = mutableListOf<Hlc>()
        repeat(50) { round ->
            macTime += 3
            ubuntuTime += 2
            val fromMac = mac.next()
            stamps += fromMac
            ubuntu.observe(fromMac)
            val fromUbuntu = ubuntu.next()
            stamps += fromUbuntu
            mac.observe(fromUbuntu)
            if (round % 7 == 0) ubuntuTime -= 1 // clock drift, deliberately
        }

        assertEquals(stamps.size, stamps.distinct().size, "clocks must be unique")
        assertEquals(stamps, stamps.sorted(), "exchange order must already be sorted")
        assertEquals(
            stamps.map { it.encode() },
            stamps.map { it.encode() }.sorted(),
            "and the encoded form must sort the same way",
        )
    }

    @Test
    fun `encoded comparator matches decoded comparison`() {
        val random = Random(9)
        val encoded = List(200) {
            Hlc(random.nextLong(0, 1_000_000_000), random.nextInt(0, 500), "n${random.nextInt(5)}").encode()
        }
        val viaComparator = encoded.sortedWith(Hlc.ENCODED_ORDER)
        val viaDecode = encoded.sortedBy { Hlc.decode(it) }
        assertEquals(viaDecode, viaComparator)
    }

    @Test
    fun `unparseable clocks sort last rather than crashing the comparator`() {
        val mixed = listOf("broken", Hlc(5, 0, "a").encode(), "", Hlc(1, 0, "a").encode())
        val sorted = mixed.sortedWith(Hlc.ENCODED_ORDER)
        assertEquals(Hlc(1, 0, "a").encode(), sorted[0])
        assertEquals(Hlc(5, 0, "a").encode(), sorted[1])
    }
}
