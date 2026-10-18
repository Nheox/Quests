package cl.nheox.quests.events

import kotlinx.serialization.json.Json

/**
 * One JSON object per line, with `type` as the discriminator, so a log stays
 * readable — and repairable — in a text editor. That is a feature, not an
 * accident: the user owns these files (§3.3).
 */
public object EventCodec {
    public val json: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = false
        explicitNulls = false
        // A newer version writing a field this one has never heard of must not
        // make the log unreadable: forward compatibility matters when the same
        // folder is synced between machines running different releases.
        ignoreUnknownKeys = true
        prettyPrint = false
        allowStructuredMapKeys = false
    }

    /** Pretty-printed, for snapshots and for anything a human will diff. */
    public val prettyJson: Json = Json(json) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    public fun encode(event: Event): String = json.encodeToString(Event.serializer(), event)

    public fun decode(line: String): Event = json.decodeFromString(Event.serializer(), line)

    public fun decodeOrNull(line: String): Event? = runCatching { decode(line) }.getOrNull()
}
