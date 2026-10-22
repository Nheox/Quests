package cl.nheox.quests.events

import cl.nheox.quests.domain.Difficulty
import cl.nheox.quests.domain.EnergyMode
import cl.nheox.quests.domain.Fear
import cl.nheox.quests.domain.Language
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Repetition
import cl.nheox.quests.domain.Urgency
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime

/**
 * Builds the `fields` map that a `*Changed` event carries.
 *
 * The map is dynamic on the wire, because last-write-wins has to work per
 * field, but it must not be dynamic in the code that produces it: a typo in a
 * field name would silently do nothing. So every writer goes through a builder
 * whose setters are named, typed and enumerated here, and every reader goes
 * through an exhaustive `when` on the same names (see Projector).
 */
public class FieldPatch {
    private val fields = linkedMapOf<String, JsonElement>()

    public val isEmpty: Boolean get() = fields.isEmpty()

    public fun build(): Map<String, JsonElement> = fields.toMap()

    public fun set(key: String, value: String): FieldPatch = put(key, JsonPrimitive(value))

    public fun set(key: String, value: Int): FieldPatch = put(key, JsonPrimitive(value))

    public fun set(key: String, value: Boolean): FieldPatch = put(key, JsonPrimitive(value))

    public fun setEnum(key: String, value: Enum<*>): FieldPatch = put(key, JsonPrimitive(serialNameOf(value)))

    public fun setDate(key: String, value: LocalDate?): FieldPatch =
        put(key, value?.let { JsonPrimitive(it.toString()) } ?: JsonNull)

    public fun setTime(key: String, value: LocalTime?): FieldPatch =
        put(key, value?.let { JsonPrimitive(it.format(cl.nheox.quests.domain.Iso.TIME)) } ?: JsonNull)

    public fun setIntOrNull(key: String, value: Int?): FieldPatch =
        put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)

    public fun setStringOrNull(key: String, value: String?): FieldPatch =
        put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)

    public fun setRepetition(key: String, value: Repetition?): FieldPatch = put(
        key,
        value?.let { EventCodec.json.encodeToJsonElement(Repetition.serializer(), it) } ?: JsonNull,
    )

    public fun setReminders(key: String, value: List<Reminder>): FieldPatch =
        put(key, EventCodec.json.encodeToJsonElement(ListSerializer(Reminder.serializer()), value))

    public fun setStrings(key: String, value: Set<String>): FieldPatch =
        put(key, EventCodec.json.encodeToJsonElement(SetSerializer(String.serializer()), value))

    public fun clear(key: String): FieldPatch = put(key, JsonNull)

    private fun put(key: String, value: JsonElement): FieldPatch {
        fields[key] = value
        return this
    }

    public companion object {
        /** Honours `@SerialName`, so `RepeatUnit.DAY` writes as `"day"`. */
        private fun serialNameOf(value: Enum<*>): String = when (value) {
            is Difficulty, is Fear, is Urgency, is EnergyMode, is Language -> value.name
            else -> value.name
        }
    }
}

/** The field names a quest understands. Shared by the writer and the reader. */
public object QuestFields {
    public const val TITLE: String = "title"
    public const val NOTES: String = "notes"
    public const val DIFFICULTY: String = "difficulty"
    public const val FEAR: String = "fear"
    public const val URGENCY: String = "urgency"
    public const val ESTIMATED_MINUTES: String = "estimatedMinutes"
    public const val DUE_DATE: String = "dueDate"
    public const val DUE_TIME: String = "dueTime"
    public const val REPETITION: String = "repetition"
    public const val REMINDERS: String = "reminders"
    public const val PARENT_ID: String = "parentId"
    public const val MANUAL_ORDER: String = "manualOrder"
    public const val SKILL_IDS: String = "skillIds"
    public const val REWARD_BONUS: String = "rewardBonus"
}

public object SkillFields {
    public const val NAME: String = "name"
    public const val CATEGORY: String = "category"
    public const val ICON: String = "icon"
    public const val NOTES: String = "notes"
}

public object RewardFields {
    public const val NAME: String = "name"
    public const val ICON: String = "icon"
    public const val NOTES: String = "notes"
    public const val COST: String = "cost"
    public const val COST_INCREMENT: String = "costIncrement"
    public const val INCREMENT_EVERY: String = "incrementEvery"
}

public object ItemFields {
    public const val NAME: String = "name"
    public const val ICON: String = "icon"
    public const val NOTES: String = "notes"
    public const val CONSUMABLE: String = "consumable"
}

public object NoteFields {
    public const val TEXT: String = "text"
    public const val DONE: String = "done"
    public const val QUEST_ID: String = "questId"
}

public object ProfileFields {
    public const val DISPLAY_NAME: String = "displayName"
    public const val AVATAR: String = "avatar"
    public const val ENERGY_MODE: String = "energyMode"
    public const val WAKE_TIME: String = "wakeTime"
    public const val SLEEP_TIME: String = "sleepTime"
    public const val MANUAL_ENERGY: String = "manualEnergy"
}

public object SettingsFields {
    public const val LANGUAGE: String = "language"
    public const val SOUNDS_ENABLED: String = "soundsEnabled"
    public const val FAILURE_COSTS_POINTS: String = "failureCostsPoints"
    public const val DAILY_DIGEST_ENABLED: String = "dailyDigestEnabled"
    public const val DAILY_DIGEST_TIME: String = "dailyDigestTime"
    public const val OVERDUE_SUMMARY_ON_LAUNCH: String = "overdueSummaryOnLaunch"
    public const val ICS_EXPORT_ENABLED: String = "icsExportEnabled"
    public const val MARKDOWN_EXPORT_ENABLED: String = "markdownExportEnabled"
    public const val REMINDERS_ENABLED: String = "remindersEnabled"
    public const val TRAY_ENABLED: String = "trayEnabled"
    public const val ONBOARDING_COMPLETE: String = "onboardingComplete"
    public const val START_IN_TRAY: String = "startInTray"
}
