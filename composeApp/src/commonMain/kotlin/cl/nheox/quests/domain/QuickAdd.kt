package cl.nheox.quests.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The result of parsing one line of quick-add input.
 *
 * Everything is optional except the title: an unparseable line still becomes a
 * quest, because losing a thought to a syntax error would defeat the point of
 * quick capture entirely.
 */
public data class QuickAddResult(
    val title: String,
    val difficulty: Difficulty? = null,
    val fear: Fear? = null,
    val urgency: Urgency? = null,
    val estimatedMinutes: Int? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val skillNames: List<String> = emptyList(),
    val repetition: Repetition? = null,
) {
    public val isEmpty: Boolean get() = title.isBlank()
}

/**
 * Parses `write methods tomorrow 3pm #thesis !! ~2h` into structured fields.
 *
 * Implemented as an ordered series of extraction passes over the raw string
 * rather than a token walk, because several of the forms this needs to accept
 * are multi-word ("next week", "every 2 weeks", "a las 3pm"). Each pass removes
 * what it matched; whatever survives is the title.
 *
 * Keywords are accepted in Spanish as well as English. The interface is English
 * only (§1), but input is not interface: a line typed as "llamar mañana" should
 * still land on tomorrow, and recognising more words costs nothing.
 */
public object QuickAdd {
    private val DIFFICULTY_WORDS: Map<String, Difficulty> = mapOf(
        "trivial" to Difficulty.TRIVIAL,
        "verylow" to Difficulty.VERY_LOW,
        "very-low" to Difficulty.VERY_LOW,
        "muybaja" to Difficulty.VERY_LOW,
        "low" to Difficulty.LOW,
        "baja" to Difficulty.LOW,
        "moderate" to Difficulty.MODERATE,
        "moderada" to Difficulty.MODERATE,
        "high" to Difficulty.HIGH,
        "alta" to Difficulty.HIGH,
        "veryhigh" to Difficulty.VERY_HIGH,
        "very-high" to Difficulty.VERY_HIGH,
        "muyalta" to Difficulty.VERY_HIGH,
        "extreme" to Difficulty.EXTREME,
        "extrema" to Difficulty.EXTREME,
    )

    private val FEAR_WORDS: Map<String, Fear> = mapOf(
        "none" to Fear.NONE,
        "ninguno" to Fear.NONE,
        "mild" to Fear.MILD,
        "leve" to Fear.MILD,
        "moderate" to Fear.MODERATE,
        "moderado" to Fear.MODERATE,
        "strong" to Fear.STRONG,
        "fuerte" to Fear.STRONG,
        "severe" to Fear.SEVERE,
        "severo" to Fear.SEVERE,
    )

    private val URGENCY_WORDS: Map<String, Urgency> = mapOf(
        "whenever" to Urgency.WHENEVER,
        "cuandosea" to Urgency.WHENEVER,
        "low" to Urgency.LOW,
        "baja" to Urgency.LOW,
        "normal" to Urgency.NORMAL,
        "high" to Urgency.HIGH,
        "alta" to Urgency.HIGH,
        "critical" to Urgency.CRITICAL,
        "critica" to Urgency.CRITICAL,
        "crítica" to Urgency.CRITICAL,
    )

    private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "lunes" to DayOfWeek.MONDAY, "lun" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "martes" to DayOfWeek.TUESDAY, "mar" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY, "miércoles" to DayOfWeek.WEDNESDAY, "mie" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "jueves" to DayOfWeek.THURSDAY, "jue" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "viernes" to DayOfWeek.FRIDAY, "vie" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sabado" to DayOfWeek.SATURDAY, "sábado" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
        "domingo" to DayOfWeek.SUNDAY, "dom" to DayOfWeek.SUNDAY,
    )

    private val REPEAT_UNITS: Map<String, RepeatUnit> = mapOf(
        "hour" to RepeatUnit.HOUR, "hours" to RepeatUnit.HOUR,
        "hora" to RepeatUnit.HOUR, "horas" to RepeatUnit.HOUR,
        "day" to RepeatUnit.DAY, "days" to RepeatUnit.DAY,
        "dia" to RepeatUnit.DAY, "día" to RepeatUnit.DAY, "dias" to RepeatUnit.DAY, "días" to RepeatUnit.DAY,
        "week" to RepeatUnit.WEEK, "weeks" to RepeatUnit.WEEK,
        "semana" to RepeatUnit.WEEK, "semanas" to RepeatUnit.WEEK,
        "month" to RepeatUnit.MONTH, "months" to RepeatUnit.MONTH,
        "mes" to RepeatUnit.MONTH, "meses" to RepeatUnit.MONTH,
        "year" to RepeatUnit.YEAR, "years" to RepeatUnit.YEAR,
        "año" to RepeatUnit.YEAR, "ano" to RepeatUnit.YEAR, "años" to RepeatUnit.YEAR,
    )

    private val PERIOD_WORDS: Map<String, Repetition> = mapOf(
        "hourly" to Repetition.HOURLY,
        "daily" to Repetition.DAILY,
        "diario" to Repetition.DAILY,
        "diaria" to Repetition.DAILY,
        "weekly" to Repetition.WEEKLY,
        "semanal" to Repetition.WEEKLY,
        "monthly" to Repetition.MONTHLY,
        "mensual" to Repetition.MONTHLY,
        "yearly" to Repetition.YEARLY,
        "annually" to Repetition.YEARLY,
        "anual" to Repetition.YEARLY,
    )

    private val quotedSkill = Regex("""#"([^"]+)"""")
    private val bareSkill = Regex("""(?<!\S)#([\p{L}\p{N}_\-]+)""")
    private val keyedDifficulty = Regex("""(?<!\S)d:([\p{L}\-]+)""", RegexOption.IGNORE_CASE)
    private val keyedFear = Regex("""(?<!\S)f:([\p{L}\-]+)""", RegexOption.IGNORE_CASE)
    private val keyedUrgency = Regex("""(?<!\S)u:([\p{L}\-]+)""", RegexOption.IGNORE_CASE)
    private val everyN = Regex(
        """(?<!\S)(?:every|each|cada)\s+(\d+)\s+([\p{L}]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val everyOne = Regex(
        """(?<!\S)(?:every|each|cada)\s+([\p{L}]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val periodWord = Regex(
        """(?<!\S)(${PERIOD_WORDS.keys.joinToString("|")})(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )
    private val duration = Regex(
        """(?<!\S)~\s*(?:(\d+)\s*h(?:ours?|oras?)?)?\s*(?:(\d+)\s*m(?:in(?:utes?|utos?)?)?)?(?:(\d+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val isoDate = Regex("""(?<!\S)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
    private val slashDate = Regex("""(?<!\S)(\d{1,2})[/.](\d{1,2})(?:[/.](\d{2,4}))?(?!\d)""")
    private val relativeDate = Regex("""(?<!\S)\+(\d+)\s*([dwmy])(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val nextWeek = Regex("""(?<!\S)(?:next\s+week|pr[oó]xima\s+semana|semana\s+que\s+viene)""", RegexOption.IGNORE_CASE)
    private val nextMonth = Regex("""(?<!\S)(?:next\s+month|pr[oó]ximo\s+mes|mes\s+que\s+viene)""", RegexOption.IGNORE_CASE)
    private val todayWord = Regex("""(?<!\S)(?:today|hoy)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val tomorrowWord = Regex("""(?<!\S)(?:tomorrow|ma[nñ]ana)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val weekdayWord = Regex(
        """(?<!\S)(?:next\s+|pr[oó]ximo\s+|pr[oó]xima\s+|el\s+)?(${WEEKDAYS.keys.joinToString("|")})(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )
    private val meridiemTime = Regex(
        """(?<!\S)(?:at\s+|a\s+las\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )
    private val clockTime = Regex("""(?<!\S)(?:at\s+|a\s+las\s+)?(\d{1,2}):(\d{2})(?!\d)""", RegexOption.IGNORE_CASE)
    private val stars = Regex("""(?<!\S)(\*{1,7})(?!\S)""")
    private val carets = Regex("""(?<!\S)(\^{1,5})(?!\S)""")
    private val bangs = Regex("""(?<!\S)(!{1,4})(?!\S)""")

    public fun parse(input: String, today: LocalDate): QuickAddResult {
        var rest = input
        val skills = mutableListOf<String>()
        var difficulty: Difficulty? = null
        var fear: Fear? = null
        var urgency: Urgency? = null
        var minutes: Int? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        var repetition: Repetition? = null

        fun take(regex: Regex, onMatch: (MatchResult) -> Boolean) {
            val match = regex.find(rest) ?: return
            if (onMatch(match)) {
                rest = rest.removeRange(match.range)
            }
        }

        fun takeAll(regex: Regex, onMatch: (MatchResult) -> Boolean) {
            while (true) {
                val match = regex.find(rest) ?: return
                if (!onMatch(match)) return
                rest = rest.removeRange(match.range)
            }
        }

        takeAll(quotedSkill) { skills += it.groupValues[1].trim(); true }
        takeAll(bareSkill) { skills += it.groupValues[1].replace('-', ' ').trim(); true }

        take(keyedDifficulty) { match ->
            difficulty = DIFFICULTY_WORDS[normalise(match.groupValues[1])]
            difficulty != null
        }
        take(keyedFear) { match ->
            fear = FEAR_WORDS[normalise(match.groupValues[1])]
            fear != null
        }
        take(keyedUrgency) { match ->
            urgency = URGENCY_WORDS[normalise(match.groupValues[1])]
            urgency != null
        }

        take(everyN) { match ->
            val unit = REPEAT_UNITS[normalise(match.groupValues[2])]
            val count = match.groupValues[1].toIntOrNull()
            if (unit != null && count != null && count >= 1) {
                repetition = Repetition(unit, count)
                true
            } else {
                false
            }
        }
        if (repetition == null) {
            take(everyOne) { match ->
                val unit = REPEAT_UNITS[normalise(match.groupValues[1])]
                if (unit != null) {
                    repetition = Repetition(unit, 1)
                    true
                } else {
                    false
                }
            }
        }
        if (repetition == null) {
            take(periodWord) { match ->
                repetition = PERIOD_WORDS[normalise(match.groupValues[1])]
                repetition != null
            }
        }

        take(duration) { match ->
            val hours = match.groupValues[1].toIntOrNull()
            val mins = match.groupValues[2].toIntOrNull()
            val bare = match.groupValues[3].toIntOrNull()
            val total = (hours ?: 0) * 60 + (mins ?: 0) + (bare ?: 0)
            if (total > 0) {
                minutes = total
                true
            } else {
                false
            }
        }

        take(isoDate) { match ->
            date = runCatching {
                LocalDate.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }.getOrNull()
            date != null
        }
        if (date == null) {
            take(slashDate) { match ->
                // Day first: Chile writes 20/10, not 10/20.
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val yearText = match.groupValues[3]
                val year = when {
                    yearText.isEmpty() -> today.year
                    yearText.length <= 2 -> 2000 + yearText.toInt()
                    else -> yearText.toInt()
                }
                date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                    ?.let { if (yearText.isEmpty() && it.isBefore(today)) it.plusYears(1) else it }
                date != null
            }
        }
        if (date == null) {
            take(relativeDate) { match ->
                val count = match.groupValues[1].toLong()
                date = when (match.groupValues[2].lowercase()) {
                    "d" -> today.plusDays(count)
                    "w" -> today.plusWeeks(count)
                    "m" -> today.plusMonths(count)
                    "y" -> today.plusYears(count)
                    else -> null
                }
                date != null
            }
        }
        if (date == null) {
            take(nextWeek) { date = Dates.startOfWeek(today).plusWeeks(1); true }
        }
        if (date == null) {
            take(nextMonth) { date = today.plusMonths(1); true }
        }
        if (date == null) {
            take(todayWord) { date = today; true }
        }
        if (date == null) {
            take(tomorrowWord) { date = today.plusDays(1); true }
        }
        if (date == null) {
            take(weekdayWord) { match ->
                val day = WEEKDAYS[normalise(match.groupValues[1])]
                if (day == null) {
                    false
                } else {
                    val forced = match.value.trimStart().lowercase().let {
                        it.startsWith("next") || it.startsWith("pró") || it.startsWith("pro")
                    }
                    date = nextOccurrence(today, day, forceFuture = forced)
                    true
                }
            }
        }

        take(meridiemTime) { match ->
            val rawHour = match.groupValues[1].toIntOrNull() ?: return@take false
            if (rawHour !in 1..12) return@take false
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (minute !in 0..59) return@take false
            val pm = match.groupValues[3].lowercase() == "pm"
            val hour = when {
                pm && rawHour < 12 -> rawHour + 12
                !pm && rawHour == 12 -> 0
                else -> rawHour
            }
            time = LocalTime.of(hour, minute)
            true
        }
        if (time == null) {
            take(clockTime) { match ->
                val hour = match.groupValues[1].toIntOrNull() ?: return@take false
                val minute = match.groupValues[2].toIntOrNull() ?: return@take false
                if (hour !in 0..23 || minute !in 0..59) return@take false
                time = LocalTime.of(hour, minute)
                true
            }
        }

        if (difficulty == null) {
            take(stars) { match ->
                difficulty = Difficulty.fromStars(match.groupValues[1].length)
                difficulty != null
            }
        }
        if (fear == null) {
            take(carets) { match ->
                fear = Fear.fromCarets(match.groupValues[1].length)
                fear != null
            }
        }
        if (urgency == null) {
            take(bangs) { match ->
                urgency = if (match.groupValues[1].length == 1) Urgency.HIGH else Urgency.CRITICAL
                true
            }
        }

        // A time with no date means today, which is what anyone typing "3pm"
        // means. A date with no time stays date-only.
        if (time != null && date == null) date = today

        return QuickAddResult(
            title = rest.replace(Regex("""\s+"""), " ").trim(),
            difficulty = difficulty,
            fear = fear,
            urgency = urgency,
            estimatedMinutes = minutes,
            dueDate = date,
            dueTime = time,
            skillNames = skills.filter { it.isNotBlank() }.distinct(),
            repetition = repetition,
        )
    }

    private fun nextOccurrence(today: LocalDate, day: DayOfWeek, forceFuture: Boolean): LocalDate {
        val delta = ((day.value - today.dayOfWeek.value) + 7) % 7
        return when {
            delta == 0 && forceFuture -> today.plusDays(7)
            delta == 0 -> today
            else -> today.plusDays(delta.toLong())
        }
    }

    /**
     * Lowercases, strips accents and drops hyphens, so `crítica`, `critica`,
     * `very-high` and `veryhigh` all land on the same key.
     */
    private fun normalise(text: String): String = text.lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
        .replace("-", "")
}
