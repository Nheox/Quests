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

/**
 * What a list can be sorted by (§7.1).
 *
 * Never persisted — the sort is per-session UI state — so the declaration
 * order is free to be the menu's order, and [PRIORITY], the default, leads.
 * Should a sort ever be saved, save it by name.
 */
public enum class SortKey {
    PRIORITY,
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

    /**
     * The list a quest is opened in when it is asked for from outside one —
     * from the Standing screen, say.
     *
     * Today, Tomorrow, Next and No due date between them hold every open
     * quest exactly once, so an open quest lands in the one that holds it,
     * among the quests it is due alongside; a finished one lands in Completed
     * or Failed.
     */
    public fun home(quest: Quest, clock: Clock): QuestView {
        val due = quest.dueDate
        val today = clock.today()
        return when {
            quest.isDone -> QuestView.COMPLETED
            quest.isFailed -> QuestView.FAILED
            due == null -> QuestView.NO_DUE_DATE
            !due.isAfter(today) -> QuestView.TODAY
            due == today.plusDays(1) -> QuestView.TOMORROW
            else -> QuestView.NEXT
        }
    }

    /** Views that read as a tree rather than a flat list. */
    public fun isTree(view: QuestView): Boolean = view == QuestView.PLAN

    /**
     * The order a view opens in.
     *
     * Every list of open quests orders itself by priority (§3, §5.6): the user
     * rates a quest once and the app decides what rises. The history views
     * answer a different question — what happened, most recent first — so
     * they keep a date order.
     */
    public fun defaultSort(view: QuestView): SortOrder = when (view) {
        QuestView.TODAY,
        QuestView.TOMORROW,
        QuestView.OVERDUE,
        QuestView.NEXT,
        QuestView.PLAN,
        QuestView.ALL,
        QuestView.NO_DUE_DATE,
        -> SortOrder(SortKey.PRIORITY, SortDirection.DESCENDING)

        QuestView.COMPLETED, QuestView.LAST_SEVEN_DAYS -> SortOrder(SortKey.COMPLETED, SortDirection.DESCENDING)
        QuestView.FAILED -> SortOrder(SortKey.UPDATED, SortDirection.DESCENDING)
    }
}
