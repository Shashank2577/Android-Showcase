package com.showcase.desertant.chats

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.showcase.desertant.app
import com.showcase.desertant.data.AppDatabase
import com.showcase.desertant.data.ChatEntity
import com.showcase.desertant.data.ChatMessageEntity
import com.showcase.desertant.data.ChatPersonEntity
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.people.CallLinks
import com.showcase.desertant.people.Contacts
import com.showcase.desertant.people.People
import com.showcase.desertant.privacy.LedgerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Chats come into Burrow only one way: the user shares an export to it
 * (WhatsApp → chat → ⋮ → More → Export chat → Burrow). Burrow reads that file
 * once, keeps the messages in its own database on the phone, and links each
 * name in it to a person.
 */
object ChatImporter {
    /** Who a name in a chat is. */
    sealed interface Who {
        data object Me : Who
        data class Person(val id: Long) : Who
        /** A new person, named as in the chat. */
        data object New : Who
        data object Nobody : Who
    }

    /** The text of a shared export and its file name, or null if nothing readable was shared. */
    suspend fun read(context: Context, intent: Intent): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val uris = buildList {
            stream(intent)?.let(::add)
            streams(intent)?.let(::addAll)
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        // WhatsApp sends the .txt (and, with media, the media files or a .zip).
        for (uri in uris.sortedBy { if (name(context, it)?.endsWith(".txt", true) == true) 0 else 1 }) {
            val name = name(context, uri)
            val text = runCatching { textOf(context, uri, name) }.getOrNull() ?: continue
            if (ChatParser.looksLikeChat(text)) return@withContext text to (name?.takeUnless { it.startsWith("_chat") } ?: subject)
        }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { ChatParser.looksLikeChat(it) }?.let { it to subject }
    }

    private fun textOf(context: Context, uri: Uri, name: String?): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { raw ->
            if (name?.endsWith(".zip", true) == true || context.contentResolver.getType(uri) == "application/zip") {
                val zip = ZipInputStream(raw)
                generateSequence { zip.nextEntry }.firstOrNull { it.name.endsWith(".txt", true) } ?: return null
                zip.readBytes().toString(Charsets.UTF_8)
            } else {
                raw.readBytes().toString(Charsets.UTF_8)
            }
        }
    }

    private fun name(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    @Suppress("DEPRECATION")
    private fun stream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri>? =
        if (intent.action != Intent.ACTION_SEND_MULTIPLE) null
        else if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)

    /**
     * Burrow's first guess at who each name is: a person with that name, alias
     * or number; in a one-to-one chat "with Priya", the other name is you.
     */
    fun guess(chat: ChatParser.Chat, people: List<ProfileEntity>): Map<String, Who> {
        val me = people.firstOrNull { it.isMe }
        val names = chat.senders.map { it.first }
        val out = LinkedHashMap<String, Who>()
        names.forEach { n ->
            val p = Contacts.match(n, people)
            out[n] = when {
                p == null -> Who.Nobody
                p.id == me?.id -> Who.Me
                else -> Who.Person(p.id)
            }
        }
        val title = chat.title?.let(Contacts::plain)
        if (names.size == 2 && out.values.none { it == Who.Me }) {
            val other = names.firstOrNull { Contacts.plain(it) == title } ?: names.firstOrNull { out[it] is Who.Person }
            if (other != null) {
                val mine = names.first { it != other }
                out[mine] = Who.Me
                if (out[other] == Who.Nobody) out[other] = Who.New
            }
        }
        return out
    }

    /** Keep the chat. The same chat exported again replaces the older copy. Returns the chat's id. */
    suspend fun save(context: Context, chat: ChatParser.Chat, who: Map<String, Who>, source: String = "whatsapp"): Long {
        val app = context.app
        val db = app.db
        require(chat.messages.isNotEmpty()) { "no messages in that file" }
        val people = People(context)
        val title = chat.title ?: chat.senders.firstOrNull { who[it.first] !is Who.Me }?.first ?: "chat"
        val first = chat.messages.minOf { it.at }
        db.chats().allNow().filter { it.title == title && it.source == source && it.firstAt == first }.forEach { db.chats().delete(it.id) }

        val id = db.chats().insert(ChatEntity(title = title, source = source, importedAt = System.currentTimeMillis(), firstAt = first, lastAt = chat.messages.maxOf { it.at }, messageCount = chat.messages.size))
        chat.messages.chunked(2_000).forEach { part -> db.chats().insertMessages(part.map { ChatMessageEntity(chatId = id, at = it.at, sender = it.sender, text = it.text) }) }
        val counts = chat.senders.toMap()
        db.chats().upsertPeople(
            counts.keys.map { sender ->
                val w = who[sender] ?: Who.Nobody
                val profileId = when (w) {
                    is Who.Person -> w.id
                    Who.New -> people.create(sender.trim(), isMe = false)
                    Who.Me -> db.profiles().allNow().firstOrNull { it.isMe }?.id
                    Who.Nobody -> null
                }
                ChatPersonEntity(id, sender, profileId, isMe = w == Who.Me, messages = counts[sender] ?: 0)
            },
        )
        rememberNames(db, id)
        app.ledger.add(LedgerEntry.CHATS_ADDED)
        return id
    }

    /** Change who one name in a chat is. */
    suspend fun setWho(context: Context, chatId: Long, sender: String, w: Who) {
        val db = context.app.db
        val profileId = when (w) {
            is Who.Person -> w.id
            Who.New -> People(context).create(sender.trim(), isMe = false)
            Who.Me -> db.profiles().allNow().firstOrNull { it.isMe }?.id
            Who.Nobody -> null
        }
        db.chats().setPerson(chatId, sender, profileId, w == Who.Me)
        rememberNames(db, chatId)
    }

    /** A chat name that differs from the person's ("Mum", "Priya 🌸") becomes an alias, so their calls and next chats link too. */
    private suspend fun rememberNames(db: AppDatabase, chatId: Long) {
        val links = db.chats().allPeopleNow().filter { it.chatId == chatId && it.profileId != null }
        links.forEach { l ->
            val p = db.profiles().get(l.profileId!!) ?: return@forEach
            val updated = Contacts.withAlias(p, l.sender)
            if (updated != p) db.profiles().update(updated)
        }
        CallLinks.relinkAll(db)
    }
}
