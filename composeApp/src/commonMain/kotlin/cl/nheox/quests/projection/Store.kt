package cl.nheox.quests.projection

import cl.nheox.quests.domain.Balance
import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.Ids
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.events.DeviceIdentity
import cl.nheox.quests.events.Event
import cl.nheox.quests.events.FieldPatch
import cl.nheox.quests.events.FileEventLog
import cl.nheox.quests.events.HlcGenerator
import cl.nheox.quests.events.LoadResult
import cl.nheox.quests.events.QuestFields
import cl.nheox.quests.events.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime

/** Everything needed to create a quest, in one argument. */
public data class QuestDraft(
    val title: String,
    val notes: String = "",
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val fear: Fear = Fear.DEFAULT,
    val urgency: Urgency = Urgency.DEFAULT,
    val estimatedMinutes: Int? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repetition: Repetition? = null,
    val reminders: List<Reminder> = emptyList(),
    val parentId: String? = null,
    val skillIds: Set<String> = emptySet(),
    val rewardBonus: Int? = null,
)

/** What the snackbar offers to take back. */
public sealed class Undoable {
    public abstract val label: UndoLabel

    public data class Completed(val questId: String, val title: String) : Undoable() {
        override val label: UndoLabel get() = UndoLabel.COMPLETED
    }

    public data class Failed(val questId: String, val title: String) : Undoable() {
        override val label: UndoLabel get() = UndoLabel.FAILED
    }

    public data class Deleted(val questIds: List<String>, val title: String) : Undoable() {
        override val label: UndoLabel get() = UndoLabel.DELETED
    }

    public data class Claimed(val rewardId: String, val name: String, val costPaid: Int) : Undoable() {
        override val label: UndoLabel get() = UndoLabel.CLAIMED
    }
}

public enum class UndoLabel {
    COMPLETED,
    FAILED,
    DELETED,
    CLAIMED,
}

/**
 * The application's command surface: the single place where an intent becomes
 * events, and the only thing in the app that writes to disk.
 *
 * Deliberately not a ViewModel and deliberately not injected by a framework.
 * Manual constructor injection, as settled in docs/ARCHITECTURE.md — the thing
 * has one instance for the life of the process.
 */
public class QuestStore(
    private val vault: Vault,
    private val device: DeviceIdentity,
    private val log: FileEventLog,
    private val clock: Clock = Clock.System,
    private val idRandom: kotlin.random.Random = kotlin.random.Random.Default,
) {
    private val hlc = HlcGenerator(device.id, clock::nowMillis)

    private val _state = MutableStateFlow(QuestsState())
    public val state: StateFlow<QuestsState> = _state.asStateFlow()

    private val _loadReport = MutableStateFlow<LoadResult?>(null)
    public val loadReport: StateFlow<LoadResult?> = _loadReport.asStateFlow()

    private val _undoable = MutableStateFlow<Undoable?>(null)
    public val undoable: StateFlow<Undoable?> = _undoable.asStateFlow()

    private val _levelUps = MutableStateFlow<List<LevelUp>>(emptyList())
    public val levelUps: StateFlow<List<LevelUp>> = _levelUps.asStateFlow()

    /** Notified after every write, so exports and the reminder scheduler refresh. */
    public var onChanged: ((QuestsState) -> Unit)? = null

    public val deviceId: String get() = device.id
    public val vaultRoot: java.nio.file.Path get() = vault.root

    // ------------------------------------------------------------ lifecycle

    /**
     * Replays the whole folder.
     *
     * Also folds every clock seen into the local one, so that anything written
     * afterwards sorts after everything already on disk even if this machine's
     * wall clock is behind another's.
     */
    public fun load() {
        val result = log.load()
        val highest = result.events.mapNotNull { it.clock }.maxOrNull()
        if (highest != null) hlc.observe(highest)
        val projected = Projector.replay(result.events)
        _loadReport.value = result
        _state.value = projected
        // Level-ups found while replaying history are not news, so they are not
        // announced; only the ones earned in this session are.
        _levelUps.value = emptyList()
        onChanged?.invoke(projected)
    }

    /** Re-reads the folder, for when a sync client has brought in new logs. */
    public fun reload(): Unit = load()

    public fun clearUndo() {
        _undoable.value = null
    }

    public fun consumeLevelUps(): List<LevelUp> {
        val pending = _levelUps.value
        _levelUps.value = emptyList()
        return pending
    }

    // --------------------------------------------------------------- quests

    public fun createQuest(draft: QuestDraft): String {
        val now = clock.now()
        val id = Ids.quest(now.toEpochMilli(), idRandom)
        val order = nextManualOrder(draft.parentId)
        emit(
            Event.QuestCreated(
                ts = now,
                hlc = stamp(),
                device = device.id,
                id = id,
                title = draft.title.trim(),
                notes = draft.notes,
                difficulty = draft.difficulty,
                fear = draft.fear,
                urgency = draft.urgency,
                estimatedMinutes = draft.estimatedMinutes,
                dueDate = draft.dueDate,
                dueTime = draft.dueTime,
                repetition = draft.repetition,
                reminders = draft.reminders,
                parentId = draft.parentId,
                manualOrder = order,
                skillIds = draft.skillIds,
                rewardBonus = draft.rewardBonus,
            ),
        )
        return id
    }

    public fun changeQuest(id: String, patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.QuestChanged(clock.now(), stamp(), device.id, id, patch.build()))
    }

    /**
     * Completes a quest: pays out, and for a repeating quest also creates the
     * next occurrence.
     *
     * The payout is computed here and *recorded in the event*, so retuning the
     * balance later never rewrites what somebody already earned.
     */
    public fun completeQuest(id: String) {
        val quest = _state.value.quests[id] ?: return
        if (!quest.isOpen) return
        val now = clock.now()
        val xp = quest.projectedXp()
        val points = quest.projectedPoints()
        val distribution = Balance.distribute(xp, quest.skillIds.toList())

        val events = mutableListOf<Event>()
        var nextId: String? = null

        val repetition = quest.repetition
        if (repetition != null) {
            // A repeating quest becomes a *new* quest rather than resetting
            // itself, so the history of how often it actually got done survives.
            val (nextDate, nextTime) = repetition.next(
                quest.dueDate ?: clock.today(),
                quest.dueTime,
            )
            nextId = Ids.quest(now.toEpochMilli(), idRandom)
            events += Event.QuestCreated(
                ts = now,
                hlc = stamp(),
                device = device.id,
                id = nextId,
                title = quest.title,
                notes = quest.notes,
                difficulty = quest.difficulty,
                fear = quest.fear,
                urgency = quest.urgency,
                estimatedMinutes = quest.estimatedMinutes,
                dueDate = nextDate,
                dueTime = nextTime,
                repetition = repetition,
                reminders = quest.reminders,
                parentId = quest.parentId,
                manualOrder = quest.manualOrder,
                skillIds = quest.skillIds,
                rewardBonus = quest.rewardBonus,
            )
        }

        events += Event.QuestCompleted(
            ts = now,
            hlc = stamp(),
            device = device.id,
            id = id,
            at = now,
            xp = xp,
            points = points,
            skillXp = distribution,
            nextQuestId = nextId,
        )

        emitAll(events)
        _undoable.value = Undoable.Completed(id, quest.title)
    }

    public fun failQuest(id: String) {
        val quest = _state.value.quests[id] ?: return
        if (!quest.isOpen) return
        val now = clock.now()
        // §10 Q2 answered as a setting rather than as a hard rule: failure is
        // recorded always, and costs points only if the user asked for stakes.
        val cost = if (_state.value.settings.failureCostsPoints) quest.projectedPoints() else 0
        emit(Event.QuestFailed(now, stamp(), device.id, id, now, cost))
        _undoable.value = Undoable.Failed(id, quest.title)
    }

    public fun reopenQuest(id: String) {
        val quest = _state.value.quests[id] ?: return
        if (quest.isOpen) return
        val returned = Balance.distribute(quest.xpAwarded, quest.skillIds.toList())
        emit(
            Event.QuestReopened(
                ts = clock.now(),
                hlc = stamp(),
                device = device.id,
                id = id,
                xpReturned = quest.xpAwarded,
                pointsReturned = quest.rewardPoints,
                skillXpReturned = returned,
            ),
        )
    }

    public fun markWorked(id: String) {
        val now = clock.now()
        emit(Event.QuestWorked(now, stamp(), device.id, id, now))
    }

    public fun deleteQuest(id: String, includeChildren: Boolean = true) {
        val current = _state.value
        val quest = current.quests[id] ?: return
        val targets = if (includeChildren) current.branch(id).map { it.id } else listOf(id)
        val now = clock.now()
        // Children first, so a crash mid-write never leaves a child pointing at
        // a parent that is already gone.
        emitAll(targets.reversed().map { Event.QuestDeleted(now, stamp(), device.id, it) })
        _undoable.value = Undoable.Deleted(targets, quest.title)
    }

    public fun duplicateQuest(id: String): String? {
        val quest = _state.value.quests[id] ?: return null
        return createQuest(
            QuestDraft(
                title = quest.title,
                notes = quest.notes,
                difficulty = quest.difficulty,
                fear = quest.fear,
                urgency = quest.urgency,
                estimatedMinutes = quest.estimatedMinutes,
                dueDate = quest.dueDate,
                dueTime = quest.dueTime,
                repetition = quest.repetition,
                reminders = quest.reminders,
                parentId = quest.parentId,
                skillIds = quest.skillIds,
                rewardBonus = quest.rewardBonus,
            ),
        )
    }

    public fun reschedule(id: String, date: LocalDate?, time: LocalTime? = null) {
        val patch = FieldPatch().setDate(QuestFields.DUE_DATE, date)
        if (date == null) patch.clear(QuestFields.DUE_TIME) else patch.setTime(QuestFields.DUE_TIME, time)
        changeQuest(id, patch)
    }

    /** "Next repetition" — pushes the due date on by one cycle. */
    public fun advanceToNextRepetition(id: String) {
        val quest = _state.value.quests[id] ?: return
        val repetition = quest.repetition ?: return
        val (date, time) = repetition.next(quest.dueDate ?: clock.today(), quest.dueTime)
        reschedule(id, date, time)
    }

    public fun reparent(id: String, parentId: String?) {
        if (id == parentId) return
        // Re-parenting into one's own branch would make a cycle, and a cycle in
        // the Plan tree is an infinite expansion, not a cosmetic problem.
        if (parentId != null && _state.value.branch(id).any { it.id == parentId }) return
        changeQuest(id, FieldPatch().setStringOrNull(QuestFields.PARENT_ID, parentId))
    }

    public fun promoteToTopLevel(id: String): Unit = reparent(id, null)

    public fun moveToTop(id: String) {
        val quest = _state.value.quests[id] ?: return
        val siblings = siblingsOf(quest)
        val lowest = siblings.minOfOrNull { it.manualOrder } ?: 0
        changeQuest(id, FieldPatch().set(QuestFields.MANUAL_ORDER, lowest - 1))
    }

    public fun moveToBottom(id: String) {
        val quest = _state.value.quests[id] ?: return
        val siblings = siblingsOf(quest)
        val highest = siblings.maxOfOrNull { it.manualOrder } ?: 0
        changeQuest(id, FieldPatch().set(QuestFields.MANUAL_ORDER, highest + 1))
    }

    public fun setSkills(id: String, skillIds: Set<String>) {
        changeQuest(id, FieldPatch().setStrings(QuestFields.SKILL_IDS, skillIds))
    }

    // ---------------------------------------------------------- bulk actions

    public fun completeAll(ids: Collection<String>): Unit = ids.forEach(::completeQuest)

    public fun failAll(ids: Collection<String>): Unit = ids.forEach(::failQuest)

    public fun deleteAll(ids: Collection<String>) {
        val now = clock.now()
        val targets = ids.distinct()
        if (targets.isEmpty()) return
        val title = _state.value.quests[targets.first()]?.title.orEmpty()
        emitAll(targets.map { Event.QuestDeleted(now, stamp(), device.id, it) })
        _undoable.value = Undoable.Deleted(targets, title)
    }

    public fun rescheduleAll(ids: Collection<String>, date: LocalDate?, time: LocalTime? = null) {
        ids.forEach { reschedule(it, date, time) }
    }

    public fun assignSkillsToAll(ids: Collection<String>, skillIds: Set<String>, replace: Boolean = false) {
        val current = _state.value
        ids.forEach { id ->
            val quest = current.quests[id] ?: return@forEach
            val merged = if (replace) skillIds else quest.skillIds + skillIds
            setSkills(id, merged)
        }
    }

    public fun setDifficultyForAll(ids: Collection<String>, difficulty: Difficulty) {
        ids.forEach { changeQuest(it, FieldPatch().setEnum(QuestFields.DIFFICULTY, difficulty)) }
    }

    public fun setUrgencyForAll(ids: Collection<String>, urgency: Urgency) {
        ids.forEach { changeQuest(it, FieldPatch().setEnum(QuestFields.URGENCY, urgency)) }
    }

    public fun setFearForAll(ids: Collection<String>, fear: Fear) {
        ids.forEach { changeQuest(it, FieldPatch().setEnum(QuestFields.FEAR, fear)) }
    }

    // --------------------------------------------------------------- skills

    public fun createSkill(
        name: String,
        category: String? = null,
        icon: String = "spark",
        notes: String = "",
    ): String {
        val now = clock.now()
        val id = Ids.skill(now.toEpochMilli(), idRandom)
        emit(Event.SkillCreated(now, stamp(), device.id, id, name.trim(), category, icon, notes))
        return id
    }

    /** Used by quick-add, where `#thesis` should just work the first time too. */
    public fun findOrCreateSkill(name: String): String {
        val trimmed = name.trim()
        _state.value.skillByName(trimmed)?.let { return it.id }
        return createSkill(trimmed)
    }

    public fun changeSkill(id: String, patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.SkillChanged(clock.now(), stamp(), device.id, id, patch.build()))
    }

    public fun deleteSkill(id: String) {
        emit(Event.SkillDeleted(clock.now(), stamp(), device.id, id))
    }

    public fun grantSkillXp(id: String, xp: Int, reason: String = "") {
        if (xp == 0) return
        emit(Event.SkillXpGranted(clock.now(), stamp(), device.id, id, xp, reason))
    }

    // -------------------------------------------------------------- rewards

    public fun createReward(
        name: String,
        cost: Int,
        icon: String = "gift",
        notes: String = "",
        costIncrement: Int? = null,
        incrementEvery: Int? = null,
    ): String {
        val now = clock.now()
        val id = Ids.reward(now.toEpochMilli(), idRandom)
        emit(
            Event.RewardCreated(
                now, stamp(), device.id, id, name.trim(), cost, icon, notes, costIncrement, incrementEvery,
            ),
        )
        return id
    }

    public fun changeReward(id: String, patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.RewardChanged(clock.now(), stamp(), device.id, id, patch.build()))
    }

    public fun deleteReward(id: String) {
        emit(Event.RewardDeleted(clock.now(), stamp(), device.id, id))
    }

    /** Returns false when there are not enough points, so the UI can say so. */
    public fun claimReward(id: String): Boolean {
        val current = _state.value
        val reward = current.rewards[id] ?: return false
        val price = reward.currentCost
        if (current.profile.rewardPointBalance < price) return false
        val now = clock.now()
        emit(Event.RewardClaimed(now, stamp(), device.id, id, now, price))
        _undoable.value = Undoable.Claimed(id, reward.name, price)
        return true
    }

    // ------------------------------------------------------------ inventory

    public fun createItem(
        name: String,
        quantity: Int = 1,
        consumable: Boolean = true,
        icon: String = "box",
        notes: String = "",
    ): String {
        val now = clock.now()
        val id = Ids.item(now.toEpochMilli(), idRandom)
        emit(Event.ItemCreated(now, stamp(), device.id, id, name.trim(), icon, notes, quantity, consumable))
        return id
    }

    public fun changeItem(id: String, patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.ItemChanged(clock.now(), stamp(), device.id, id, patch.build()))
    }

    public fun deleteItem(id: String) {
        emit(Event.ItemDeleted(clock.now(), stamp(), device.id, id))
    }

    public fun adjustItemStock(id: String, delta: Int) {
        if (delta == 0) return
        emit(Event.ItemStockChanged(clock.now(), stamp(), device.id, id, delta))
    }

    public fun useItem(id: String, quantity: Int = 1) {
        val item = _state.value.items[id] ?: return
        if (item.consumable && item.quantity < quantity) return
        val now = clock.now()
        emit(Event.ItemUsed(now, stamp(), device.id, id, now, quantity))
    }

    // ----------------------------------------------------------- scratchpad

    public fun addNote(text: String, questId: String? = null): String {
        val now = clock.now()
        val id = Ids.note(now.toEpochMilli(), idRandom)
        emit(Event.NoteCreated(now, stamp(), device.id, id, text.trim(), questId))
        return id
    }

    public fun changeNote(id: String, patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.NoteChanged(clock.now(), stamp(), device.id, id, patch.build()))
    }

    public fun deleteNote(id: String) {
        emit(Event.NoteDeleted(clock.now(), stamp(), device.id, id))
    }

    public fun toggleNote(id: String) {
        val note = _state.value.notes[id] ?: return
        changeNote(id, FieldPatch().set(cl.nheox.quests.events.NoteFields.DONE, !note.done))
    }

    /** Promotes a checklist line into a real quest, keeping the link both ways. */
    public fun promoteNote(id: String, draft: QuestDraft? = null): String? {
        val note = _state.value.notes[id] ?: return null
        if (note.promotedToQuestId != null) return note.promotedToQuestId
        val questId = createQuest(draft ?: QuestDraft(title = note.text, parentId = note.questId))
        emit(Event.NotePromoted(clock.now(), stamp(), device.id, id, questId))
        return questId
    }

    // ------------------------------------------------------ profile, purse

    public fun changeProfile(patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.ProfileChanged(clock.now(), stamp(), device.id, patch.build()))
    }

    public fun changeSettings(patch: FieldPatch) {
        if (patch.isEmpty) return
        emit(Event.SettingsChanged(clock.now(), stamp(), device.id, patch.build()))
    }

    public fun adjustPoints(delta: Int, reason: String = "") {
        if (delta == 0) return
        emit(Event.PointsAdjusted(clock.now(), stamp(), device.id, delta, reason))
    }

    // ------------------------------------------------------------------ undo

    /**
     * Takes back the last destructive action.
     *
     * Undo is itself recorded as events rather than as a rollback of the log:
     * an append-only log has no rewind, and "what happened" genuinely includes
     * having changed one's mind.
     */
    public fun undo() {
        when (val action = _undoable.value) {
            null -> return
            is Undoable.Completed -> reopenQuest(action.questId)
            is Undoable.Failed -> reopenQuest(action.questId)
            is Undoable.Deleted -> {
                val now = clock.now()
                emitAll(action.questIds.map { Event.QuestRestored(now, stamp(), device.id, it) })
            }

            is Undoable.Claimed -> {
                val now = clock.now()
                // Claim counts are additive history; refunding the points is the
                // honest inverse, and the claim stays on the record.
                emitAll(
                    listOf(
                        Event.PointsAdjusted(now, stamp(), device.id, action.costPaid, "undo claim"),
                    ),
                )
            }
        }
        _undoable.value = null
    }

    // --------------------------------------------------------------- plumbing

    private fun stamp(): String = hlc.next().encode()

    private fun emit(event: Event): Unit = emitAll(listOf(event))

    /**
     * Appends to disk first, then updates memory.
     *
     * That order matters: if the write fails — a full disk, a sync folder on an
     * unmounted drive — the UI must not show a change that is not on disk. The
     * exception propagates and the state is left alone.
     */
    private fun emitAll(events: List<Event>) {
        if (events.isEmpty()) return
        log.append(events)
        val next = Projector.apply(_state.value, events)
        val fresh = next.levelUps.drop(_state.value.levelUps.size)
        _state.value = next
        if (fresh.isNotEmpty()) _levelUps.value = _levelUps.value + fresh
        onChanged?.invoke(next)
    }

    private fun nextManualOrder(parentId: String?): Int {
        val siblings = _state.value.quests.values.filter { it.parentId == parentId }
        return (siblings.maxOfOrNull { it.manualOrder } ?: 0) + 1
    }

    private fun siblingsOf(quest: Quest): List<Quest> =
        _state.value.quests.values.filter { it.parentId == quest.parentId }

    public companion object {
        /** Wires the whole stack up from a folder. */
        public fun open(
            root: java.nio.file.Path,
            device: DeviceIdentity,
            clock: Clock = Clock.System,
        ): QuestStore {
            val vault = Vault(root)
            vault.prepare()
            return QuestStore(vault, device, FileEventLog(vault, device), clock)
        }
    }
}
