package cl.nheox.quests.domain

import kotlinx.serialization.Serializable

/**
 * How hard a quest is, independent of how frightening it is.
 *
 * Multipliers are ours, designed from first principles (see docs/BALANCE.md).
 * The curve is deliberately super-linear: an Extreme quest is worth ten Trivial
 * ones, so breaking work down never pays better than doing the hard thing.
 */
@Serializable
public enum class Difficulty(public val multiplier: Double) {
    TRIVIAL(0.5),
    VERY_LOW(0.75),
    LOW(1.0),
    MODERATE(1.5),
    HIGH(2.25),
    VERY_HIGH(3.5),
    EXTREME(5.0),
    ;

    public val index: Int get() = ordinal

    public companion object {
        public val DEFAULT: Difficulty = MODERATE

        /** 1-based index as typed in quick-add (`***` is 3 stars, i.e. [LOW]). */
        public fun fromStars(stars: Int): Difficulty? = entries.getOrNull(stars - 1)
    }
}

/**
 * How frightening a quest is. A first-class dimension, separate from
 * difficulty: a two-minute phone call can be terrifying, and that is where
 * procrastination actually lives.
 */
@Serializable
public enum class Fear(public val bonus: Double) {
    NONE(1.00),
    MILD(1.10),
    MODERATE(1.25),
    STRONG(1.45),
    SEVERE(1.70),
    ;

    public val index: Int get() = ordinal

    public companion object {
        public val DEFAULT: Fear = NONE

        /** 1-based carets in quick-add: `^` is [MILD], `^^^^` is [SEVERE]. */
        public fun fromCarets(carets: Int): Fear? = entries.getOrNull(carets)
    }
}

/**
 * How soon a quest needs doing.
 *
 * Deliberately absent from every reward formula: paying more for urgent work
 * would reward leaving things until they are urgent. Urgency drives sorting
 * and the suggester only.
 */
@Serializable
public enum class Urgency {
    WHENEVER,
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
    ;

    public val index: Int get() = ordinal

    public companion object {
        public val DEFAULT: Urgency = NORMAL
    }
}

/** Failure is an explicit outcome, not merely an absence of completion. */
@Serializable
public enum class QuestState {
    OPEN,
    DONE,
    FAILED,
}

/** How the energy bar is driven. Energy never changes XP (see docs/BALANCE.md). */
@Serializable
public enum class EnergyMode {
    MANUAL,
    SLEEP_SCHEDULE,
}

/** UI language. English and Spanish from day one, structured for more later. */
@Serializable
public enum class Language {
    EN,
    ES,
    ;

    public companion object {
        public fun fromTag(tag: String?): Language =
            if (tag?.lowercase()?.startsWith("es") == true) ES else EN
    }
}
