package cl.nheox.quests.domain

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Every number here is ours, designed from first principles. No formula from
 * the original app is known to us and none is reproduced. See docs/BALANCE.md
 * for the reasoning and the play-testing notes.
 */
public object Balance {
    public const val BASE_XP: Double = 10.0

    /** 30 minutes is the reference quest: it neither inflates nor deflates XP. */
    public const val REFERENCE_MINUTES: Double = 30.0
    public const val DURATION_FACTOR_MIN: Double = 0.5
    public const val DURATION_FACTOR_MAX: Double = 4.0

    public const val LEVEL_COEFFICIENT: Double = 100.0
    public const val LEVEL_EXPONENT: Double = 1.6

    public const val POINTS_COEFFICIENT: Double = 2.0

    /**
     * `clamp(sqrt(minutes / 30), 0.5, 4.0)`, so a quest with no estimate is
     * treated as the 30-minute reference rather than as worthless.
     */
    public fun durationFactor(estimatedMinutes: Int?): Double {
        val minutes = estimatedMinutes ?: return 1.0
        if (minutes <= 0) return DURATION_FACTOR_MIN
        return sqrt(minutes / REFERENCE_MINUTES).coerceIn(DURATION_FACTOR_MIN, DURATION_FACTOR_MAX)
    }

    /**
     * `round(10 × difficulty × fear × duration)`.
     *
     * Urgency is deliberately absent: paying more for urgent work rewards
     * leaving things until they are urgent. Energy is deliberately absent too —
     * nobody should be paid less for working while tired.
     */
    public fun xp(difficulty: Difficulty, fear: Fear, estimatedMinutes: Int?): Int =
        (BASE_XP * difficulty.multiplier * fear.bonus * durationFactor(estimatedMinutes)).roundToInt()

    /**
     * `round(2 × difficulty × duration)` — fear excluded, so the spendable
     * economy stays legible and does not track the XP curve.
     */
    public fun rewardPoints(difficulty: Difficulty, estimatedMinutes: Int?): Int =
        (POINTS_COEFFICIENT * difficulty.multiplier * durationFactor(estimatedMinutes)).roundToInt()

    /**
     * Distributes [totalXp] across [skillIds] as evenly as whole numbers allow,
     * giving the remainder to the first skills in a stable order.
     *
     * Even distribution is the rule, but it must not leak XP: the parts always
     * sum back to the total, which is what makes replay reproducible.
     */
    public fun distribute(totalXp: Int, skillIds: List<String>): Map<String, Int> {
        if (skillIds.isEmpty() || totalXp == 0) return emptyMap()
        val ordered = skillIds.distinct().sorted()
        val share = totalXp / ordered.size
        val remainder = totalXp % ordered.size
        return ordered.withIndex().associate { (index, id) ->
            id to share + if (index < remainder) 1 else 0
        }
    }

    /** Cumulative XP needed to *reach* level [level]. Level 1 is free. */
    public fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0
        return (LEVEL_COEFFICIENT * level.toDouble().pow(LEVEL_EXPONENT)).roundToLong()
    }

    /**
     * The level a given total XP buys. Inverts the curve analytically, then
     * walks the boundary, so it stays exact under rounding and stays correct if
     * the curve is ever retuned.
     */
    public fun levelForXp(totalXp: Long): Int {
        if (totalXp < xpForLevel(2)) return 1
        var guess = (totalXp.toDouble() / LEVEL_COEFFICIENT).pow(1.0 / LEVEL_EXPONENT).toInt().coerceAtLeast(1)
        while (xpForLevel(guess + 1) <= totalXp) guess++
        while (guess > 1 && xpForLevel(guess) > totalXp) guess--
        return guess
    }
}
