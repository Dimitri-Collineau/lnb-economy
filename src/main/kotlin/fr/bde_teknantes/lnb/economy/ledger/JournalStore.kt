package fr.bde_teknantes.lnb.economy.ledger

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The journal on disk, as JSON. Written synchronously and atomically before every send, so a crash never loses a
 * transfer whose key may already have reached the site.
 */
class JournalStore(private val file: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val listType = object : TypeToken<List<JournalEntry>>() {}.type

    fun load(): List<JournalEntry> {
        if (!Files.exists(file)) return emptyList()
        val text = Files.readString(file)
        if (text.isBlank()) return emptyList()
        return gson.fromJson<List<JournalEntry>>(text, listType).orEmpty()
    }

    fun save(entries: Collection<JournalEntry>) {
        Files.createDirectories(file.parent)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temp, gson.toJson(entries.toList(), listType))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
