package cl.nheox.quests.events

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
public data class Snapshot(
    val version: Int = CURRENT_VERSION,
    val device: String,
    /** Everything in this snapshot has a clock at or below this one. */
    val upToHlc: String,
    val events: List<Event>,
) {
    public companion object {
        public const val CURRENT_VERSION: Int = 1
    }
}

public object Snapshots {
    private val fileName = Regex("""^(.+)-(\d{5})\.json$""")

    public fun list(vault: Vault): List<Path> {
        val dir = vault.snapshotsDir
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && fileName.matches(it.name) }.sorted().toList()
        }
    }

    public fun read(file: Path): Snapshot? = runCatching {
        EventCodec.json.decodeFromString(Snapshot.serializer(), file.readText())
    }.getOrNull()

    public fun nextIndex(vault: Vault, device: String): Int {
        val highest = list(vault)
            .mapNotNull { fileName.matchEntire(it.name) }
            .filter { it.groupValues[1] == device }
            .mapNotNull { it.groupValues[2].toIntOrNull() }
            .maxOrNull() ?: 0
        return highest + 1
    }

    public fun write(vault: Vault, snapshot: Snapshot): Path {
        vault.prepare()
        val index = nextIndex(vault, snapshot.device)
        val target = vault.snapshotsDir.resolve("${snapshot.device}-${index.toString().padStart(5, '0')}.json")
        target.writeText(EventCodec.prettyJson.encodeToString(Snapshot.serializer(), snapshot))
        return target
    }
}

/**
 * Keeps logs from growing forever.
 *
 * Because every file belongs to exactly one device, compaction needs no
 * coordination at all: a device rewrites its own log and nobody else's, and the
 * result still merges with everyone else's logs by the same rule as before.
 *
 * What is dropped is narrow on purpose. Only *superseded field edits* go: if
 * this device set a quest's title three times, only the last setting can ever
 * win, so the first two are dead weight. Everything additive or historical
 * stays — completions, failures, claims, tombstones — because that history is
 * the data behind the fear log and the trend charts (§7.4). A tracker that
 * compacted away the record of what you did would be compacting away the
 * point.
 */
public object Compaction {
    public data class Result(
        val kept: List<Event>,
        val droppedEvents: Int,
        val droppedFields: Int,
    ) {
        public val changedAnything: Boolean get() = droppedEvents > 0 || droppedFields > 0
    }

    /**
     * Compacts one device's own log.
     *
     * Safety argument for dropping a superseded field edit: suppose this device
     * set field F at clock 5 and again at clock 20, and another device set F at
     * clock 12. Full replay applies 5, then 12, then 20 — so 20 wins.
     * Compacted replay applies 12 and 20 — 20 still wins. Any edit that lost to
     * a later edit *from the same device* loses to that same edit in every
     * interleaving, so removing it cannot change the outcome.
     */
    public fun compact(ownEvents: List<Event>): Result {
        val ordered = ownEvents.sortedWith(compareBy(Hlc.ENCODED_ORDER) { it.hlc })

        // Walk backwards: the first time a field is seen is its last setting.
        val seenFields = HashSet<Pair<String, String>>()
        val keptReversed = ArrayList<Event>(ordered.size)
        var droppedEvents = 0
        var droppedFields = 0

        for (event in ordered.asReversed()) {
            val entityId = entityIdOf(event)
            val fields = fieldsOf(event)
            if (entityId == null || fields == null) {
                keptReversed += event
                continue
            }
            val surviving = fields.filterKeys { field -> seenFields.add(entityId to field) }
            droppedFields += fields.size - surviving.size
            when {
                surviving.size == fields.size -> keptReversed += event
                surviving.isEmpty() -> droppedEvents++
                else -> keptReversed += withFields(event, surviving)
            }
        }

        return Result(keptReversed.asReversed().toList(), droppedEvents, droppedFields)
    }

    /**
     * Compacts this device's log and leaves a snapshot behind, so the history
     * is still on disk even though the log no longer carries it.
     */
    public fun run(vault: Vault, device: DeviceIdentity, log: FileEventLog): Result? {
        val all = log.load().events
        val own = all.filter { it.device == device.id }
        if (own.isEmpty()) return null
        val result = compact(own)
        if (!result.changedAnything) return result
        val highest = result.kept.maxWithOrNull(compareBy(Hlc.ENCODED_ORDER) { it.hlc })?.hlc ?: return result
        Snapshots.write(
            vault,
            Snapshot(device = device.id, upToHlc = highest, events = result.kept),
        )
        // The snapshot is on disk before the log is replaced, so a crash between
        // the two loses nothing: load() unions both and deduplicates by clock.
        log.rewriteOwnLog(emptyList())
        return result
    }

    private fun entityIdOf(event: Event): String? = when (event) {
        is Event.QuestChanged -> "quest:" + event.id
        is Event.SkillChanged -> "skill:" + event.id
        is Event.RewardChanged -> "reward:" + event.id
        is Event.ItemChanged -> "item:" + event.id
        is Event.NoteChanged -> "note:" + event.id
        is Event.ProfileChanged -> "profile"
        is Event.SettingsChanged -> "settings"
        else -> null
    }

    private fun fieldsOf(event: Event) = when (event) {
        is Event.QuestChanged -> event.fields
        is Event.SkillChanged -> event.fields
        is Event.RewardChanged -> event.fields
        is Event.ItemChanged -> event.fields
        is Event.NoteChanged -> event.fields
        is Event.ProfileChanged -> event.fields
        is Event.SettingsChanged -> event.fields
        else -> null
    }

    private fun withFields(
        event: Event,
        fields: Map<String, kotlinx.serialization.json.JsonElement>,
    ): Event = when (event) {
        is Event.QuestChanged -> event.copy(fields = fields)
        is Event.SkillChanged -> event.copy(fields = fields)
        is Event.RewardChanged -> event.copy(fields = fields)
        is Event.ItemChanged -> event.copy(fields = fields)
        is Event.NoteChanged -> event.copy(fields = fields)
        is Event.ProfileChanged -> event.copy(fields = fields)
        is Event.SettingsChanged -> event.copy(fields = fields)
        else -> event
    }
}
