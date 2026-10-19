package cl.nheox.quests.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Why a quest was suggested. A suggester that cannot explain itself gets ignored. */
public enum class SuggestionReason {
    FITS_THE_TIME,
    OVERDUE,
    DUE_TODAY,
    URGENT,
    GOOD_ENERGY_FOR_FEAR,
    GENTLE_ON_LOW_ENERGY,
    UNTOUCHED_FOR_A_WHILE,
    CLOSE_TO_A_LEVEL,
    QUICK_WIN,
}

public data class Suggestion(
    val quest: Quest,
    val score: Double,
    val reasons: List<SuggestionReason>,
)

public data class SuggestContext(
    val energy: Int,
    val minutesAvailable: Int?,
    val today: LocalDate,
    val skills: Map<String, Skill> = emptyMap(),
)

/**
 * "What now?" — the feature the original app's energy model gestured at and
 * never delivered.
 *
 * The central idea: fear-heavy work should be pushed into high-energy windows
 * and kept out of low-energy ones. Avoidance is not a willpower problem to be
 * nagged about; it is a scheduling problem, and the app knows the schedule.
 */
public object Suggester {
    public const val DEFAULT_COUNT: Int = 3

    public fun suggest(
        quests: Collection<Quest>,
        context: SuggestContext,
        count: Int = DEFAULT_COUNT,
    ): List<Suggestion> = quests
        .asSequence()
        .filter { it.isOpen && it.title.isNotBlank() }
        .map { score(it, context) }
        .filter { it.score > Double.NEGATIVE_INFINITY }
        .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.quest.id })
        .take(count)
        .toList()

    internal fun score(quest: Quest, context: SuggestContext): Suggestion {
        val reasons = mutableListOf<SuggestionReason>()
        var score = 0.0

        // Urgency: the one place it is allowed to matter.
        score += quest.urgency.index * 6.0
        if (quest.urgency.index >= Urgency.HIGH.index) reasons += SuggestionReason.URGENT

        // Deadlines.
        val due = quest.dueDate
        if (due != null) {
            val daysOut = ChronoUnit.DAYS.between(context.today, due)
            when {
                daysOut < 0 -> {
                    score += 26.0 + minOf(-daysOut, 14L) * 1.5
                    reasons += SuggestionReason.OVERDUE
                }

                daysOut == 0L -> {
                    score += 22.0
                    reasons += SuggestionReason.DUE_TODAY
                }

                daysOut <= 3 -> score += 12.0 - daysOut * 2.0
                daysOut <= 14 -> score += 4.0
                else -> score += 1.0
            }
        }

        // Does it fit the window the user actually has?
        val estimate = quest.estimatedMinutes
        val available = context.minutesAvailable
        if (available != null) {
            val needed = estimate ?: Balance.REFERENCE_MINUTES.toInt()
            when {
                needed <= available -> {
                    // Reward using the window well without rewarding overrun.
                    val usage = needed.toDouble() / available.toDouble()
                    score += 10.0 + 8.0 * usage
                    reasons += SuggestionReason.FITS_THE_TIME
                }

                needed <= available * 1.25 -> score -= 6.0
                else -> score -= 24.0
            }
            if (needed <= 10 && available >= needed) reasons += SuggestionReason.QUICK_WIN
        } else if (estimate != null && estimate <= 10) {
            reasons += SuggestionReason.QUICK_WIN
            score += 3.0
        }

        // Energy and fear. High energy is when the frightening thing is cheapest.
        val energy = context.energy.coerceIn(0, 100)
        val fearWeight = quest.fear.index.toDouble()
        val effortWeight = quest.difficulty.index.toDouble() / (Difficulty.entries.size - 1).toDouble()
        when (Energy.band(energy)) {
            EnergyBand.HIGH -> {
                score += fearWeight * 7.0 + effortWeight * 8.0
                if (quest.fear.index >= Fear.STRONG.index) reasons += SuggestionReason.GOOD_ENERGY_FOR_FEAR
            }

            EnergyBand.MEDIUM -> score += fearWeight * 2.0 + effortWeight * 3.0

            EnergyBand.LOW -> {
                score -= fearWeight * 5.0 + effortWeight * 8.0
                if (quest.fear.index <= Fear.MILD.index && quest.difficulty.index <= Difficulty.LOW.index) {
                    reasons += SuggestionReason.GENTLE_ON_LOW_ENERGY
                    score += 6.0
                }
            }

            EnergyBand.SPENT -> {
                score -= fearWeight * 9.0 + effortWeight * 12.0
                if (quest.fear == Fear.NONE && quest.difficulty.index <= Difficulty.VERY_LOW.index) {
                    reasons += SuggestionReason.GENTLE_ON_LOW_ENERGY
                    score += 8.0
                }
            }
        }

        // Things quietly rotting at the bottom of the list.
        val lastWorked = quest.lastWorkedAt
        if (lastWorked != null) {
            val staleDays = ChronoUnit.DAYS.between(lastWorked, quest.updatedAt).coerceAtLeast(0)
            if (staleDays >= 7) {
                score += minOf(staleDays, 30L) * 0.3
                reasons += SuggestionReason.UNTOUCHED_FOR_A_WHILE
            }
        }

        // A nudge when finishing this would actually tick a skill over.
        val payout = quest.projectedXp()
        val closest = quest.skillIds.mapNotNull { context.skills[it]?.xpToNextLevel }.minOrNull()
        if (closest != null && payout > 0 && closest <= payout) {
            score += 9.0
            reasons += SuggestionReason.CLOSE_TO_A_LEVEL
        }

        return Suggestion(quest, score, reasons.distinct())
    }
}
