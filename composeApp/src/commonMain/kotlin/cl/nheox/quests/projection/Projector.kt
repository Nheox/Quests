package cl.nheox.quests.projection

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.EnergyMode
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.InventoryItem
import cl.nheox.quests.domain.Iso
import cl.nheox.quests.domain.Language
import cl.nheox.quests.domain.LaunchView
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.QuestState
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.domain.Reward
import cl.nheox.quests.domain.ScratchpadEntry
import cl.nheox.quests.domain.Skill
import cl.nheox.quests.domain.Urgency
import cl.nheox.quests.events.Event
import cl.nheox.quests.events.EventCodec
import cl.nheox.quests.events.Hlc
import cl.nheox.quests.events.ItemFields
import cl.nheox.quests.events.NoteFields
import cl.nheox.quests.events.ProfileFields
import cl.nheox.quests.events.QuestFields
import cl.nheox.quests.events.RewardFields
import cl.nheox.quests.events.SettingsFields
import cl.nheox.quests.events.SkillFields
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalTime

/**
 * Replays logs into state.
 *
 * Two properties matter here, and both are tested:
 *
 *  1. **Determinism.** Events are sorted by their hybrid logical clock before
 *     folding, and that clock is a *total* order (it includes the node id), so
 *     the result does not depend on which log happened to be read first.
 *
 *  2. **Last-write-wins, per field, for free.** Because the fold runs in clock
 *     order, a later edit to a field simply overwrites an earlier one as it
 *     goes. There is no separate per-field clock table to keep, and no way for
 *     it to disagree with the log.
 */
public object Projector {
    public fun replay(events: Collection<Event>): QuestsState =
        fold(QuestsState(), sortByClock(events))

    /**
     * Folds new events into existing state.
     *
     * Only sound for events that sort after everything already applied, which
     * is exactly the case for events this device has just generated. Anything
     * read from disk goes through [replay].
     */
    public fun apply(state: QuestsState, events: Collection<Event>): QuestsState =
        fold(state, sortByClock(events))

    public fun sortByClock(events: Collection<Event>): List<Event> =
        events.sortedWith(compareBy(Hlc.ENCODED_ORDER) { it.hlc })

    private fun fold(initial: QuestsState, ordered: List<Event>): QuestsState {
        var state = initial
        for (event in ordered) {
            state = applyOne(state, event)
        }
        return state.copy(eventCount = initial.eventCount + ordered.size)
    }

    private fun applyOne(state: QuestsState, event: Event): QuestsState = when (event) {
        is Event.QuestCreated -> {
            // A tombstone always wins over a late-arriving creation, otherwise a
            // deleted quest could reappear when an old log finally syncs.
            if (event.id in state.deletedQuestIds) {
                state
            } else {
                state.copy(
                    quests = state.quests + (
                        event.id to Quest(
                            id = event.id,
                            title = event.title,
                            notes = event.notes,
                            difficulty = event.difficulty,
                            fear = event.fear,
                            urgency = event.urgency,
                            estimatedMinutes = event.estimatedMinutes,
                            dueDate = event.dueDate,
                            dueTime = event.dueTime,
                            repetition = event.repetition,
                            reminders = event.reminders,
                            parentId = event.parentId,
                            // QuestCreated.manualOrder is legacy creation order,
                            // never a pin, so it is not read: every quest starts
                            // in the automatic order.
                            skillIds = event.skillIds,
                            createdAt = event.ts,
                            updatedAt = event.ts,
                            rewardBonus = event.rewardBonus,
                        )
                        ),
                )
            }
        }

        is Event.QuestChanged -> state.updateQuest(event.id) { quest ->
            var updated = quest
            for ((key, value) in event.fields) {
                updated = applyQuestField(updated, key, value)
            }
            updated.copy(updatedAt = event.ts)
        }

        is Event.QuestCompleted -> {
            val quest = state.quests[event.id]
            if (quest == null) {
                state
            } else {
                val completed = quest.copy(
                    state = QuestState.DONE,
                    completedAt = event.at,
                    updatedAt = event.ts,
                    lastWorkedAt = event.at,
                    xpAwarded = event.xp,
                    rewardPoints = event.points,
                )
                val (skills, levelUps) = grantXp(state.skills, event.skillXp, event.at)
                state.copy(
                    quests = state.quests + (event.id to completed),
                    skills = skills,
                    levelUps = state.levelUps + levelUps,
                    profile = state.profile.copy(
                        rewardPointBalance = state.profile.rewardPointBalance + event.points,
                    ),
                    completions = state.completions + CompletionRecord(
                        questId = quest.id,
                        title = quest.title,
                        at = event.at,
                        xp = event.xp,
                        points = event.points,
                        difficulty = quest.difficulty,
                        fear = quest.fear,
                        estimatedMinutes = quest.estimatedMinutes,
                        skillIds = quest.skillIds,
                    ),
                )
            }
        }

        is Event.QuestFailed -> {
            val quest = state.quests[event.id]
            if (quest == null) {
                state
            } else {
                state.copy(
                    quests = state.quests + (
                        event.id to quest.copy(
                            state = QuestState.FAILED,
                            updatedAt = event.ts,
                            completedAt = event.at,
                        )
                        ),
                    profile = state.profile.copy(
                        rewardPointBalance = state.profile.rewardPointBalance - event.pointsLost,
                    ),
                    failures = state.failures + FailureRecord(
                        questId = quest.id,
                        title = quest.title,
                        at = event.at,
                        difficulty = quest.difficulty,
                        fear = quest.fear,
                        pointsLost = event.pointsLost,
                    ),
                )
            }
        }

        is Event.QuestReopened -> {
            val quest = state.quests[event.id]
            if (quest == null) {
                state
            } else {
                val (skills, _) = grantXp(
                    state.skills,
                    event.skillXpReturned.mapValues { -it.value },
                    event.ts,
                    allowLevelUps = false,
                )
                state.copy(
                    quests = state.quests + (
                        event.id to quest.copy(
                            state = QuestState.OPEN,
                            completedAt = null,
                            updatedAt = event.ts,
                            xpAwarded = 0,
                            rewardPoints = 0,
                        )
                        ),
                    skills = skills,
                    profile = state.profile.copy(
                        rewardPointBalance = state.profile.rewardPointBalance - event.pointsReturned,
                    ),
                    // Reopening withdraws the completion from the history too, so
                    // the fear log is not quietly inflated by undo.
                    completions = state.completions.filterNot { it.questId == event.id && it.at == quest.completedAt },
                    failures = state.failures.filterNot { it.questId == event.id && it.at == quest.completedAt },
                )
            }
        }

        is Event.QuestWorked -> state.updateQuest(event.id) {
            it.copy(lastWorkedAt = event.at, updatedAt = event.ts)
        }

        is Event.QuestDeleted -> {
            val doomed = state.quests[event.id] ?: state.deletedQuests[event.id]
            state.copy(
                quests = state.quests - event.id,
                deletedQuests = if (doomed == null) state.deletedQuests else state.deletedQuests + (event.id to doomed),
                deletedQuestIds = state.deletedQuestIds + event.id,
                // Orphaned children are promoted to top level rather than hidden,
                // so deleting a parent can never silently take a branch out of
                // every view.
            ).promoteOrphans(event.id, event.ts)
        }

        is Event.QuestRestored -> {
            val revived = state.deletedQuests[event.id]
            state.copy(
                quests = if (revived == null) state.quests else state.quests + (event.id to revived),
                deletedQuests = state.deletedQuests - event.id,
                deletedQuestIds = state.deletedQuestIds - event.id,
            )
        }

        is Event.SkillCreated -> state.copy(
            skills = state.skills + (
                event.id to (
                    state.skills[event.id]?.copy(
                        name = event.name,
                        category = event.category,
                        icon = event.icon,
                        notes = event.notes,
                    ) ?: Skill(
                        id = event.id,
                        name = event.name,
                        category = event.category,
                        icon = event.icon,
                        notes = event.notes,
                        createdAt = event.ts,
                    )
                    )
                ),
        )

        is Event.SkillChanged -> state.updateSkill(event.id) { skill ->
            var updated = skill
            for ((key, value) in event.fields) {
                updated = when (key) {
                    SkillFields.NAME -> value.asString()?.let { updated.copy(name = it) } ?: updated
                    SkillFields.CATEGORY -> updated.copy(category = value.asStringOrNull())
                    SkillFields.ICON -> value.asString()?.let { updated.copy(icon = it) } ?: updated
                    SkillFields.NOTES -> value.asString()?.let { updated.copy(notes = it) } ?: updated
                    else -> updated
                }
            }
            updated
        }

        is Event.SkillDeleted -> state.copy(
            skills = state.skills - event.id,
            quests = state.quests.mapValues { (_, quest) ->
                if (event.id in quest.skillIds) quest.copy(skillIds = quest.skillIds - event.id) else quest
            },
        )

        is Event.SkillXpGranted -> {
            val (skills, levelUps) = grantXp(state.skills, mapOf(event.id to event.xp), event.ts)
            state.copy(skills = skills, levelUps = state.levelUps + levelUps)
        }

        is Event.RewardCreated -> state.copy(
            rewards = state.rewards + (
                event.id to (
                    state.rewards[event.id]?.copy(
                        name = event.name,
                        cost = event.cost,
                        icon = event.icon,
                        notes = event.notes,
                        costIncrement = event.costIncrement,
                        incrementEvery = event.incrementEvery,
                    ) ?: Reward(
                        id = event.id,
                        name = event.name,
                        cost = event.cost,
                        icon = event.icon,
                        notes = event.notes,
                        costIncrement = event.costIncrement,
                        incrementEvery = event.incrementEvery,
                        createdAt = event.ts,
                    )
                    )
                ),
        )

        is Event.RewardChanged -> state.updateReward(event.id) { reward ->
            var updated = reward
            for ((key, value) in event.fields) {
                updated = when (key) {
                    RewardFields.NAME -> value.asString()?.let { updated.copy(name = it) } ?: updated
                    RewardFields.ICON -> value.asString()?.let { updated.copy(icon = it) } ?: updated
                    RewardFields.NOTES -> value.asString()?.let { updated.copy(notes = it) } ?: updated
                    RewardFields.COST -> value.asInt()?.let { updated.copy(cost = it) } ?: updated
                    RewardFields.COST_INCREMENT -> updated.copy(costIncrement = value.asIntOrNull())
                    RewardFields.INCREMENT_EVERY -> updated.copy(incrementEvery = value.asIntOrNull())
                    else -> updated
                }
            }
            updated
        }

        is Event.RewardDeleted -> state.copy(rewards = state.rewards - event.id)

        is Event.RewardClaimed -> {
            val reward = state.rewards[event.id]
            if (reward == null) {
                state
            } else {
                state.copy(
                    rewards = state.rewards + (
                        event.id to reward.copy(
                            claimCount = reward.claimCount + 1,
                            lastClaimedAt = event.at,
                        )
                        ),
                    profile = state.profile.copy(
                        rewardPointBalance = state.profile.rewardPointBalance - event.costPaid,
                    ),
                    claims = state.claims + ClaimRecord(reward.id, reward.name, event.at, event.costPaid),
                )
            }
        }

        is Event.ItemCreated -> state.copy(
            items = state.items + (
                event.id to (
                    state.items[event.id]?.copy(
                        name = event.name,
                        icon = event.icon,
                        notes = event.notes,
                        consumable = event.consumable,
                    ) ?: InventoryItem(
                        id = event.id,
                        name = event.name,
                        icon = event.icon,
                        notes = event.notes,
                        quantity = event.quantity,
                        consumable = event.consumable,
                        createdAt = event.ts,
                    )
                    )
                ),
        )

        is Event.ItemChanged -> state.updateItem(event.id) { item ->
            var updated = item
            for ((key, value) in event.fields) {
                updated = when (key) {
                    ItemFields.NAME -> value.asString()?.let { updated.copy(name = it) } ?: updated
                    ItemFields.ICON -> value.asString()?.let { updated.copy(icon = it) } ?: updated
                    ItemFields.NOTES -> value.asString()?.let { updated.copy(notes = it) } ?: updated
                    ItemFields.CONSUMABLE -> value.asBoolean()?.let { updated.copy(consumable = it) } ?: updated
                    else -> updated
                }
            }
            updated
        }

        is Event.ItemDeleted -> state.copy(items = state.items - event.id)

        is Event.ItemStockChanged -> state.updateItem(event.id) {
            it.copy(quantity = (it.quantity + event.delta).coerceAtLeast(0))
        }

        is Event.ItemUsed -> state.updateItem(event.id) { item ->
            val spent = if (item.consumable) event.quantity else 0
            item.copy(
                quantity = (item.quantity - spent).coerceAtLeast(0),
                lastUsedAt = event.at,
            )
        }

        is Event.NoteCreated -> state.copy(
            notes = state.notes + (
                event.id to ScratchpadEntry(
                    id = event.id,
                    text = event.text,
                    questId = event.questId,
                    createdAt = event.ts,
                )
                ),
        )

        is Event.NoteChanged -> state.updateNote(event.id) { note ->
            var updated = note
            for ((key, value) in event.fields) {
                updated = when (key) {
                    NoteFields.TEXT -> value.asString()?.let { updated.copy(text = it) } ?: updated
                    NoteFields.DONE -> value.asBoolean()?.let { updated.copy(done = it) } ?: updated
                    NoteFields.QUEST_ID -> updated.copy(questId = value.asStringOrNull())
                    else -> updated
                }
            }
            updated
        }

        is Event.NoteDeleted -> state.copy(notes = state.notes - event.id)

        is Event.NotePromoted -> state.updateNote(event.id) {
            it.copy(promotedToQuestId = event.questId, done = true)
        }

        is Event.ProfileChanged -> {
            var profile = state.profile
            for ((key, value) in event.fields) {
                profile = when (key) {
                    ProfileFields.DISPLAY_NAME -> value.asString()?.let { profile.copy(displayName = it) } ?: profile
                    ProfileFields.AVATAR -> value.asString()?.let { profile.copy(avatar = it) } ?: profile
                    ProfileFields.ENERGY_MODE -> value.asEnum(EnergyMode.entries)?.let { profile.copy(energyMode = it) }
                        ?: profile

                    ProfileFields.WAKE_TIME -> value.asTime()?.let { profile.copy(wakeTime = it) } ?: profile
                    ProfileFields.SLEEP_TIME -> value.asTime()?.let { profile.copy(sleepTime = it) } ?: profile
                    ProfileFields.MANUAL_ENERGY -> profile.copy(manualEnergy = value.asIntOrNull())
                    else -> profile
                }
            }
            state.copy(profile = profile)
        }

        is Event.SettingsChanged -> {
            var settings = state.settings
            for ((key, value) in event.fields) {
                settings = when (key) {
                    SettingsFields.LANGUAGE -> value.asEnum(Language.entries)?.let { settings.copy(language = it) }
                        ?: settings

                    SettingsFields.SOUNDS_ENABLED -> value.asBoolean()?.let { settings.copy(soundsEnabled = it) }
                        ?: settings

                    SettingsFields.FAILURE_COSTS_POINTS ->
                        value.asBoolean()?.let { settings.copy(failureCostsPoints = it) } ?: settings

                    SettingsFields.DAILY_DIGEST_ENABLED ->
                        value.asBoolean()?.let { settings.copy(dailyDigestEnabled = it) } ?: settings

                    SettingsFields.DAILY_DIGEST_TIME -> value.asTime()?.let { settings.copy(dailyDigestTime = it) }
                        ?: settings

                    SettingsFields.OVERDUE_SUMMARY_ON_LAUNCH ->
                        value.asBoolean()?.let { settings.copy(overdueSummaryOnLaunch = it) } ?: settings

                    SettingsFields.ICS_EXPORT_ENABLED ->
                        value.asBoolean()?.let { settings.copy(icsExportEnabled = it) } ?: settings

                    SettingsFields.MARKDOWN_EXPORT_ENABLED ->
                        value.asBoolean()?.let { settings.copy(markdownExportEnabled = it) } ?: settings

                    SettingsFields.REMINDERS_ENABLED ->
                        value.asBoolean()?.let { settings.copy(remindersEnabled = it) } ?: settings

                    SettingsFields.TRAY_ENABLED -> value.asBoolean()?.let { settings.copy(trayEnabled = it) }
                        ?: settings

                    SettingsFields.ONBOARDING_COMPLETE ->
                        value.asBoolean()?.let { settings.copy(onboardingComplete = it) } ?: settings

                    SettingsFields.START_IN_TRAY -> value.asBoolean()?.let { settings.copy(startInTray = it) }
                        ?: settings

                    // Unlike the switches above, an unreadable value is not
                    // skipped: a view this version cannot open — a newer
                    // release's choice, or a hand edit — means Standing, not
                    // whichever older choice the log happened to hold before it.
                    SettingsFields.LAUNCH_VIEW -> settings.copy(
                        launchView = value.asEnum(LaunchView.entries) ?: LaunchView.STANDING,
                    )

                    else -> settings
                }
            }
            state.copy(settings = settings)
        }

        is Event.PointsAdjusted -> state.copy(
            profile = state.profile.copy(
                rewardPointBalance = state.profile.rewardPointBalance + event.delta,
            ),
        )
    }

    private fun applyQuestField(quest: Quest, key: String, value: JsonElement): Quest = when (key) {
        QuestFields.TITLE -> value.asString()?.let { quest.copy(title = it) } ?: quest
        QuestFields.NOTES -> value.asString()?.let { quest.copy(notes = it) } ?: quest
        QuestFields.DIFFICULTY -> value.asEnum(Difficulty.entries)?.let { quest.copy(difficulty = it) } ?: quest
        QuestFields.FEAR -> value.asEnum(Fear.entries)?.let { quest.copy(fear = it) } ?: quest
        QuestFields.URGENCY -> value.asEnum(Urgency.entries)?.let { quest.copy(urgency = it) } ?: quest
        QuestFields.ESTIMATED_MINUTES -> quest.copy(estimatedMinutes = value.asIntOrNull())
        QuestFields.DUE_DATE -> quest.copy(dueDate = value.asDate())
        QuestFields.DUE_TIME -> quest.copy(dueTime = value.asTime())
        QuestFields.REPETITION -> quest.copy(repetition = value.decodeOrNull(Repetition.serializer()))
        QuestFields.REMINDERS -> quest.copy(
            reminders = value.decodeOrNull(ListSerializer(Reminder.serializer())) ?: quest.reminders,
        )

        QuestFields.PARENT_ID -> {
            // Refusing a self-parent here rather than at the call site means a
            // hand-edited log cannot produce a cycle that hangs the tree view.
            val parent = value.asStringOrNull()
            if (parent == quest.id) quest else quest.copy(parentId = parent)
        }

        // An int pins (see Pin.of for how legacy values read); an explicit null
        // clears the pin and returns the quest to the automatic order.
        QuestFields.MANUAL_ORDER -> quest.copy(manualOrder = value.asIntOrNull())
        QuestFields.SKILL_IDS -> quest.copy(
            skillIds = value.decodeOrNull(SetSerializer(String.serializer())) ?: quest.skillIds,
        )

        QuestFields.REWARD_BONUS -> quest.copy(rewardBonus = value.asIntOrNull())

        else -> quest
    }

    /**
     * Adds XP to skills and reports any levels crossed.
     *
     * Negative grants (an undone completion) deliberately do not report a
     * "level up"; nor do they report a level *down*, because a snackbar that
     * announced a demotion a second after announcing a promotion would be
     * noise, not information.
     */
    private fun grantXp(
        skills: Map<String, Skill>,
        grants: Map<String, Int>,
        at: java.time.Instant,
        allowLevelUps: Boolean = true,
    ): Pair<Map<String, Skill>, List<LevelUp>> {
        if (grants.isEmpty()) return skills to emptyList()
        val updated = skills.toMutableMap()
        val levelUps = mutableListOf<LevelUp>()
        for ((skillId, xp) in grants) {
            val skill = updated[skillId] ?: continue
            val before = skill.level
            val next = skill.copy(totalXp = (skill.totalXp + xp).coerceAtLeast(0))
            updated[skillId] = next
            if (allowLevelUps && next.level > before) {
                levelUps += LevelUp(skillId, next.name, before, next.level, at)
            }
        }
        return updated to levelUps
    }

    // --- small decoding helpers ------------------------------------------

    private fun JsonElement.asString(): String? =
        if (this is JsonNull) null else runCatching { jsonPrimitive.content }.getOrNull()

    private fun JsonElement.asStringOrNull(): String? = if (this is JsonNull) null else asString()

    private fun JsonElement.asInt(): Int? =
        if (this is JsonNull) null else runCatching { jsonPrimitive.intOrNull }.getOrNull()

    private fun JsonElement.asIntOrNull(): Int? = if (this is JsonNull) null else asInt()

    private fun JsonElement.asBoolean(): Boolean? =
        if (this is JsonNull) null else runCatching { jsonPrimitive.booleanOrNull }.getOrNull()

    private fun JsonElement.asDate(): LocalDate? =
        asString()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JsonElement.asTime(): LocalTime? =
        asString()?.let { runCatching { LocalTime.parse(it, Iso.TIME) }.getOrNull() }

    private fun <T : Enum<T>> JsonElement.asEnum(entries: List<T>): T? {
        val name = asString() ?: return null
        return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun <T> JsonElement.decodeOrNull(serializer: kotlinx.serialization.KSerializer<T>): T? =
        if (this is JsonNull) null else runCatching { EventCodec.json.decodeFromJsonElement(serializer, this) }.getOrNull()

    // --- state helpers ----------------------------------------------------

    /**
     * Edits a quest wherever it currently lives.
     *
     * An edit can legitimately arrive after a delete — another device had the
     * quest open while this one deleted it. Applying it to the tombstoned copy
     * costs nothing and means the edit is still there if the delete is undone.
     */
    private inline fun QuestsState.updateQuest(id: String, transform: (Quest) -> Quest): QuestsState {
        quests[id]?.let { return copy(quests = quests + (id to transform(it))) }
        deletedQuests[id]?.let { return copy(deletedQuests = deletedQuests + (id to transform(it))) }
        return this
    }

    private inline fun QuestsState.updateSkill(id: String, transform: (Skill) -> Skill): QuestsState {
        val existing = skills[id] ?: return this
        return copy(skills = skills + (id to transform(existing)))
    }

    private inline fun QuestsState.updateReward(id: String, transform: (Reward) -> Reward): QuestsState {
        val existing = rewards[id] ?: return this
        return copy(rewards = rewards + (id to transform(existing)))
    }

    private inline fun QuestsState.updateItem(id: String, transform: (InventoryItem) -> InventoryItem): QuestsState {
        val existing = items[id] ?: return this
        return copy(items = items + (id to transform(existing)))
    }

    private inline fun QuestsState.updateNote(
        id: String,
        transform: (ScratchpadEntry) -> ScratchpadEntry,
    ): QuestsState {
        val existing = notes[id] ?: return this
        return copy(notes = notes + (id to transform(existing)))
    }

    private fun QuestsState.promoteOrphans(parentId: String, at: java.time.Instant): QuestsState = copy(
        quests = quests.mapValues { (_, quest) ->
            if (quest.parentId == parentId) quest.copy(parentId = null, updatedAt = at) else quest
        },
    )
}
