package cl.nheox.quests.domain

import java.time.Instant
import java.time.ZoneId

/**
 * When reminders fire.
 *
 * One definition for the notification scheduler and for the Reminders list,
 * so the list can never promise a reminder the scheduler will not send, or
 * leave out one it will.
 */
public object Reminders {
    /**
     * The moment [reminder] fires: its offset before the quest's due moment,
     * or its own date and time. Null for an offset on a quest that no longer
     * has a due date — it has nothing to be before.
     */
    public fun moment(quest: Quest, reminder: Reminder, zone: ZoneId): Instant? = when (reminder) {
        is Reminder.BeforeDue -> quest.dueDate?.let { date ->
            Dates.dueInstant(date, quest.dueTime, zone).minusSeconds(reminder.minutesBefore * 60L)
        }

        is Reminder.At -> reminder.date.atTime(reminder.time).atZone(zone).toInstant()
    }

    /**
     * The earliest of an open quest's reminders still to come at [now], or
     * null when it has none — it is finished, or every reminder has passed.
     * A reminder due this very instant still counts as to come.
     */
    public fun next(quest: Quest, now: Instant, zone: ZoneId): Instant? {
        if (!quest.isOpen) return null
        return quest.reminders
            .mapNotNull { moment(quest, it, zone) }
            .filter { !it.isBefore(now) }
            .minOrNull()
    }
}
