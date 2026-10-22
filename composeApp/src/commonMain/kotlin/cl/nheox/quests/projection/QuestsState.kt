package cl.nheox.quests.projection

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.InventoryItem
import cl.nheox.quests.domain.IsoInstant
import cl.nheox.quests.domain.Profile
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.Reward
import cl.nheox.quests.domain.ScratchpadEntry
import cl.nheox.quests.domain.Settings
import cl.nheox.quests.domain.Skill

/**
 * A completion, kept as history rather than folded away.
 *
 * The fear log and the trend charts are the whole reason this exists: knowing
 * that you finish 80% of low-fear quests and 22% of high-fear ones requires
 * remembering the fear rating each finished quest *had at the time*, which a
 * current-state projection throws away.
 */
public data class CompletionRecord(
    val questId: String,
    val title: String,
    val at: IsoInstant,
    val xp: Int,
    val points: Int,
    val difficulty: Difficulty,
    val fear: Fear,
    val estimatedMinutes: Int?,
    val skillIds: Set<String>,
)

public data class FailureRecord(
    val questId: String,
    val title: String,
    val at: IsoInstant,
    val difficulty: Difficulty,
    val fear: Fear,
    val pointsLost: Int,
)

public data class ClaimRecord(
    val rewardId: String,
    val name: String,
    val at: IsoInstant,
    val costPaid: Int,
)

/** A level crossed during this replay, so the UI can celebrate it once. */
public data class LevelUp(
    val skillId: String,
    val skillName: String,
    val fromLevel: Int,
    val toLevel: Int,
    val at: IsoInstant,
)

/**
 * The whole world, derived from the logs and authoritative over nothing.
 *
 * Immutable: the projector produces a new one per fold step. For a personal
 * quest list this costs nothing measurable and buys a trivially correct undo
 * and a UI that can never observe a half-applied change.
 */
public data class QuestsState(
    val quests: Map<String, Quest> = emptyMap(),
    /**
     * Tombstoned quests, kept with their last known contents.
     *
     * The log never physically deletes anything, and neither does this: holding
     * the deleted value means undo can restore a quest *exactly* as it was,
     * including a completion, without re-emitting a completion event and so
     * paying its XP a second time.
     */
    val deletedQuests: Map<String, Quest> = emptyMap(),
    val deletedQuestIds: Set<String> = emptySet(),
    val skills: Map<String, Skill> = emptyMap(),
    val rewards: Map<String, Reward> = emptyMap(),
    val items: Map<String, InventoryItem> = emptyMap(),
    val notes: Map<String, ScratchpadEntry> = emptyMap(),
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val completions: List<CompletionRecord> = emptyList(),
    val failures: List<FailureRecord> = emptyList(),
    val claims: List<ClaimRecord> = emptyList(),
    val levelUps: List<LevelUp> = emptyList(),
    val eventCount: Int = 0,
) {
    public val openQuests: List<Quest> get() = quests.values.filter { it.isOpen }

    public fun quest(id: String?): Quest? = id?.let { quests[it] }

    public fun skill(id: String?): Skill? = id?.let { skills[it] }

    /**
     * A quest's direct children, in creation order.
     *
     * Creation order because it needs no clock: this is what delete, export and
     * counts walk, and none of them should depend on the time of day. Anything
     * that *presents* siblings orders them with
     * [cl.nheox.quests.domain.Priority.order], as the Plan tree does.
     */
    public fun children(parentId: String): List<Quest> =
        quests.values.filter { it.parentId == parentId }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    public fun roots(): List<Quest> =
        quests.values.filter { it.parentId == null || it.parentId !in quests }

    /** Every descendant of a quest, depth first. Used by export and by delete. */
    public fun branch(id: String): List<Quest> {
        val root = quests[id] ?: return emptyList()
        val out = mutableListOf(root)
        val queue = ArrayDeque(children(id))
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            out += next
            queue.addAll(children(next.id))
        }
        return out
    }

    /**
     * A quest's ancestors, outermost first — the path a picker shows so two
     * quests with one title can be told apart. Stops at a missing parent, and
     * at a cycle should a hand-edited log ever contain one.
     */
    public fun ancestors(id: String): List<Quest> {
        val out = ArrayDeque<Quest>()
        val seen = mutableSetOf(id)
        var parentId = quests[id]?.parentId
        while (parentId != null && seen.add(parentId)) {
            val parent = quests[parentId] ?: break
            out.addFirst(parent)
            parentId = parent.parentId
        }
        return out.toList()
    }

    /**
     * Where [ids] can be moved: every open quest except those being moved and
     * everything under them, since a quest inside its own branch would make
     * the Plan tree a cycle.
     */
    public fun moveTargets(ids: Collection<String>): List<Quest> {
        val excluded = ids.flatMap { branch(it) }.mapTo(mutableSetOf()) { it.id }
        return quests.values.filter { it.isOpen && it.id !in excluded }
    }

    public fun skillsOf(quest: Quest): List<Skill> =
        quest.skillIds.mapNotNull { skills[it] }.sortedBy { it.name.lowercase() }

    public fun skillByName(name: String): Skill? =
        skills.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    public val totalXp: Long get() = skills.values.sumOf { it.totalXp }

    public fun notesFor(questId: String): List<ScratchpadEntry> =
        notes.values.filter { it.questId == questId }.sortedBy { it.createdAt }

    public fun globalNotes(): List<ScratchpadEntry> =
        notes.values.filter { it.questId == null }.sortedBy { it.createdAt }
}
