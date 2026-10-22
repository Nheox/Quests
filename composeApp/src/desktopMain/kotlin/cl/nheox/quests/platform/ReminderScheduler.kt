package cl.nheox.quests.platform

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.projection.QuestsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

public data class ReminderAlert(
    val title: String,
    val body: String,
)

/**
 * Fires per-quest reminders while the app is running.
 *
 * There is no daily digest and no launch summary any more: the Standing
 * screen (§7.5) answers at launch what those two notifications used to
 * announce, and a notification at a set hour is gone before it is read.
 *
 * In-process, per §8.1 — a coroutine that wakes up every minute and asks what
 * is due. No `cron`, no `systemd` timer, no launch agent, because none of those
 * can be installed from inside a Flatpak sandbox and all of them would need
 * uninstalling again.
 *
 * It is worth being honest about what this can and cannot do. It only fires
 * while the machine is awake and Quests is running, which is exactly the
 * limitation §7.5 identifies: if the laptop is shut and the phone is across the
 * room, nothing here reaches anybody. That is why the `.ics` feed exists and
 * why it, not this, is the reminder story. This is the convenience for when the
 * user is already at the machine.
 */
public class ReminderScheduler(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val state: () -> QuestsState,
    private val onAlert: (ReminderAlert) -> Unit,
) {
    private var job: Job? = null

    /** Keyed per quest and per reminder, so each fires exactly once per run. */
    private val fired = HashSet<String>()

    public fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            // On launch, everything already in the past is marked as fired.
            // Otherwise starting the app in the evening would unleash a burst of
            // notifications for reminders whose moment passed hours ago.
            suppressPast()
            while (isActive) {
                tick()
                delay(TICK_MILLIS)
            }
        }
    }

    public fun stop() {
        job?.cancel()
        job = null
    }

    internal fun tick() {
        val current = state()
        val now = clock.now()

        if (current.settings.remindersEnabled) {
            for (quest in current.openQuests) {
                for ((index, reminder) in quest.reminders.withIndex()) {
                    val due = dueAt(quest, reminder) ?: continue
                    val key = "${quest.id}#$index@${due.epochSecond}"
                    if (key in fired) continue
                    if (!due.isAfter(now) && ChronoUnit.MINUTES.between(due, now) <= GRACE_MINUTES) {
                        fired += key
                        onAlert(ReminderAlert(quest.title, reminderBody(quest)))
                    } else if (!due.isAfter(now)) {
                        // Too old to be worth announcing, but still recorded so it
                        // is not announced later either.
                        fired += key
                    }
                }
            }
        }
    }

    private fun suppressPast() {
        val now = clock.now()
        for (quest in state().openQuests) {
            for ((index, reminder) in quest.reminders.withIndex()) {
                val due = dueAt(quest, reminder) ?: continue
                if (!due.isAfter(now)) fired += "${quest.id}#$index@${due.epochSecond}"
            }
        }
    }

    private fun dueAt(quest: Quest, reminder: Reminder): Instant? = when (reminder) {
        is Reminder.BeforeDue -> {
            val date = quest.dueDate
            if (date == null) {
                null
            } else {
                Dates.dueInstant(date, quest.dueTime, clock.zone())
                    .minusSeconds(reminder.minutesBefore * 60L)
            }
        }

        is Reminder.At -> reminder.date.atTime(reminder.time).atZone(clock.zone()).toInstant()
    }

    private fun reminderBody(quest: Quest): String = buildString {
        quest.dueTime?.let { append(it.toString()).append(" · ") }
        append(quest.difficulty.name.lowercase().replace('_', ' '))
        quest.estimatedMinutes?.let { append(" · ").append(Dates.formatMinutes(it)) }
    }

    public companion object {
        private const val TICK_MILLIS = 60_000L

        /**
         * How late a reminder may be and still fire.
         *
         * A laptop that was asleep at 15:00 should not announce a 15:00
         * reminder at 09:00 the next morning as though it were news.
         */
        private const val GRACE_MINUTES = 30L
    }
}
