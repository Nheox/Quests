package cl.nheox.quests.domain

import java.time.LocalTime
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Energy, 0–100.
 *
 * It feeds the energy bar and the suggester, and nothing else. Keeping it out
 * of the reward maths is a deliberate commitment: a tired person who does the
 * work anyway has earned more, not less.
 */
public object Energy {
    public const val PEAK: Int = 100
    public const val END_OF_DAY: Int = 20
    public const val ASLEEP: Int = 5

    /** How sharply energy falls across the waking day. Below 1 is gentle. */
    private const val DECAY_SHAPE: Double = 1.25

    public fun current(profile: Profile, now: LocalTime): Int = when (profile.energyMode) {
        EnergyMode.MANUAL -> (profile.manualEnergy ?: 70).coerceIn(0, PEAK)
        EnergyMode.SLEEP_SCHEDULE -> onSchedule(profile.wakeTime, profile.sleepTime, now)
    }

    /**
     * Decays from [PEAK] at wake time to [END_OF_DAY] at sleep time on a gentle
     * curve, and sits near zero while asleep. Handles a waking window that
     * crosses midnight, because plenty of people have one.
     */
    public fun onSchedule(wake: LocalTime, sleep: LocalTime, now: LocalTime): Int {
        val wakeMin = wake.toSecondOfDay() / 60
        val sleepMin = sleep.toSecondOfDay() / 60
        val nowMin = now.toSecondOfDay() / 60
        val dayMinutes = 24 * 60

        val awakeLength = ((sleepMin - wakeMin) + dayMinutes) % dayMinutes
        if (awakeLength == 0) return END_OF_DAY

        val sinceWake = ((nowMin - wakeMin) + dayMinutes) % dayMinutes
        if (sinceWake > awakeLength) return ASLEEP

        val through = sinceWake.toDouble() / awakeLength.toDouble()
        val span = (PEAK - END_OF_DAY).toDouble()
        return (END_OF_DAY + span * (1.0 - through).pow(DECAY_SHAPE)).roundToInt().coerceIn(ASLEEP, PEAK)
    }

    /** A label for the bar. Numbers alone do not tell you what to do with them. */
    public fun band(energy: Int): EnergyBand = when {
        energy >= 75 -> EnergyBand.HIGH
        energy >= 45 -> EnergyBand.MEDIUM
        energy >= 20 -> EnergyBand.LOW
        else -> EnergyBand.SPENT
    }
}

public enum class EnergyBand {
    HIGH,
    MEDIUM,
    LOW,
    SPENT,
}
