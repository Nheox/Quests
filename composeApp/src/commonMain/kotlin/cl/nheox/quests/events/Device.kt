package cl.nheox.quests.events

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random

/**
 * This machine's identity, and the single most load-bearing idea in the sync
 * design: **a log file is only ever written by the device that owns it.**
 *
 * Logseq writes the same files from every device, which is why Logseq users
 * collect `.conflict` files. Here no sync tool ever sees a concurrent write to
 * one file, so there is nothing to merge at the file level — only a union to
 * replay. The cost is that the device name has to be stable for the life of the
 * machine, which is why it is written down rather than derived each launch.
 */
public data class DeviceIdentity(val id: String) {
    public val logFileName: String get() = "events-$id.jsonl"

    public companion object {
        public const val MAX_LENGTH: Int = 32

        /** `MacBook-Pro.local` becomes `macbook-pro`. */
        public fun sanitise(raw: String): String {
            val cleaned = raw.lowercase()
                .substringBefore(".local")
                .map { if (it.isLetterOrDigit() || it == '-') it else '-' }
                .joinToString("")
                .trim('-')
                .replace(Regex("-{2,}"), "-")
            return cleaned.take(MAX_LENGTH).ifBlank { "device" }
        }

        /**
         * Reads the stored device name, or picks one and stores it.
         *
         * The random suffix only appears when the hostname is useless
         * (`localhost`, a container id), because `events-macbook.jsonl` is a
         * much friendlier thing to find in a synced folder than
         * `events-7f3a91.jsonl`.
         */
        public fun loadOrCreate(
            configFile: Path,
            hostname: String?,
            random: Random = Random.Default,
        ): DeviceIdentity {
            if (configFile.exists()) {
                val stored = runCatching { configFile.readText().trim() }.getOrNull()
                if (!stored.isNullOrBlank()) return DeviceIdentity(sanitise(stored))
            }
            val base = sanitise(hostname.orEmpty())
            val id = if (base == "device" || base == "localhost") {
                "device-" + random.nextInt(0x100000, 0xFFFFFF).toString(16)
            } else {
                base
            }
            runCatching {
                configFile.createParentDirectories()
                configFile.writeText(id)
            }
            return DeviceIdentity(id)
        }

        /** The device that owns `events-macbook.jsonl`. */
        public fun fromLogFileName(fileName: String): String? {
            if (!fileName.startsWith("events-") || !fileName.endsWith(".jsonl")) return null
            return fileName.removePrefix("events-").removeSuffix(".jsonl").ifBlank { null }
        }

        internal fun logFiles(root: Path): List<Path> {
            if (!Files.isDirectory(root)) return emptyList()
            return Files.list(root).use { stream ->
                stream.filter { path ->
                    val name = path.fileName?.toString() ?: return@filter false
                    Files.isRegularFile(path) && fromLogFileName(name) != null
                }.sorted().toList()
            }
        }
    }
}
