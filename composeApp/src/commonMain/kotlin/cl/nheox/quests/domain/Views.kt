package cl.nheox.quests.domain

/** The saved lists down the left-hand side. */
public enum class QuestView {
    TODAY,
    TOMORROW,
    OVERDUE,
    NEXT,
    PLAN,
    ALL,
    COMPLETED,
    LAST_SEVEN_DAYS,
    FAILED,
    NO_DUE_DATE,
}

public enum class SortKey {
    MANUAL,
    NAME,
    CREATED,
    UPDATED,
    DUE_DATE,
    DIFFICULTY,
    FEAR,
    URGENCY,
    DURATION,
    XP,
    XP_TO_NEXT_LEVEL,
    LAST_WORKED,
    COMPLETED,
    RANDOM,
}

public enum class SortDirection {
    ASCENDING,
    DESCENDING,
    ;

    public fun flipped(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
}

public data class SortOrder(
    val key: SortKey = SortKey.MANUAL,
    val direction: SortDirection = SortDirection.ASCENDING,
)

public object Views {
    /**
     * Which quests belong in a view.
     *
     * [QuestView.NEXT] is everything open and dated beyond tomorrow — the
     * horizon list, so Today stays a list of what is actually due today rather
     * than a list of everything.
     */
    public fun filter(view: QuestView, quests: Collection<Quest>, clock: Clock): List<Quest> {
        val today = clock.today()
        val tomorrow = today.plusDays(1)
        return when (view) {
            QuestView.TODAY -> quests.filter {
                it.isOpen && it.dueDate != null && !it.dueDate.isAfter(today)
            }

            QuestView.TOMORROW -> quests.filter { it.isOpen && it.dueDate == tomorrow }

            QuestView.OVERDUE -> quests.filter {
                it.isOpen && Dates.isOverdue(it.dueDate, it.dueTime, clock)
            }

            QuestView.NEXT -> quests.filter {
                it.isOpen && it.dueDate != null && it.dueDate.isAfter(tomorrow)
            }

            QuestView.PLAN, QuestView.ALL -> quests.filter { it.isOpen }

            QuestView.COMPLETED -> quests.filter { it.isDone }

            QuestView.FAILED -> quests.filter { it.isFailed }

            QuestView.LAST_SEVEN_DAYS -> {
                val cutoff = today.minusDays(7)
                quests.filter { quest ->
                    val completed = quest.completedAt ?: return@filter false
                    !completed.atZone(clock.zone()).toLocalDate().isBefore(cutoff)
                }
            }

            QuestView.NO_DUE_DATE -> quests.filter { it.isOpen && it.dueDate == null }
        }
    }

    /** Views that read as a tree rather than a flat list. */
    public fun isTree(view: QuestView): Boolean = view == QuestView.PLAN

    public fun defaultSort(view: QuestView): SortOrder = when (view) {
        QuestView.TODAY, QuestView.OVERDUE -> SortOrder(SortKey.URGENCY, SortDirection.DESCENDING)
        QuestView.TOMORROW, QuestView.NEXT -> SortOrder(SortKey.DUE_DATE, SortDirection.ASCENDING)
        QuestView.COMPLETED, QuestView.LAST_SEVEN_DAYS -> SortOrder(SortKey.COMPLETED, SortDirection.DESCENDING)
        QuestView.FAILED -> SortOrder(SortKey.UPDATED, SortDirection.DESCENDING)
        else -> SortOrder(SortKey.MANUAL, SortDirection.ASCENDING)
    }
}
