package cl.nheox.quests.domain

import cl.nheox.quests.projection.QuestsState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** One day on the activity heatmap: what was finished, and what it paid. */
public data class DayActivity(
    val date: LocalDate,
    val xp: Int,
    val completed: Int,
)

/**
 * XP earned in the current ISO week so far, against the whole of the one
 * before it.
 *
 * Plain calendar weeks rather than "the last seven days", so the number means
 * the same thing as the week in every other chart, and a Monday reads as a
 * fresh start rather than as a cliff.
 */
public data class WeekComparison(
    val thisWeek: Int,
    val lastWeek: Int,
) {
    public val delta: Int get() = thisWeek - lastWeek
}

/**
 * Today's plate: what is still on it and what has been cleared from it.
 *
 * The plate is the Today view — open quests due today or earlier — plus the
 * quests from it finished today, so completing one moves it from [open] to
 * [done] instead of shrinking the total.
 */
public data class TodayProgress(
    val done: Int,
    val open: Int,
) {
    public val total: Int get() = done + open
    public val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** Everything the Standing screen shows that depends only on the log and the date. */
public data class StandingSummary(
    val activity: List<DayActivity>,
    val activityXp: Int,
    val activeDays: Int,
    val streakDays: Int,
    val overdueCount: Int,
    val worstOverdue: List<Quest>,
    val today: TodayProgress,
    /** Today's plate itself: what [today] counts, finished first. */
    val todayQuests: List<Quest>,
    val week: WeekComparison,
    /** Monday to Sunday of the current week, for the week's bar chart. */
    val weekDays: List<DayActivity>,
    /** The skills nearest their next level, nearest first. */
    val closestSkills: List<Skill>,
)

/**
 * The numbers behind the Standing screen (§7.5), which answers "what is my
 * situation?" at a glance instead of firing a notification at a set hour.
 *
 * Every day here is a calendar day in the clock's zone, worked out on
 * [LocalDate] rather than by adding hours to an instant, so a daylight-saving
 * change can neither drop a day from the heatmap nor move a completion into
 * the wrong one: a quest finished at 23:30 belongs to the day it was finished
 * on, as the person who finished it saw the clock.
 */
public object Standing {
    /** Twelve weeks, ending today. */
    public const val HEATMAP_DAYS: Int = 84

    public const val WORST_OVERDUE: Int = 3

    /** One featured skill and the two behind it. */
    public const val CLOSEST_SKILLS: Int = 3

    /** Shades on the heatmap: none, then four steps up to the full accent. */
    public const val HEAT_LEVELS: Int = 5

    /**
     * The least XP a day needs to reach the top shade.
     *
     * Shades are relative to the best day in view, so a light user's map
     * still has a range — but against a floor, or someone whose best day was
     * one fifteen-minute quest would see that day painted as a triumph. Sixty
     * is four of the reference quest (§5.2: Moderate, 30 minutes, 15 XP).
     */
    public const val HEAT_MIN_SCALE: Int = 60

    public fun summarise(state: QuestsState, clock: Clock): StandingSummary {
        val activity = dailyActivity(state, clock)
        val overdue = overdue(state, clock)
        return StandingSummary(
            activity = activity,
            activityXp = activity.sumOf { it.xp },
            activeDays = activity.count { it.completed > 0 },
            streakDays = streak(state, clock),
            overdueCount = overdue.size,
            worstOverdue = rankOverdue(overdue, clock).take(WORST_OVERDUE),
            today = todayProgress(state, clock),
            todayQuests = todayPlate(state, clock),
            week = weekOverWeek(state, clock),
            weekDays = currentWeek(state, clock),
            closestSkills = closestToLevel(state.skills.values),
        )
    }

    /**
     * One entry per day for the [days] days ending today, oldest first,
     * including the days with nothing in them — an omitted day would close
     * the gap it should show.
     *
     * Bucketed by completion time in the clock's zone. Completions stamped
     * after today, by a device whose clock runs fast, wait for their day.
     */
    public fun dailyActivity(state: QuestsState, clock: Clock, days: Int = HEATMAP_DAYS): List<DayActivity> {
        if (days <= 0) return emptyList()
        return activityFor(state, clock, clock.today().minusDays((days - 1).toLong()), days)
    }

    /**
     * Monday to Sunday of the ISO week holding today. The days still to come
     * are there, empty, so the chart keeps its seven columns all week.
     */
    public fun currentWeek(state: QuestsState, clock: Clock): List<DayActivity> =
        activityFor(state, clock, Dates.startOfWeek(clock.today()), 7)

    private fun activityFor(state: QuestsState, clock: Clock, first: LocalDate, days: Int): List<DayActivity> {
        val zone = clock.zone()
        val byDay = state.completions
            .groupBy { it.at.atZone(zone).toLocalDate() }
        return (0 until days).map { offset ->
            val date = first.plusDays(offset.toLong())
            val completions = byDay[date].orEmpty()
            DayActivity(date = date, xp = completions.sumOf { it.xp }, completed = completions.size)
        }
    }

    /**
     * The scale a heatmap shades against: its best day, or [HEAT_MIN_SCALE]
     * when no day reached that.
     */
    public fun heatScale(days: Collection<DayActivity>): Int =
        maxOf(days.maxOfOrNull { it.xp } ?: 0, HEAT_MIN_SCALE)

    /**
     * Which of the [HEAT_LEVELS] shades a day gets: 0 for nothing, otherwise
     * the quarter of [scale] its XP reaches, 1 to 4.
     *
     * A day with a completion always shows, even one that somehow paid no XP
     * (only a hand-edited log can produce that): something was done.
     */
    public fun heatLevel(day: DayActivity, scale: Int): Int {
        if (day.xp <= 0) return if (day.completed > 0) 1 else 0
        val top = HEAT_LEVELS - 1
        return ceil(top * day.xp.toDouble() / scale.coerceAtLeast(1)).toInt().coerceIn(1, top)
    }

    /**
     * Days in a row with at least one completion, up to today. Yesterday
     * still counts as unbroken, as it does for skill streaks: the day is not
     * over until it is over.
     */
    public fun streak(state: QuestsState, clock: Clock): Int {
        val zone = clock.zone()
        val today = clock.today()
        val days = state.completions
            .map { it.at.atZone(zone).toLocalDate() }
            .filter { !it.isAfter(today) }
            .distinct()
            .sortedDescending()
        return Stats.currentStreak(days, today)
    }

    public fun weekOverWeek(state: QuestsState, clock: Clock): WeekComparison {
        val zone = clock.zone()
        val thisWeek = Dates.startOfWeek(clock.today())
        val lastWeek = thisWeek.minusWeeks(1)
        var current = 0
        var previous = 0
        for (completion in state.completions) {
            when (Dates.startOfWeek(completion.at.atZone(zone).toLocalDate())) {
                thisWeek -> current += completion.xp
                lastWeek -> previous += completion.xp
                else -> Unit
            }
        }
        return WeekComparison(thisWeek = current, lastWeek = previous)
    }

    /**
     * The [count] skills with the least XP left to their next level — the
     * ones a single quest is most likely to tip over — nearest first.
     *
     * Ties go to the skill further through its level, then by name, so the
     * card does not flicker between two equals from one launch to the next.
     */
    public fun closestToLevel(skills: Collection<Skill>, count: Int = CLOSEST_SKILLS): List<Skill> = skills.sortedWith(
        compareBy<Skill> { it.xpToNextLevel }
            .thenByDescending { it.progressToNextLevel }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id },
    ).take(count)

    /**
     * The [count] overdue quests the automatic order ranks highest (§5.6).
     *
     * By score alone: a pin places a quest among its siblings, and these are
     * drawn from every branch at once. Equal scores fall to the longer
     * overdue, then to the title, then to the id.
     */
    public fun worstOverdue(state: QuestsState, clock: Clock, count: Int = WORST_OVERDUE): List<Quest> =
        rankOverdue(overdue(state, clock), clock).take(count)

    public fun todayProgress(state: QuestsState, clock: Clock): TodayProgress {
        val zone = clock.zone()
        val today = clock.today()
        var done = 0
        var open = 0
        for (quest in state.quests.values) {
            val due = quest.dueDate ?: continue
            if (due.isAfter(today)) continue
            when {
                quest.isOpen -> open++
                quest.isDone && quest.completedAt?.atZone(zone)?.toLocalDate() == today -> done++
            }
        }
        return TodayProgress(done = done, open = open)
    }

    /**
     * The quests [todayProgress] counts: those finished today first, in the
     * order they were finished, then the open ones by priority — so the card
     * reads as a checklist being worked down from the top.
     */
    public fun todayPlate(state: QuestsState, clock: Clock): List<Quest> {
        val zone = clock.zone()
        val today = clock.today()
        val onPlate = state.quests.values.filter { quest ->
            val due = quest.dueDate ?: return@filter false
            !due.isAfter(today) &&
                (quest.isOpen || quest.isDone && quest.completedAt?.atZone(zone)?.toLocalDate() == today)
        }
        val done = onPlate.filter { it.isDone }.sortedWith(compareBy<Quest> { it.completedAt }.thenBy { it.id })
        return done + rankByScore(onPlate.filter { it.isOpen }, clock)
    }

    /** Whole days a quest has been overdue: 0 when it fell overdue today. */
    public fun daysOverdue(quest: Quest, clock: Clock): Long {
        val due = quest.dueDate ?: return 0
        return ChronoUnit.DAYS.between(due, clock.today()).coerceAtLeast(0)
    }

    private fun overdue(state: QuestsState, clock: Clock): List<Quest> =
        state.openQuests.filter { Dates.isOverdue(it.dueDate, it.dueTime, clock) }

    private fun rankOverdue(quests: List<Quest>, clock: Clock): List<Quest> = rankByScore(quests, clock)

    /** Highest priority first; equal scores fall to the earlier due date, then title, then id. */
    private fun rankByScore(quests: List<Quest>, clock: Clock): List<Quest> = quests
        .map { it to Priority.score(it, clock) }
        .sortedWith(
            compareByDescending<Pair<Quest, Double>> { it.second }
                .thenBy { it.first.dueDate }
                .thenBy { it.first.title.lowercase() }
                .thenBy { it.first.id },
        )
        .map { it.first }
}
