package cl.nheox.quests.platform

import cl.nheox.quests.domain.Clock
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.Quest
import cl.nheox.quests.domain.Reminder
import cl.nheox.quests.domain.Settings
import cl.nheox.quests.projection.QuestsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

public data class ReminderAlert(
    val title: String,
    val body: String,
)

/**
 * Fires reminders and the daily digest while the app is running.
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
    private var lastDigest: LocalDate? = null

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

    /** The overdue summary shown once at launch (§7.5). */
    public fun launchSummary(settings: Settings): ReminderAlert? {
        if (!settings.overdueSummaryOnLaunch) return null
        val current = state()
        val overdue = current.openQuests.count { Dates.isOverdue(it.dueDate, it.dueTime, clock) }
        if (overdue == 0) return null
        return ReminderAlert(
            title = "Quests",
            body = overdueBody(overdue),
        )
    }

    internal fun tick() {
        val current = state()
        val settings = current.settings
        val now = clock.now()

        if (settings.remindersEnabled) {
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

        if (settings.dailyDigestEnabled) {
            val today = clock.today()
            val digestMoment = today.atTime(settings.dailyDigestTime).atZone(clock.zone()).toInstant()
            if (lastDigest != today && !digestMoment.isAfter(now)) {
                lastDigest = today
                val open = current.openQuests.size
                val overdue = current.openQuests.count { Dates.isOverdue(it.dueDate, it.dueTime, clock) }
                if (open > 0) {
                    onAlert(ReminderAlert("Quests", "$open open, $overdue overdue"))
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
        lastDigest = clock.today()
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

    private fun overdueBody(count: Int): String =
        if (count == 1) "1 quest is overdue" else "$count quests are overdue"

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
