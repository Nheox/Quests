package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Table-driven tests for the XP and level maths (§8.4).
 *
 * These are the numbers a user's whole history is denominated in, so the
 * expected values are written out by hand from the published formula rather
 * than generated from the implementation — a test that recomputes the formula
 * only proves the formula equals itself.
 */
class BalanceTest {

    @Test
    fun `duration factor is one at the reference duration`() {
        assertEquals(1.0, Balance.durationFactor(30), 1e-9)
    }

    @Test
    fun `duration factor is missing-estimate neutral`() {
        // A quest with no estimate must be worth the same as a 30-minute one,
        // not nothing: otherwise the app quietly punishes capturing quickly.
        assertEquals(Balance.durationFactor(30), Balance.durationFactor(null), 1e-9)
    }

    @Test
    fun `duration factor is clamped at both ends`() {
        assertEquals(0.5, Balance.durationFactor(1), 1e-9)
        assertEquals(0.5, Balance.durationFactor(0), 1e-9)
        assertEquals(4.0, Balance.durationFactor(60 * 24), 1e-9)
        // sqrt(480/30) = 4.0 exactly, the point where the clamp starts binding.
        assertEquals(4.0, Balance.durationFactor(480), 1e-9)
    }

    @Test
    fun `xp table matches the published formula`() {
        data class Case(
            val difficulty: Difficulty,
            val fear: Fear,
            val minutes: Int?,
            val expected: Int,
        )

        // round(10 × difficulty × fear × duration)
        val cases = listOf(
            Case(Difficulty.LOW, Fear.NONE, 30, 10),
            Case(Difficulty.TRIVIAL, Fear.NONE, 30, 5),
            Case(Difficulty.EXTREME, Fear.NONE, 30, 50),
            Case(Difficulty.MODERATE, Fear.NONE, null, 15),
            Case(Difficulty.HIGH, Fear.STRONG, 120, 65),
            Case(Difficulty.TRIVIAL, Fear.SEVERE, 10, 5),
            Case(Difficulty.MODERATE, Fear.NONE, 180, 37),
            Case(Difficulty.LOW, Fear.MILD, 45, 13),
            Case(Difficulty.EXTREME, Fear.SEVERE, 480, 340),
            Case(Difficulty.VERY_LOW, Fear.MODERATE, 15, 7),
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                Balance.xp(case.difficulty, case.fear, case.minutes),
                "xp for ${case.difficulty}/${case.fear}/${case.minutes}",
            )
        }
    }

    @Test
    fun `reward points ignore fear`() {
        Fear.entries.forEach { fear ->
            assertEquals(
                Balance.rewardPoints(Difficulty.HIGH, 60),
                Balance.xp(Difficulty.HIGH, fear, 60).let { Balance.rewardPoints(Difficulty.HIGH, 60) },
            )
        }
        // round(2 × difficulty × duration)
        assertEquals(3, Balance.rewardPoints(Difficulty.LOW, 60))
        assertEquals(2, Balance.rewardPoints(Difficulty.LOW, 30))
        assertEquals(20, Balance.rewardPoints(Difficulty.EXTREME, 120))
    }

    @Test
    fun `reward bonus adds to the formula and nothing else`() {
        data class Case(
            val difficulty: Difficulty,
            val minutes: Int?,
            val bonus: Int?,
            val expected: Int,
        )

        // round(2 × difficulty × duration) + (rewardBonus ?: 0)
        val cases = listOf(
            Case(Difficulty.LOW, 30, null, 2),
            Case(Difficulty.LOW, 30, 0, 2),
            Case(Difficulty.LOW, 30, 1, 3),
            Case(Difficulty.LOW, 30, 5, 7),
            Case(Difficulty.MODERATE, null, 10, 13),
            Case(Difficulty.EXTREME, 120, 3, 23),
            Case(Difficulty.TRIVIAL, 1, 100, 101),
            // Only a hand-edited log can say -4; it must not turn completion
            // into a fine.
            Case(Difficulty.LOW, 30, -4, 2),
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                Balance.rewardPoints(case.difficulty, case.minutes, case.bonus),
                "points for ${case.difficulty}/${case.minutes}/bonus ${case.bonus}",
            )
        }
    }

    @Test
    fun `a quest's projected payout carries its bonus into points and never into xp`() {
        val created = java.time.Instant.parse("2026-10-01T09:00:00Z")
        val plain = Quest(id = "q", title = "q", difficulty = Difficulty.LOW, createdAt = created, updatedAt = created)
        val bonused = plain.copy(rewardBonus = 5)
        assertEquals(2, plain.projectedPoints())
        assertEquals(7, bonused.projectedPoints())
        assertEquals(plain.projectedXp(), bonused.projectedXp())
    }

    @Test
    fun `urgency never appears in any payout`() {
        // The strongest commitment in the balance design: if this ever fails,
        // the app has started rewarding procrastination.
        val xp = Balance.xp(Difficulty.MODERATE, Fear.MILD, 60)
        val points = Balance.rewardPoints(Difficulty.MODERATE, 60)
        Urgency.entries.forEach { _ ->
            assertEquals(xp, Balance.xp(Difficulty.MODERATE, Fear.MILD, 60))
            assertEquals(points, Balance.rewardPoints(Difficulty.MODERATE, 60))
        }
    }

    /**
     * The curve is `round(100 × n^1.6)`, cumulative.
     *
     * Note for anyone comparing against docs/SPEC.md §5.3: the worked examples
     * printed there for levels 5, 20 and 50 do not match the formula printed
     * immediately above them (its figures for levels 2 and 10 do). The formula
     * is the normative part and is what is implemented; these are the values it
     * actually produces. See docs/BALANCE.md.
     */
    @Test
    fun `level thresholds match 100 times n to the 1_6`() {
        assertEquals(0L, Balance.xpForLevel(1))
        assertEquals(303L, Balance.xpForLevel(2))
        assertEquals(580L, Balance.xpForLevel(3))
        assertEquals(919L, Balance.xpForLevel(4))
        assertEquals(1313L, Balance.xpForLevel(5))
        assertEquals(3981L, Balance.xpForLevel(10))
        assertEquals(12068L, Balance.xpForLevel(20))
        assertEquals(52282L, Balance.xpForLevel(50))
    }

    @Test
    fun `level is the inverse of the threshold`() {
        assertEquals(1, Balance.levelForXp(0))
        assertEquals(1, Balance.levelForXp(302))
        assertEquals(2, Balance.levelForXp(303))
        assertEquals(2, Balance.levelForXp(579))
        assertEquals(3, Balance.levelForXp(580))
        assertEquals(10, Balance.levelForXp(3981))
        assertEquals(9, Balance.levelForXp(3980))

        // Exhaustive agreement across the range a real user could reach.
        for (level in 1..200) {
            val threshold = Balance.xpForLevel(level)
            assertEquals(level, Balance.levelForXp(threshold), "at threshold of level $level")
            if (level > 1) {
                assertEquals(level - 1, Balance.levelForXp(threshold - 1), "just below level $level")
            }
        }
    }

    @Test
    fun `negative xp cannot happen but is survived`() {
        assertEquals(1, Balance.levelForXp(-500))
    }

    @Test
    fun `distribution never loses or invents xp`() {
        val cases = listOf(
            100 to listOf("a"),
            100 to listOf("a", "b", "c"),
            7 to listOf("a", "b", "c"),
            1 to listOf("a", "b"),
            65 to listOf("s_thesis", "s_lab", "s_writing", "s_stats"),
        )
        cases.forEach { (total, skills) ->
            val shares = Balance.distribute(total, skills)
            assertEquals(total, shares.values.sum(), "total preserved for $total across ${skills.size}")
            assertEquals(skills.size, shares.size)
            // Fairness: nobody is more than one XP better off than anybody else.
            val spread = shares.values.max() - shares.values.min()
            assertTrue(spread <= 1, "spread of $spread is unfair")
        }
    }

    @Test
    fun `distribution is stable regardless of input order`() {
        val forwards = Balance.distribute(10, listOf("a", "b", "c"))
        val backwards = Balance.distribute(10, listOf("c", "b", "a"))
        // Replay must produce identical state from identical events whatever
        // order a set happened to iterate in.
        assertEquals(forwards, backwards)
    }

    @Test
    fun `distribution of nothing is nothing`() {
        assertEquals(emptyMap(), Balance.distribute(0, listOf("a")))
        assertEquals(emptyMap(), Balance.distribute(50, emptyList()))
    }

    @Test
    fun `duplicate skill links are not paid twice`() {
        val shares = Balance.distribute(10, listOf("a", "a", "b"))
        assertEquals(2, shares.size)
        assertEquals(10, shares.values.sum())
    }
}
