package com.streakguard.app.diagnostics

import java.io.File
import java.time.Instant

/** Private segmented UTF-8 log, with entry-level expiry and eviction (not whole-day deletion). */
class RollingLogStore(
    private val directory: File,
    private val maxBytes: Long = 10_000_000,
    private val maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1000,
    private val now: () -> Long = System::currentTimeMillis,
    private val segmentBytes: Long = 64 * 1024,
) {
    private data class Entry(val index: Int, val timestamp: Long, val line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8).size.toLong()
    }
    private data class Segment(val file: File, val sequence: Long, var bytes: Long,
                               var earliest: Long, var latest: Long)
    private val segments = mutableListOf<Segment>()
    private var loaded = false
    init { require(maxBytes > 0 && maxAgeMillis > 0 && segmentBytes > 0) }

    private fun entries(file: File) = file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
        line.substringBefore('\t').toLongOrNull()?.let { Entry(index, it, line) }
    }
    private fun load() {
        if (loaded) return
        segments.clear()
        directory.mkdirs()
        File(directory, "rewrite.tmp").delete()
        directory.listFiles()?.filter { it.name.matches(Regex("log-[0-9]+\\.txt")) }?.forEach { file ->
            val records = entries(file)
            val segment = Segment(file, file.name.removePrefix("log-").removeSuffix(".txt").toLong(),
                file.length(), records.minOfOrNull { it.timestamp } ?: 0, records.maxOfOrNull { it.timestamp } ?: 0)
            segments.add(segment)
            // Recover partial/corrupt records after an interrupted write.
            if (records.isEmpty() || records.sumOf { it.bytes } != segment.bytes) rewrite(segment, records)
        }
        segments.sortBy { it.sequence }
        loaded = true
    }
    private fun rewrite(segment: Segment, records: List<Entry>) {
        if (records.isEmpty()) {
            check(!segment.file.exists() || segment.file.delete())
            segments.remove(segment)
            return
        }
        val temporary = File(directory, "rewrite.tmp")
        temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
            records.sortedBy { it.index }.forEach { writer.write(it.line); writer.write("\n") }
        }
        java.nio.file.Files.move(temporary.toPath(), segment.file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        segment.bytes = records.sumOf { it.bytes }
        segment.earliest = records.minOf { it.timestamp }
        segment.latest = records.maxOf { it.timestamp }
    }

    @Synchronized fun append(event: String, fields: String = "", timestamp: Long = now()) {
        load()
        val time = timestamp
        val line = "$time\t${Instant.ofEpochMilli(time)}\t$event\t$fields"
        require(!event.contains('\n') && !fields.contains('\n') && line.toByteArray().size <= 4096)
        val bytes = line.toByteArray(Charsets.UTF_8).size + 1L
        val last = segments.lastOrNull()
        val target = if (last != null && last.bytes + bytes <= segmentBytes) last else {
            val sequence = (last?.sequence ?: 0) + 1
            Segment(File(directory, "log-$sequence.txt"), sequence, 0, time, time).also { segments.add(it) }
        }
        try {
            target.file.appendText(line + "\n", Charsets.UTF_8)
            target.bytes += bytes
            target.earliest = minOf(target.earliest, time)
            target.latest = maxOf(target.latest, time)
            prune()
        } catch (error: Exception) { loaded = false; throw error }
    }

    @Synchronized fun prune() {
        load()
        val cutoff = now() - maxAgeMillis
        // Cached timestamp/byte metadata means ordinary writes never scan the archive.
        segments.toList().forEach { segment ->
            when {
                segment.latest <= cutoff -> rewrite(segment, emptyList())
                segment.earliest <= cutoff -> rewrite(segment, entries(segment.file).filter { it.timestamp > cutoff })
            }
        }
        var bytes = segments.sumOf { it.bytes }
        while (bytes > maxBytes) {
            val oldest = segments.minWithOrNull(compareBy<Segment> { it.earliest }.thenBy { it.sequence }) ?: break
            val nextTimestamp = segments.filter { it !== oldest }.minOfOrNull { it.earliest } ?: Long.MAX_VALUE
            val records = entries(oldest.file)
            val removed = mutableSetOf<Int>()
            for (record in records.sortedWith(compareBy<Entry> { it.timestamp }.thenBy { it.index })) {
                if (bytes <= maxBytes || (removed.isNotEmpty() && record.timestamp > nextTimestamp)) break
                removed.add(record.index)
                bytes -= record.bytes
            }
            rewrite(oldest, records.filter { it.index !in removed })
        }
    }

    @Synchronized fun size(): Long { prune(); return segments.sumOf { it.bytes } }
    @Synchronized fun read(): String { prune(); return segments.joinToString("") { it.file.readText(Charsets.UTF_8) } }
    @Synchronized fun copyTo(output: java.io.OutputStream) {
        prune()
        segments.forEach { segment -> segment.file.inputStream().use { it.copyTo(output) } }
    }
    @Synchronized fun clear() {
        try { directory.listFiles()?.forEach { check(!it.exists() || it.delete()) } }
        finally { segments.clear(); loaded = false }
    }
}
