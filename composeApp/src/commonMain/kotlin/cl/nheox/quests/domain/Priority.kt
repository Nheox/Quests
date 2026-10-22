package cl.nheox.quests.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * One quest's priority, itemised.
 *
 * Kept as parts rather than folded into a single number because the score is
 * meant to be visible (§3): a list that reorders itself has to be able to say
 * why, or it reads as arbitrary and the user goes back to sorting by hand.
 */
public data class PriorityBreakdown(
    val urgency: Double,
    val due: Double,
    val fear: Double,
    val age: Double,
) {
    /**
     * The sum, rounded to hundredths.
     *
     * Every weight is a multiple of 0.05, so the rounding loses nothing; what it
     * removes is floating-point noise, which would otherwise split two quests
     * that tie on paper and so skip the tie-breaks meant for exactly them.
     */
    public val total: Double = hundredths(urgency + due + fear + age)
}

/**
 * Where a quest sits relative to the automatic order (§5.6).
 *
 * A pin is the override, not the daily mechanism: set only by an explicit
 * "pin to top" or "pin to bottom", per sibling group, and cleared with one
 * key. Declared in display order, top to bottom; [Priority.order] sorts by it.
 */
public enum class Pin {
    /** `manualOrder <= 0`: above every unpinned quest. */
    TOP,

    /** `manualOrder == null`: placed by score. */
    NONE,

    /** `manualOrder > 0`: below every unpinned quest. */
    BOTTOM,
    ;

    public companion object {
        /**
         * Reads a stored `manualOrder`.
         *
         * The automatic block is anchored at 0, which is also how legacy
         * values map. Before pins (SPEC v0.5; the app's own version did not
         * change) the field held creation order, counting up from 1 per
         * sibling group, and "move to top" and "move to bottom" wrote one past
         * the lowest or highest sibling. Creation order lived in
         * `QuestCreated` and is ignored, so only those explicit moves can
         * reach here. A "move to top" landed at 0 or below whenever the first
         * sibling still existed, and reads as a top pin; a "move to bottom"
         * always landed above 0, and reads as a bottom pin. An old "move to
         * top" made after the first siblings were deleted landed above 0 and
         * reads as a bottom pin; one keystroke re-pins it.
         */
        public fun of(manualOrder: Int?): Pin = when {
            manualOrder == null -> NONE
            manualOrder <= 0 -> TOP
            else -> BOTTOM
        }
    }
}

/**
 * The automatic order (§5.6): what rises to the top of a list without anyone
 * dragging it there.
 *
 * `priority = urgencyW + dueW + fearW + ageW`, recomputed on read and never
 * stored, so retuning a weight reorders every list at once without touching a
 * single log line.
 *
 * Difficulty is deliberately absent. Ranking hard work up would bury quick
 * wins; ranking it down would bury the hard thing. It only breaks ties, via
 * duration, so that among equals a list opens with something finishable.
 */
public object Priority {
    /** Whenever, Low, Normal, High, Critical. */
    public val URGENCY_WEIGHTS: List<Double> = listOf(0.0, 1.0, 2.0, 4.0, 7.0)

    /**
     * None, Mild, Moderate, Strong, Severe.
     *
     * Fear pushes a quest *up*: surfacing the avoided thing is why this app
     * exists. The suggester is where fear is tempered by energy; the list does
     * not flinch.
     */
    public val FEAR_WEIGHTS: List<Double> = listOf(0.0, 0.4, 0.9, 1.5, 2.2)

    public const val OVERDUE_BASE: Double = 6.0
    public const val OVERDUE_PER_DAY: Double = 0.4
    public const val OVERDUE_DAYS_CAP: Long = 10
    public const val DUE_TODAY: Double = 5.0
    public const val DUE_TOMORROW: Double = 3.0
    public const val DUE_WITHIN_THREE_DAYS: Double = 2.0
    public const val DUE_WITHIN_A_WEEK: Double = 1.0

    /**
     * A slow ramp with a ceiling: an untouched quest drifts upward until it is
     * dealt with or deleted, but can never outrank a genuine emergency.
     */
    public const val AGE_PER_DAY: Double = 0.05
    public const val AGE_CAP: Double = 3.0

    public fun urgencyWeight(urgency: Urgency): Double = URGENCY_WEIGHTS[urgency.index]

    public fun fearWeight(fear: Fear): Double = FEAR_WEIGHTS[fear.index]

    /**
     * `overdue → 6 + min(daysOverdue, 10) × 0.4`, today 5, tomorrow 3, within
     * three days 2, within a week 1, later or undated 0.
     *
     * Days are whole calendar days in the clock's zone. Whether the quest is
     * overdue at all is [Dates.isOverdue]'s answer rather than a date
     * comparison of its own, so the order agrees with the red chip and the
     * Overdue view: a quest due today at 09:00 is overdue at 10:00, by zero
     * days, and outranks one merely due today.
     */
    public fun dueWeight(date: LocalDate?, time: LocalTime?, clock: Clock): Double {
        if (date == null) return 0.0
        val today = clock.today()
        if (Dates.isOverdue(date, time, clock)) {
            val daysOverdue = ChronoUnit.DAYS.between(date, today).coerceIn(0, OVERDUE_DAYS_CAP)
            return hundredths(OVERDUE_BASE + daysOverdue * OVERDUE_PER_DAY)
        }
        return when (ChronoUnit.DAYS.between(today, date)) {
            0L -> DUE_TODAY
            1L -> DUE_TOMORROW
            in 2L..3L -> DUE_WITHIN_THREE_DAYS
            in 4L..7L -> DUE_WITHIN_A_WEEK
            else -> 0.0
        }
    }

    /**
     * `min(daysSinceCreated × 0.05, 3.0)`, in whole calendar days in the
     * clock's zone. A creation stamp ahead of the clock — another device whose
     * clock runs fast — counts as created today rather than as negative age.
     */
    public fun ageWeight(createdAt: Instant, clock: Clock): Double {
        val created = createdAt.atZone(clock.zone()).toLocalDate()
        val days = ChronoUnit.DAYS.between(created, clock.today()).coerceAtLeast(0)
        return hundredths((days * AGE_PER_DAY).coerceAtMost(AGE_CAP))
    }

    public fun breakdown(quest: Quest, clock: Clock): PriorityBreakdown = PriorityBreakdown(
        urgency = urgencyWeight(quest.urgency),
        due = dueWeight(quest.dueDate, quest.dueTime, clock),
        fear = fearWeight(quest.fear),
        age = ageWeight(quest.createdAt, clock),
    )

    public fun score(quest: Quest, clock: Clock): Double = breakdown(quest, clock).total

    /**
     * The automatic order, highest first, with pins at either end: top pins
     * ascending, then every unpinned quest by score, then bottom pins
     * ascending.
     *
     * Total and deterministic, so two devices replaying the same logs show the
     * same list: equal scores fall to the shorter estimate (no estimate counts
     * as the 30-minute reference, as it does for XP), then to the title
     * ignoring case, then to the id. Quests that share a pin value are ordered
     * among themselves the same way.
     */
    public fun order(quests: Collection<Quest>, clock: Clock): List<Quest> =
        quests.map { Ranked(it, score(it, clock)) }.sortedWith(RANKED).map { it.quest }

    private class Ranked(val quest: Quest, val score: Double)

    private val RANKED: Comparator<Ranked> = compareBy<Ranked>(
        { it.quest.pin },
        { it.quest.manualOrder ?: 0 },
    )
        .thenByDescending { it.score }
        .thenBy { it.quest.estimatedMinutes?.toDouble() ?: Balance.REFERENCE_MINUTES }
        .thenBy { it.quest.title.lowercase() }
        .thenBy { it.quest.id }
}

private fun hundredths(value: Double): Double = Math.round(value * 100.0) / 100.0
