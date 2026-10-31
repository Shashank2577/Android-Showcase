package com.showcase.desertant.people

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract
import com.showcase.desertant.data.ProfileEntity

/**
 * Phone contacts without the contacts permission. The user picks one number in
 * Android's own contact picker; Android then lets Burrow read just that row
 * (name, number, which contact it is). Burrow never reads the address book.
 */
object Contacts {
    data class Picked(val name: String, val number: String, val contactUri: String?)

    /** Android's picker for one contact's phone number. */
    class PickNumber : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data.takeIf { resultCode == Activity.RESULT_OK }
    }

    fun read(context: Context, uri: Uri): Picked? =
        context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.CONTACT_ID, Phone.LOOKUP_KEY), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getLong(2)
            val key = c.getString(3)
            Picked(
                name = c.getString(0).orEmpty(),
                number = c.getString(1).orEmpty(),
                contactUri = if (key != null) ContactsContract.Contacts.getLookupUri(id, key)?.toString() else null,
            )
        }

    /** Digits only; enough to compare numbers written with and without a country code. */
    fun digits(number: String) = number.filter { it.isDigit() }

    fun sameNumber(a: String, b: String): Boolean {
        val x = digits(a)
        val y = digits(b)
        return x.length >= 6 && y.length >= 6 && x.takeLast(9) == y.takeLast(9)
    }

    fun phonesOf(p: ProfileEntity) = p.phones.split(',').filter { it.isNotBlank() }

    fun aliasesOf(p: ProfileEntity) = p.aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** "Priya 🌸" → "priya": what names are compared on. */
    fun plain(name: String) = name.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex("\\s+"), " ")

    /**
     * Which person [who] (a name or number from a call recording or a chat) is,
     * if Burrow can tell for sure: a number on their contact, their name or an
     * alias, or a first name only one person has.
     */
    fun match(who: String, people: List<ProfileEntity>): ProfileEntity? {
        if (digits(who).length >= 6) people.firstOrNull { p -> phonesOf(p).any { sameNumber(it, who) } }?.let { return it }
        val name = plain(who.replace(Regex("[+(]?[\\d][\\d ()+-]{5,}"), ""))
        if (name.isEmpty()) return null
        people.filter { p -> plain(p.name) == name || aliasesOf(p).any { plain(it) == name } }.singleOrNull()?.let { return it }
        val first = name.substringBefore(' ')
        if (first.length < 2 || name.contains(' ')) return null
        return people.filter { plain(it.name).substringBefore(' ') == first }.singleOrNull()
    }

    fun withAlias(p: ProfileEntity, alias: String): ProfileEntity {
        val a = alias.trim()
        if (a.isEmpty() || plain(a) == plain(p.name) || aliasesOf(p).any { plain(it) == plain(a) }) return p
        return p.copy(aliases = (aliasesOf(p) + a.replace(",", " ")).joinToString(","))
    }

    fun withNumber(p: ProfileEntity, number: String): ProfileEntity {
        val d = digits(number)
        if (d.length < 6 || phonesOf(p).any { sameNumber(it, d) }) return p
        return p.copy(phones = (phonesOf(p) + d).joinToString(","))
    }
}
