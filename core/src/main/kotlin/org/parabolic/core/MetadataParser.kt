package org.parabolic.core

import kotlinx.serialization.json.*

/** Reads the complete yt-dlp schema without relying on the wrapper's incomplete VideoInfo DTO. */
class MetadataParser {
    fun parse(raw: String): MediaInfo = parseObject(Json.parseToJsonElement(raw).jsonObject, 0)

    private fun parseObject(o: JsonObject, depth: Int): MediaInfo {
        require(depth <= 8) { "Metadata collection nesting is too deep" }
        val rawEntries = o.array("entries")
        val entries = rawEntries.mapNotNull { (it as? JsonObject)?.let { entry -> parseObject(entry, depth + 1) } }
        return MediaInfo(
            id = o.string("id"), title = o.string("title") ?: o.string("id") ?: "Untitled media",
            webpageUrl = o.string("webpage_url") ?: o.string("original_url") ?: o.string("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            thumbnailUrl = o.string("thumbnail"), uploader = o.string("uploader") ?: o.string("channel"),
            durationSeconds = o.number("duration")?.takeIf { it >= 0 },
            formats = o.array("formats").mapNotNull { item ->
                val f = item as? JsonObject ?: return@mapNotNull null
                val id = f.string("format_id") ?: return@mapNotNull null
                MediaFormat(id, f.string("ext"), f.int("width"), f.int("height"), f.number("fps"),
                    f.string("vcodec")?.takeUnless { it == "none" }, f.string("acodec")?.takeUnless { it == "none" },
                    f.string("language"), f.string("format_note"), f.long("filesize") ?: f.long("filesize_approx"),
                    f.long("filesize") == null && f.long("filesize_approx") != null, f.number("tbr"), f.int("audio_channels"))
            },
            chapters = o.array("chapters").mapNotNull { item ->
                val c = item as? JsonObject ?: return@mapNotNull null
                val start = c.number("start_time") ?: return@mapNotNull null
                Chapter(c.string("title") ?: "Chapter", start, c.number("end_time"))
            },
            subtitles = tracks(o, "subtitles", false) + tracks(o, "automatic_captions", true),
            entries = entries, isCollection = o.string("_type") in setOf("playlist", "multi_video") || "entries" in o,
            entryCount = o.int("playlist_count") ?: o.int("n_entries"), unavailableEntries = rawEntries.size - entries.size,
        )
    }
    private fun tracks(o: JsonObject, key: String, automatic: Boolean): List<SubtitleTrack> =
        (o[key] as? JsonObject)?.map { (language, entries) ->
            SubtitleTrack(language, (entries as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("ext") }.toSet(), automatic)
        }.orEmpty()
}
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
