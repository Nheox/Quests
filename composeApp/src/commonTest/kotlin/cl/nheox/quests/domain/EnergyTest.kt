package cl.nheox.quests.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.LocalTime

class EnergyTest {

    private val wake = LocalTime.of(7, 30)
    private val sleep = LocalTime.of(23, 30)

    @Test
    fun `energy peaks at wake time and bottoms out at sleep time`() {
        assertEquals(Energy.PEAK, Energy.onSchedule(wake, sleep, wake))
        assertEquals(Energy.END_OF_DAY, Energy.onSchedule(wake, sleep, sleep))
    }

    @Test
    fun `energy decreases monotonically across the waking day`() {
        var previous = Energy.PEAK + 1
        var minute = 7 * 60 + 30
        while (minute <= 23 * 60 + 30) {
            val now = LocalTime.of(minute / 60, minute % 60)
            val energy = Energy.onSchedule(wake, sleep, now)
            assertTrue(energy <= previous, "energy rose at $now ($energy after $previous)")
            previous = energy
            minute += 15
        }
    }

    @Test
    fun `energy is near zero while asleep`() {
        assertEquals(Energy.ASLEEP, Energy.onSchedule(wake, sleep, LocalTime.of(3, 0)))
        assertEquals(Energy.ASLEEP, Energy.onSchedule(wake, sleep, LocalTime.of(2, 0)))
        assertEquals(Energy.ASLEEP, Energy.onSchedule(wake, sleep, LocalTime.of(6, 0)))
    }

    @Test
    fun `a waking window that crosses midnight works`() {
        // Plenty of people have one, and getting this wrong would make the
        // suggester useless for exactly the people who need it most.
        val nightWake = LocalTime.of(14, 0)
        val nightSleep = LocalTime.of(6, 0)
        assertEquals(Energy.PEAK, Energy.onSchedule(nightWake, nightSleep, nightWake))
        assertEquals(Energy.END_OF_DAY, Energy.onSchedule(nightWake, nightSleep, nightSleep))
        val middle = Energy.onSchedule(nightWake, nightSleep, LocalTime.of(0, 0))
        assertTrue(middle in (Energy.END_OF_DAY + 1) until Energy.PEAK, "midnight energy was $middle")
        assertEquals(Energy.ASLEEP, Energy.onSchedule(nightWake, nightSleep, LocalTime.of(10, 0)))
    }

    @Test
    fun `a degenerate schedule does not divide by zero`() {
        assertEquals(Energy.END_OF_DAY, Energy.onSchedule(wake, wake, LocalTime.of(12, 0)))
    }

    @Test
    fun `manual energy is returned as given and clamped`() {
        val profile = Profile(energyMode = EnergyMode.MANUAL, manualEnergy = 42)
        assertEquals(42, Energy.current(profile, LocalTime.of(3, 0)))
        assertEquals(100, Energy.current(profile.copy(manualEnergy = 500), LocalTime.NOON))
        assertEquals(0, Energy.current(profile.copy(manualEnergy = -5), LocalTime.NOON))
    }

    @Test
    fun `bands cover the whole range without gaps`() {
        (0..100).forEach { value ->
            // Exhaustive: a value with no band would crash the energy bar.
            Energy.band(value)
        }
        assertEquals(EnergyBand.HIGH, Energy.band(100))
        assertEquals(EnergyBand.HIGH, Energy.band(75))
        assertEquals(EnergyBand.MEDIUM, Energy.band(74))
        assertEquals(EnergyBand.MEDIUM, Energy.band(45))
        assertEquals(EnergyBand.LOW, Energy.band(44))
        assertEquals(EnergyBand.LOW, Energy.band(20))
        assertEquals(EnergyBand.SPENT, Energy.band(19))
        assertEquals(EnergyBand.SPENT, Energy.band(0))
    }
}
