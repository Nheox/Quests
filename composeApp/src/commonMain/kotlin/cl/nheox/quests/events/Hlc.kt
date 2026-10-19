package cl.nheox.quests.events

import java.util.concurrent.atomic.AtomicReference

/**
 * A hybrid logical clock.
 *
 * Wall-clock timestamps alone are not trustworthy across devices: laptops
 * sleep, clocks drift, time zones change and NTP corrections jump backwards. An
 * HLC keeps a wall-clock reading for human legibility, a logical counter that
 * breaks ties when two events share a millisecond, and the node id so that the
 * ordering is *total* rather than merely partial.
 *
 * A total order is the point. It is what makes replay deterministic regardless
 * of the order the logs happen to be read in, which is the property the whole
 * sync design rests on (see docs/SYNC.md).
 */
public data class Hlc(
    val wallMillis: Long,
    val counter: Int,
    val nodeId: String,
) : Comparable<Hlc> {
    /**
     * Encoded so that lexicographic string order matches [compareTo]. Thirteen
     * digits of milliseconds reach the year 2286, which is long enough.
     */
    public fun encode(): String = buildString {
        append(wallMillis.toString().padStart(13, '0'))
        append(':')
        append(counter.toString().padStart(5, '0'))
        append(':')
        append(nodeId)
    }

    override fun compareTo(other: Hlc): Int {
        wallMillis.compareTo(other.wallMillis).let { if (it != 0) return it }
        counter.compareTo(other.counter).let { if (it != 0) return it }
        return nodeId.compareTo(other.nodeId)
    }

    override fun toString(): String = encode()

    public companion object {
        public const val MAX_COUNTER: Int = 99_999

        public fun decode(encoded: String): Hlc? {
            val parts = encoded.split(':', limit = 3)
            if (parts.size != 3) return null
            val millis = parts[0].toLongOrNull() ?: return null
            val counter = parts[1].toIntOrNull() ?: return null
            if (parts[2].isEmpty()) return null
            return Hlc(millis, counter, parts[2])
        }

        /** Orders encoded clocks without decoding, for cheap sorting of raw lines. */
        public val ENCODED_ORDER: Comparator<String> = Comparator { a, b ->
            val left = decode(a)
            val right = decode(b)
            when {
                left != null && right != null -> left.compareTo(right)
                left == null && right == null -> a.compareTo(b)
                left == null -> 1
                else -> -1
            }
        }
    }
}

/**
 * The local clock, one per device. Thread-safe because reminders fire from a
 * background coroutine while the UI writes from the main one.
 */
public class HlcGenerator(
    private val nodeId: String,
    private val physicalNow: () -> Long,
    initial: Hlc? = null,
) {
    private val state = AtomicReference(initial ?: Hlc(0, 0, nodeId))

    public fun current(): Hlc = state.get()

    /** Stamps a locally generated event. */
    public fun next(): Hlc = state.updateAndGet { previous ->
        val physical = physicalNow()
        if (physical > previous.wallMillis) {
            Hlc(physical, 0, nodeId)
        } else {
            advance(previous.wallMillis, previous.counter)
        }
    }

    /**
     * Folds in a clock seen in somebody else's log, so that anything we write
     * afterwards sorts after everything we have already read. Without this, two
     * devices whose wall clocks disagree would keep writing events that
     * interleave into the past.
     */
    public fun observe(remote: Hlc): Hlc = state.updateAndGet { previous ->
        val physical = physicalNow()
        val wall = maxOf(previous.wallMillis, remote.wallMillis, physical)
        when {
            wall == previous.wallMillis && wall == remote.wallMillis ->
                advance(wall, maxOf(previous.counter, remote.counter))

            wall == previous.wallMillis -> advance(wall, previous.counter)
            wall == remote.wallMillis -> advance(wall, remote.counter)
            else -> Hlc(wall, 0, nodeId)
        }
    }

    /**
     * Counter overflow would wrap the counter back to zero and so produce a
     * clock that sorts *before* its own predecessor, breaking the "lexicographic
     * order equals logical order" guarantee the sync design rests on. Instead it
     * borrows a millisecond from the future. At 100 000 events inside one
     * millisecond this is unreachable in practice, but silently corrupting sync
     * ordering is not an acceptable failure mode.
     */
    private fun advance(wallMillis: Long, counter: Int): Hlc =
        if (counter >= Hlc.MAX_COUNTER) {
            Hlc(wallMillis + 1, 0, nodeId)
        } else {
            Hlc(wallMillis, counter + 1, nodeId)
        }

    public companion object {
        public fun seeded(nodeId: String, physicalNow: () -> Long, seenClocks: Iterable<Hlc>): HlcGenerator {
            val generator = HlcGenerator(nodeId, physicalNow)
            seenClocks.maxOrNull()?.let { generator.observe(it) }
            return generator
        }
    }
}
