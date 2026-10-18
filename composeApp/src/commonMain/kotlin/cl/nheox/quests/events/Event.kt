package cl.nheox.quests.events

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.IsoDate
import cl.nheox.quests.domain.IsoInstant
import cl.nheox.quests.domain.IsoTime
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.domain.Urgency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One line of the append-only log, and the only way anything in this app ever
 * changes.
 *
 * Every event carries:
 *  - [ts], a wall-clock reading, for a human reading the file in an editor;
 *  - [hlc], the hybrid logical clock, which is what actually orders events;
 *  - [device], the name of the log that owns the line.
 *
 * Changes to existing entities travel as a map of changed fields rather than as
 * a whole replacement object. That is what makes last-write-wins work *per
 * field*: two devices that edit the title and the due date of the same quest
 * while both offline each keep their edit, instead of one clobbering the other
 * wholesale. An explicit JSON `null` in the map means "clear this field", which
 * is distinct from the field being absent, meaning "leave it alone".
 */
@Serializable
public sealed class Event {
    public abstract val ts: IsoInstant
    public abstract val hlc: String
    public abstract val device: String

    /** Decoded once per line on load; the string form is what is on disk. */
    public val clock: Hlc? get() = Hlc.decode(hlc)

    // ---------------------------------------------------------------- quests

    @Serializable
    @SerialName("QuestCreated")
    public data class QuestCreated(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
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
        val skillIds: Set<String> = emptySet(),
    ) : Event()

    @Serializable
    @SerialName("QuestChanged")
    public data class QuestChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    /**
     * Completion carries its own payout.
     *
     * The alternative — recomputing XP from the formula at replay time — would
     * mean that retuning the balance silently rewrote history, so a skill you
     * levelled last year could un-level itself after an update. Recording what
     * was actually paid keeps the past fixed and keeps replay reproducible.
     */
    @Serializable
    @SerialName("QuestCompleted")
    public data class QuestCompleted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val at: IsoInstant,
        val xp: Int,
        val points: Int,
        val skillXp: Map<String, Int> = emptyMap(),
        /** Set when a repeating quest spawned its next occurrence. */
        val nextQuestId: String? = null,
    ) : Event()

    @Serializable
    @SerialName("QuestFailed")
    public data class QuestFailed(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val at: IsoInstant,
        val pointsLost: Int = 0,
    ) : Event()

    @Serializable
    @SerialName("QuestReopened")
    public data class QuestReopened(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        /** Completion paid out; reopening takes it back, or history stops adding up. */
        val xpReturned: Int = 0,
        val pointsReturned: Int = 0,
        val skillXpReturned: Map<String, Int> = emptyMap(),
    ) : Event()

    @Serializable
    @SerialName("QuestWorked")
    public data class QuestWorked(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val at: IsoInstant,
    ) : Event()

    /** A tombstone. The log never physically forgets anything. */
    @Serializable
    @SerialName("QuestDeleted")
    public data class QuestDeleted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
    ) : Event()

    @Serializable
    @SerialName("QuestRestored")
    public data class QuestRestored(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
    ) : Event()

    // ---------------------------------------------------------------- skills

    @Serializable
    @SerialName("SkillCreated")
    public data class SkillCreated(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val name: String,
        val category: String? = null,
        val icon: String = "spark",
        val notes: String = "",
    ) : Event()

    @Serializable
    @SerialName("SkillChanged")
    public data class SkillChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    @Serializable
    @SerialName("SkillDeleted")
    public data class SkillDeleted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
    ) : Event()

    /** Out-of-band XP: a correction, an import, or a one-off bonus. */
    @Serializable
    @SerialName("SkillXpGranted")
    public data class SkillXpGranted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val xp: Int,
        val reason: String = "",
    ) : Event()

    // --------------------------------------------------------------- rewards

    @Serializable
    @SerialName("RewardCreated")
    public data class RewardCreated(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val name: String,
        val cost: Int,
        val icon: String = "gift",
        val notes: String = "",
        val costIncrement: Int? = null,
        val incrementEvery: Int? = null,
    ) : Event()

    @Serializable
    @SerialName("RewardChanged")
    public data class RewardChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    @Serializable
    @SerialName("RewardDeleted")
    public data class RewardDeleted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
    ) : Event()

    @Serializable
    @SerialName("RewardClaimed")
    public data class RewardClaimed(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val at: IsoInstant,
        val costPaid: Int,
    ) : Event()

    // ------------------------------------------------------------- inventory

    @Serializable
    @SerialName("ItemCreated")
    public data class ItemCreated(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val name: String,
        val icon: String = "box",
        val notes: String = "",
        val quantity: Int = 1,
        val consumable: Boolean = true,
    ) : Event()

    @Serializable
    @SerialName("ItemChanged")
    public data class ItemChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    @Serializable
    @SerialName("ItemDeleted")
    public data class ItemDeleted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
    ) : Event()

    /** Additive, so two devices stocking the same item both count. */
    @Serializable
    @SerialName("ItemStockChanged")
    public data class ItemStockChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val delta: Int,
    ) : Event()

    @Serializable
    @SerialName("ItemUsed")
    public data class ItemUsed(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val at: IsoInstant,
        val quantity: Int = 1,
    ) : Event()

    // ------------------------------------------------------------ scratchpad

    @Serializable
    @SerialName("NoteCreated")
    public data class NoteCreated(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val text: String,
        val questId: String? = null,
    ) : Event()

    @Serializable
    @SerialName("NoteChanged")
    public data class NoteChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    @Serializable
    @SerialName("NoteDeleted")
    public data class NoteDeleted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
    ) : Event()

    @Serializable
    @SerialName("NotePromoted")
    public data class NotePromoted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val id: String,
        val questId: String,
    ) : Event()

    // ------------------------------------------------------ profile and purse

    @Serializable
    @SerialName("ProfileChanged")
    public data class ProfileChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    @Serializable
    @SerialName("SettingsChanged")
    public data class SettingsChanged(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val fields: Map<String, JsonElement>,
    ) : Event()

    @Serializable
    @SerialName("PointsAdjusted")
    public data class PointsAdjusted(
        override val ts: IsoInstant,
        override val hlc: String,
        override val device: String,
        val delta: Int,
        val reason: String = "",
    ) : Event()
}
