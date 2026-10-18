package cl.nheox.quests.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * `java.time` throughout — never Joda, and never a bare epoch integer in a
 * place a human will read.
 *
 * Due dates are stored as a [LocalDate] plus an optional [LocalTime] rather
 * than as an instant. A quest due "Tuesday" is due Tuesday wherever the laptop
 * happens to be, and two devices in different time zones must agree on what
 * "overdue" means without consulting each other.
 */
public object Iso {
    public val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    public val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    public val INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
}

public object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("cl.nheox.quests.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate): Unit =
        encoder.encodeString(value.format(Iso.DATE))

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

public object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("cl.nheox.quests.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime): Unit =
        encoder.encodeString(value.format(Iso.TIME))

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

public object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("cl.nheox.quests.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant): Unit =
        encoder.encodeString(value.truncatedTo(ChronoUnit.MILLIS).toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

public typealias IsoDate = @Serializable(LocalDateSerializer::class) LocalDate
public typealias IsoTime = @Serializable(LocalTimeSerializer::class) LocalTime
public typealias IsoInstant = @Serializable(InstantSerializer::class) Instant

/**
 * The app's single source of "now".
 *
 * An interface rather than a direct call to [Instant.now] so that replay
 * determinism tests, repetition tests and the suggester can be driven from a
 * fixed clock. Manual constructor injection, per docs/ARCHITECTURE.md — the
 * project is far too small to earn a DI container.
 */
public interface Clock {
    public fun now(): Instant

    public fun zone(): ZoneId

    public fun today(): LocalDate = now().atZone(zone()).toLocalDate()

    public fun localNow(): LocalDateTime = now().atZone(zone()).toLocalDateTime()

    public fun nowMillis(): Long = now().toEpochMilli()

    public companion object {
        public val System: Clock = object : Clock {
            override fun now(): Instant = Instant.now()

            override fun zone(): ZoneId = ZoneId.systemDefault()
        }

        /** A clock frozen at [at], for tests and for deterministic exports. */
        public fun fixed(at: Instant, zone: ZoneId = ZoneId.of("UTC")): Clock = object : Clock {
            override fun now(): Instant = at

            override fun zone(): ZoneId = zone
        }
    }
}

public object Dates {
    /** The instant a due date falls at, resolving a date-only due to end of day. */
    public fun dueInstant(date: LocalDate, time: LocalTime?, zone: ZoneId): Instant =
        date.atTime(time ?: LocalTime.of(23, 59)).atZone(zone).toInstant()

    public fun isOverdue(date: LocalDate?, time: LocalTime?, clock: Clock): Boolean {
        if (date == null) return false
        val now = clock.localNow()
        return if (time == null) date.isBefore(now.toLocalDate()) else date.atTime(time).isBefore(now)
    }

    public fun dayOfWeekName(day: DayOfWeek, locale: Locale): String =
        day.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** Monday-based week start, which is what both Chile and ISO use. */
    public fun startOfWeek(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    public fun formatMinutes(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
