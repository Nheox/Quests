package cl.nheox.quests.events

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.useLines

/** A line that could not be parsed, kept so the UI can say so instead of guessing. */
public data class SkippedLine(
    val file: String,
    val lineNumber: Int,
    val content: String,
    val reason: String,
)

public data class LoadResult(
    val events: List<Event>,
    val skipped: List<SkippedLine> = emptyList(),
    val filesRead: List<String> = emptyList(),
) {
    public val isEmpty: Boolean get() = events.isEmpty()
}

/**
 * The folder the user chose. Everything lives here as plain text, and the app
 * holds no authoritative state of its own — it replays this on launch.
 */
public class Vault(public val root: Path) {
    public val snapshotsDir: Path get() = root.resolve("snapshots")
    public val exportDir: Path get() = root.resolve("export")

    public fun logFile(device: String): Path = root.resolve("events-$device.jsonl")

    public fun prepare() {
        root.createDirectories()
        snapshotsDir.createDirectories()
        exportDir.createDirectories()
    }

    public companion object {
        public const val DEFAULT_FOLDER_NAME: String = "Quests"
    }
}

/**
 * Reads every log in the vault and appends only to this device's own.
 *
 * Reads are deliberately forgiving. A synced folder is a hostile environment:
 * a sync client can leave a half-written line behind, and the user is
 * encouraged to open these files in an editor. One bad line must cost one line,
 * never the whole history — so parse failures are collected and reported, not
 * thrown.
 */
public class FileEventLog(
    private val vault: Vault,
    private val device: DeviceIdentity,
) {
    public fun load(): LoadResult {
        if (!vault.root.exists()) return LoadResult(emptyList())

        val events = mutableListOf<Event>()
        val skipped = mutableListOf<SkippedLine>()
        val filesRead = mutableListOf<String>()
        val seenClocks = HashSet<String>()

        for (snapshot in Snapshots.list(vault)) {
            val loaded = Snapshots.read(snapshot) ?: continue
            filesRead += snapshot.name
            for (event in loaded.events) {
                if (seenClocks.add(event.hlc)) events += event
            }
        }

        for (file in DeviceIdentity.logFiles(vault.root)) {
            filesRead += file.name
            var lineNumber = 0
            try {
                file.useLines(StandardCharsets.UTF_8) { lines ->
                    for (line in lines) {
                        lineNumber++
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        val event = EventCodec.decodeOrNull(trimmed)
                        if (event == null) {
                            skipped += SkippedLine(file.name, lineNumber, trimmed.take(240), "unparseable")
                            continue
                        }
                        if (event.clock == null) {
                            skipped += SkippedLine(file.name, lineNumber, trimmed.take(240), "bad clock")
                            continue
                        }
                        // A snapshot and a not-yet-truncated log can legitimately
                        // carry the same event. The clock is globally unique, so
                        // it is also the deduplication key.
                        if (seenClocks.add(event.hlc)) events += event
                    }
                }
            } catch (error: IOException) {
                skipped += SkippedLine(file.name, lineNumber, "", error.message ?: "unreadable")
            }
        }

        return LoadResult(events, skipped, filesRead)
    }

    /**
     * Appends to this device's own log and forces it to disk.
     *
     * The force costs a few milliseconds per user action and buys the guarantee
     * that completing a quest survives a crash or a lost battery. For an
     * append-only log that is the whole durability story.
     */
    public fun append(events: List<Event>) {
        if (events.isEmpty()) return
        vault.prepare()
        val payload = events.joinToString("") { EventCodec.encode(it) + "\n" }
            .toByteArray(StandardCharsets.UTF_8)
        val target = vault.logFile(device.id)
        java.nio.channels.FileChannel.open(
            target,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        ).use { channel ->
            channel.write(ByteBuffer.wrap(payload))
            channel.force(false)
        }
    }

    public fun ownLogFile(): Path = vault.logFile(device.id)

    /** Replaces this device's own log, which is the only file it may rewrite. */
    internal fun rewriteOwnLog(events: List<Event>) {
        vault.prepare()
        val target = vault.logFile(device.id)
        val temp = target.resolveSibling(target.name + ".tmp")
        val payload = events.joinToString("") { EventCodec.encode(it) + "\n" }
        Files.writeString(
            temp,
            payload,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
