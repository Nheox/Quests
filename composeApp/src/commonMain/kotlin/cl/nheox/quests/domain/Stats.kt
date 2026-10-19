package cl.nheox.quests.domain

import cl.nheox.quests.projection.CompletionRecord
import cl.nheox.quests.projection.QuestsState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How often quests at a given fear level actually get finished.
 *
 * The point of the fear log is to make an avoidance pattern concrete instead of
 * a feeling. Two denominators are reported rather than one, because the
 * interesting failure mode is not *failing* frightening quests — it is never
 * resolving them at all. A quest that has been open and overdue for three weeks
 * is avoidance, and a rate computed only over resolved quests would hide it.
 */
public data class FearStat(
    val fear: Fear,
    val completed: Int,
    val failed: Int,
    val open: Int,
    val openOverdue: Int,
) {
    public val resolved: Int get() = completed + failed
    public val total: Int get() = resolved + open

    /** Of the ones that reached an outcome, how many were finished. */
    public val completionRate: Float? get() = if (resolved == 0) null else completed.toFloat() / resolved

    /** Of everything ever raised at this level, how many were finished. */
    public val throughputRate: Float? get() = if (total == 0) null else completed.toFloat() / total
}

public data class WeekBucket(
    val weekStart: LocalDate,
    val xp: Int,
    val completed: Int,
    val failed: Int,
    val points: Int,
) {
    public val resolved: Int get() = completed + failed
    public val completionRate: Float? get() = if (resolved == 0) null else completed.toFloat() / resolved
}

public data class SkillGrowth(
    val skillId: String,
    val name: String,
    val level: Int,
    val totalXp: Long,
    val xpLast30Days: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val lastActive: LocalDate?,
)

public data class StatsSummary(
    val openCount: Int,
    val overdueCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val completedLast7Days: Int,
    val xpLast7Days: Int,
    val xpAllTime: Long,
    val pointBalance: Int,
    val fearLog: List<FearStat>,
    val weeks: List<WeekBucket>,
    val skills: List<SkillGrowth>,
)

public object Stats {
    public const val DEFAULT_WEEKS: Int = 12

    public fun summarise(state: QuestsState, clock: Clock, weeks: Int = DEFAULT_WEEKS): StatsSummary {
        val today = clock.today()
        val zone = clock.zone()
        val sevenDaysAgo = today.minusDays(6)

        val recent = state.completions.filter { !it.at.atZone(zone).toLocalDate().isBefore(sevenDaysAgo) }

        return StatsSummary(
            openCount = state.openQuests.size,
            overdueCount = state.openQuests.count { Dates.isOverdue(it.dueDate, it.dueTime, clock) },
            completedCount = state.completions.size,
            failedCount = state.failures.size,
            completedLast7Days = recent.size,
            xpLast7Days = recent.sumOf { it.xp },
            xpAllTime = state.totalXp,
            pointBalance = state.profile.rewardPointBalance,
            fearLog = fearLog(state, clock),
            weeks = weeklyBuckets(state, clock, weeks),
            skills = skillGrowth(state, clock),
        )
    }

    public fun fearLog(state: QuestsState, clock: Clock): List<FearStat> {
        val completedByFear = state.completions.groupingBy { it.fear }.eachCount()
        val failedByFear = state.failures.groupingBy { it.fear }.eachCount()
        val open = state.openQuests
        val openByFear = open.groupingBy { it.fear }.eachCount()
        val overdueByFear = open
            .filter { Dates.isOverdue(it.dueDate, it.dueTime, clock) }
            .groupingBy { it.fear }
            .eachCount()

        return Fear.entries.map { fear ->
            FearStat(
                fear = fear,
                completed = completedByFear[fear] ?: 0,
                failed = failedByFear[fear] ?: 0,
                open = openByFear[fear] ?: 0,
                openOverdue = overdueByFear[fear] ?: 0,
            )
        }
    }

    /**
     * Weekly buckets, Monday-based, including weeks with nothing in them.
     *
     * The empty weeks matter: a chart that silently skips them would turn a
     * fortnight off into a flat line and make a gap look like consistency.
     */
    public fun weeklyBuckets(state: QuestsState, clock: Clock, weeks: Int = DEFAULT_WEEKS): List<WeekBucket> {
        if (weeks <= 0) return emptyList()
        val zone = clock.zone()
        val thisWeek = Dates.startOfWeek(clock.today())
        val firstWeek = thisWeek.minusWeeks((weeks - 1).toLong())

        val completionsByWeek = state.completions
            .map { Dates.startOfWeek(it.at.atZone(zone).toLocalDate()) to it }
            .filter { !it.first.isBefore(firstWeek) }
            .groupBy({ it.first }, { it.second })
        val failuresByWeek = state.failures
            .map { Dates.startOfWeek(it.at.atZone(zone).toLocalDate()) to it }
            .filter { !it.first.isBefore(firstWeek) }
            .groupBy({ it.first }, { it.second })

        return (0 until weeks).map { index ->
            val weekStart = firstWeek.plusWeeks(index.toLong())
            val completions = completionsByWeek[weekStart].orEmpty()
            WeekBucket(
                weekStart = weekStart,
                xp = completions.sumOf { it.xp },
                completed = completions.size,
                failed = failuresByWeek[weekStart].orEmpty().size,
                points = completions.sumOf { it.points },
            )
        }
    }

    public fun skillGrowth(state: QuestsState, clock: Clock): List<SkillGrowth> {
        val zone = clock.zone()
        val today = clock.today()
        val cutoff = today.minusDays(29)

        return state.skills.values.map { skill ->
            val relevant = state.completions.filter { skill.id in it.skillIds }
            val days = relevant.map { it.at.atZone(zone).toLocalDate() }.distinct().sortedDescending()
            val recentXp = relevant
                .filter { !it.at.atZone(zone).toLocalDate().isBefore(cutoff) }
                .sumOf { completion -> shareOf(completion, skill.id) }

            SkillGrowth(
                skillId = skill.id,
                name = skill.name,
                level = skill.level,
                totalXp = skill.totalXp,
                xpLast30Days = recentXp,
                currentStreakDays = currentStreak(days, today),
                longestStreakDays = longestStreak(days),
                lastActive = days.firstOrNull(),
            )
        }.sortedByDescending { it.totalXp }
    }

    /** What one skill actually received from a completion, matching Balance.distribute. */
    private fun shareOf(completion: CompletionRecord, skillId: String): Int =
        Balance.distribute(completion.xp, completion.skillIds.toList())[skillId] ?: 0

    /**
     * Days in a row up to today.
     *
     * Yesterday still counts as an unbroken streak, because a streak that
     * breaks at midnight before the day is over would punish anyone who works
     * in the evening.
     */
    internal fun currentStreak(activeDaysDescending: List<LocalDate>, today: LocalDate): Int {
        if (activeDaysDescending.isEmpty()) return 0
        val latest = activeDaysDescending.first()
        val gap = ChronoUnit.DAYS.between(latest, today)
        if (gap > 1) return 0

        var streak = 1
        var cursor = latest
        for (day in activeDaysDescending.drop(1)) {
            if (ChronoUnit.DAYS.between(day, cursor) == 1L) {
                streak++
                cursor = day
            } else {
                break
            }
        }
        return streak
    }

    internal fun longestStreak(activeDaysDescending: List<LocalDate>): Int {
        if (activeDaysDescending.isEmpty()) return 0
        val ascending = activeDaysDescending.sorted()
        var best = 1
        var run = 1
        for (index in 1 until ascending.size) {
            run = if (ChronoUnit.DAYS.between(ascending[index - 1], ascending[index]) == 1L) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
