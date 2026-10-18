package cl.nheox.quests.domain

import kotlin.random.Random

/**
 * Lexicographically sortable, collision-resistant identifiers (ULID shape:
 * 48-bit millisecond timestamp, 80 bits of randomness, Crockford base32).
 *
 * Sortable ids matter here because two devices generate ids independently and
 * never coordinate. The prefix makes a log line readable without a decoder.
 */
public object Ids {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIME_CHARS = 10
    private const val RANDOM_CHARS = 16

    public const val QUEST: String = "q"
    public const val SKILL: String = "s"
    public const val REWARD: String = "r"
    public const val ITEM: String = "i"
    public const val NOTE: String = "n"

    /** `q_01K8ZV9XQ4T0F2M7B1C3D5E7G9` */
    public fun generate(prefix: String, nowMillis: Long, random: Random = Random.Default): String =
        prefix + "_" + encodeTime(nowMillis) + encodeRandom(random)

    public fun quest(nowMillis: Long, random: Random = Random.Default): String = generate(QUEST, nowMillis, random)

    public fun skill(nowMillis: Long, random: Random = Random.Default): String = generate(SKILL, nowMillis, random)

    public fun reward(nowMillis: Long, random: Random = Random.Default): String = generate(REWARD, nowMillis, random)

    public fun item(nowMillis: Long, random: Random = Random.Default): String = generate(ITEM, nowMillis, random)

    public fun note(nowMillis: Long, random: Random = Random.Default): String = generate(NOTE, nowMillis, random)

    private fun encodeTime(millis: Long): String {
        require(millis >= 0) { "timestamp must not be negative" }
        val out = CharArray(TIME_CHARS)
        var remaining = millis
        for (i in TIME_CHARS - 1 downTo 0) {
            out[i] = ALPHABET[(remaining % 32L).toInt()]
            remaining /= 32L
        }
        return out.concatToString()
    }

    private fun encodeRandom(random: Random): String {
        val out = CharArray(RANDOM_CHARS)
        for (i in 0 until RANDOM_CHARS) out[i] = ALPHABET[random.nextInt(32)]
        return out.concatToString()
    }
}
