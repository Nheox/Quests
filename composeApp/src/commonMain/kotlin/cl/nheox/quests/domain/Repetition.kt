package cl.nheox.quests.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
public enum class RepeatUnit {
    @SerialName("hour")
    HOUR,

    @SerialName("day")
    DAY,

    @SerialName("week")
    WEEK,

    @SerialName("month")
    MONTH,

    @SerialName("year")
    YEAR,
}

/**
 * "Every N units", which covers the whole of the original app's repetition
 * vocabulary (hourly, daily, weekly, monthly, yearly, and every-N of each)
 * with one shape instead of five.
 */
@Serializable
public data class Repetition(
    val unit: RepeatUnit,
    val every: Int = 1,
) {
    init {
        require(every >= 1) { "repetition interval must be at least 1" }
    }

    /**
     * The next occurrence after a due date/time.
     *
     * Hourly repetition on a date-only quest has nowhere to put the hour, so it
     * advances by a day instead of silently doing nothing.
     */
    public fun next(date: LocalDate, time: LocalTime?): Pair<LocalDate, LocalTime?> = when (unit) {
        RepeatUnit.HOUR ->
            if (time == null) {
                date.plusDays(every.toLong()) to null
            } else {
                val total = time.hour * 60 + time.minute + every * 60
                date.plusDays((total / (24 * 60)).toLong()) to LocalTime.of((total / 60) % 24, total % 60)
            }

        RepeatUnit.DAY -> date.plusDays(every.toLong()) to time
        RepeatUnit.WEEK -> date.plusWeeks(every.toLong()) to time
        RepeatUnit.MONTH -> date.plusMonths(every.toLong()) to time
        RepeatUnit.YEAR -> date.plusYears(every.toLong()) to time
    }

    public companion object {
        public val DAILY: Repetition = Repetition(RepeatUnit.DAY)
        public val WEEKLY: Repetition = Repetition(RepeatUnit.WEEK)
        public val MONTHLY: Repetition = Repetition(RepeatUnit.MONTH)
        public val YEARLY: Repetition = Repetition(RepeatUnit.YEAR)
        public val HOURLY: Repetition = Repetition(RepeatUnit.HOUR)
    }
}

/**
 * A reminder, either relative to the due time or at a wall-clock moment.
 *
 * Desktop notifications are unreliable by nature (§7.5) — these feed the
 * notifications and the Reminders list, while the generated `.ics` feed is
 * what actually reaches a phone. When one fires is [Reminders.moment].
 */
@Serializable
public sealed class Reminder {
    @Serializable
    @SerialName("before")
    public data class BeforeDue(val minutesBefore: Int) : Reminder()

    @Serializable
    @SerialName("at")
    public data class At(
        val date: IsoDate,
        val time: IsoTime,
    ) : Reminder()
}
