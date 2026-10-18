package cl.nheox.quests.domain

import kotlinx.serialization.Serializable
import java.time.LocalTime

/**
 * A real-life quest.
 *
 * Immutable: every change goes through the event log, and the projection
 * rebuilds this from scratch. Nothing here is authoritative on its own.
 */
@Serializable
public data class Quest(
    val id: String,
    val title: String,
    val notes: String = "",
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val fear: Fear = Fear.DEFAULT,
    val urgency: Urgency = Urgency.DEFAULT,
    val estimatedMinutes: Int? = null,
    val dueDate: IsoDate? = null,
    val dueTime: IsoTime? = null,
    val repetition: Repetition? = null,
    val reminders: List<Reminder> = emptyList(),
    val parentId: String? = null,
    val manualOrder: Int = 0,
    val state: QuestState = QuestState.OPEN,
    val skillIds: Set<String> = emptySet(),
    val createdAt: IsoInstant,
    val updatedAt: IsoInstant,
    val completedAt: IsoInstant? = null,
    val lastWorkedAt: IsoInstant? = null,
    /** What completion actually paid out, recorded rather than recomputed. */
    val rewardPoints: Int = 0,
    val xpAwarded: Int = 0,
) {
    public val isOpen: Boolean get() = state == QuestState.OPEN
    public val isDone: Boolean get() = state == QuestState.DONE
    public val isFailed: Boolean get() = state == QuestState.FAILED
    public val hasDue: Boolean get() = dueDate != null
    public val isRepeating: Boolean get() = repetition != null

    /** What this quest *would* pay if completed now, under the current balance. */
    public fun projectedXp(): Int = Balance.xp(difficulty, fear, estimatedMinutes)

    public fun projectedPoints(): Int = Balance.rewardPoints(difficulty, estimatedMinutes)
}

@Serializable
public data class Skill(
    val id: String,
    val name: String,
    val category: String? = null,
    val icon: String = "spark",
    val notes: String = "",
    /** Stored truth. Level is always derived, so curve changes cost nothing. */
    val totalXp: Long = 0,
    val createdAt: IsoInstant,
) {
    public val level: Int get() = Balance.levelForXp(totalXp)
    public val xpIntoLevel: Long get() = totalXp - Balance.xpForLevel(level)
    public val xpForNextLevel: Long get() = Balance.xpForLevel(level + 1) - Balance.xpForLevel(level)
    public val progressToNextLevel: Float
        get() = if (xpForNextLevel <= 0) 0f else (xpIntoLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)
    public val xpToNextLevel: Long get() = (Balance.xpForLevel(level + 1) - totalXp).coerceAtLeast(0)
}

@Serializable
public data class Reward(
    val id: String,
    val name: String,
    val icon: String = "gift",
    val notes: String = "",
    val cost: Int,
    /** Escalating price: +[costIncrement] after every [incrementEvery] claims. */
    val costIncrement: Int? = null,
    val incrementEvery: Int? = null,
    val claimCount: Int = 0,
    val lastClaimedAt: IsoInstant? = null,
    val createdAt: IsoInstant,
) {
    /** What the next claim costs, given how many times this has been claimed. */
    public val currentCost: Int
        get() {
            val increment = costIncrement ?: return cost
            val every = incrementEvery ?: return cost
            if (increment == 0 || every <= 0) return cost
            return cost + increment * (claimCount / every)
        }
}

@Serializable
public data class InventoryItem(
    val id: String,
    val name: String,
    val icon: String = "box",
    val notes: String = "",
    val quantity: Int = 1,
    val consumable: Boolean = true,
    val lastUsedAt: IsoInstant? = null,
    val createdAt: IsoInstant,
)

/**
 * A lightweight checklist line. Global when [questId] is null, otherwise
 * attached to a quest. Promotable into a real quest, which is the point:
 * capture first, classify later.
 */
@Serializable
public data class ScratchpadEntry(
    val id: String,
    val text: String,
    val questId: String? = null,
    val done: Boolean = false,
    val promotedToQuestId: String? = null,
    val createdAt: IsoInstant,
)

@Serializable
public data class Profile(
    val displayName: String = "",
    val avatar: String = "adventurer",
    val rewardPointBalance: Int = 0,
    val energyMode: EnergyMode = EnergyMode.SLEEP_SCHEDULE,
    val wakeTime: IsoTime = LocalTime.of(7, 30),
    val sleepTime: IsoTime = LocalTime.of(23, 30),
    val manualEnergy: Int? = null,
)

/** Everything the user can change that is not a quest, skill, reward or item. */
@Serializable
public data class Settings(
    val language: Language = Language.EN,
    val soundsEnabled: Boolean = true,
    /** §10 Q2: failure is recorded, and by default costs nothing. */
    val failureCostsPoints: Boolean = false,
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestTime: IsoTime = LocalTime.of(8, 30),
    val overdueSummaryOnLaunch: Boolean = true,
    val icsExportEnabled: Boolean = true,
    val markdownExportEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val trayEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val startInTray: Boolean = false,
)
