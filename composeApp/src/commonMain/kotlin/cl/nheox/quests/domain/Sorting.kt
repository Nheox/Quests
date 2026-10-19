package cl.nheox.quests.domain

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

public object Sorting {
    /**
     * Sorts a list of quests.
     *
     * [SortKey.RANDOM] takes an explicit seed so that a random ordering stays
     * stable while the user looks at it — a list that reshuffles under the
     * cursor on every recomposition is unusable.
     */
    public fun sort(
        quests: List<Quest>,
        order: SortOrder,
        skills: Map<String, Skill> = emptyMap(),
        randomSeed: Long = 0L,
    ): List<Quest> {
        if (order.key == SortKey.RANDOM) {
            return quests.shuffled(Random(randomSeed))
        }
        val comparator = comparatorFor(order.key, skills)
        val directed = if (order.direction == SortDirection.ASCENDING) comparator else comparator.reversed()
        // Title is the final tie-break everywhere, so sorting is total and
        // therefore stable across runs and across devices.
        return quests.sortedWith(directed.thenBy { it.title.lowercase() }.thenBy { it.id })
    }

    private fun comparatorFor(key: SortKey, skills: Map<String, Skill>): Comparator<Quest> = when (key) {
        SortKey.MANUAL -> compareBy { it.manualOrder }
        SortKey.NAME -> compareBy { it.title.lowercase() }
        SortKey.CREATED -> compareBy { it.createdAt }
        SortKey.UPDATED -> compareBy { it.updatedAt }
        SortKey.DUE_DATE -> compareBy(nullsLast()) { quest ->
            quest.dueDate?.atTime(quest.dueTime ?: LocalTime.MAX)
        }

        SortKey.DIFFICULTY -> compareBy { it.difficulty.ordinal }
        SortKey.FEAR -> compareBy { it.fear.ordinal }
        SortKey.URGENCY -> compareBy { it.urgency.ordinal }
        SortKey.DURATION -> compareBy(nullsLast()) { it.estimatedMinutes }
        SortKey.XP -> compareBy { it.projectedXp() }
        SortKey.XP_TO_NEXT_LEVEL -> compareBy(nullsLast()) { quest ->
            // How close finishing this quest would bring its skills to a level.
            quest.skillIds.mapNotNull { skills[it]?.xpToNextLevel }.minOrNull()
        }

        SortKey.LAST_WORKED -> compareBy(nullsLast()) { it.lastWorkedAt }
        SortKey.COMPLETED -> compareBy(nullsLast()) { it.completedAt }
        SortKey.RANDOM -> compareBy { it.id }
    }

    private fun <T : Comparable<T>> nullsLast(): Comparator<T?> = Comparator { a, b ->
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b)
        }
    }

    /** Groups a flat list into due-date buckets, which is how Today reads best. */
    public fun groupByDue(quests: List<Quest>, clock: Clock): Map<DueBucket, List<Quest>> {
        val today = clock.today()
        return quests.groupBy { quest -> bucketOf(quest, today) }
            .toSortedMap(compareBy { it.ordinal })
    }

    public fun bucketOf(quest: Quest, today: LocalDate): DueBucket {
        val due = quest.dueDate ?: return DueBucket.SOMEDAY
        return when {
            due.isBefore(today) -> DueBucket.OVERDUE
            due == today -> DueBucket.TODAY
            due == today.plusDays(1) -> DueBucket.TOMORROW
            !due.isAfter(today.plusDays(7)) -> DueBucket.THIS_WEEK
            else -> DueBucket.LATER
        }
    }
}

public enum class DueBucket {
    OVERDUE,
    TODAY,
    TOMORROW,
    THIS_WEEK,
    LATER,
    SOMEDAY,
}
