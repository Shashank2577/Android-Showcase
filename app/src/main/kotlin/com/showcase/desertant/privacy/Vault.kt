package com.showcase.desertant.privacy

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val addedAt: Long,
    val bytes: Long,
    /** What the private sweep found it to be ("ID & bank cards"…), if it came from there. */
    val kind: String? = null,
)

class VaultLockedException : Exception("The vault is locked")

/**
 * Private photos, moved out of the gallery and encrypted on the phone.
 *
 * - Every file (photo, thumbnail, and the index of what's inside) is AES-256-GCM
 *   encrypted with a key that lives in the Android Keystore, can't be exported,
 *   and only works for a minute after the user confirms with a fingerprint or
 *   their screen lock.
 * - Files live in `noBackupFilesDir`, and the app opts out of backup, so the
 *   vault never reaches a cloud backup. Nothing is visible to the gallery or
 *   other apps.
 * - Decrypted thumbnails are kept only in memory while the vault is open, and
 *   dropped as soon as the app leaves the screen.
 */
class Vault(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val indexFile = File(dir, "index.bin")
    private val json = Json { ignoreUnknownKeys = true }

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val thumbs = HashMap<String, Bitmap>()

    /** How many photos are inside, readable without unlocking (the count isn't secret, the photos are). */
    val count: Int get() = dir.listFiles { f -> f.name.endsWith(".photo") }?.size ?: 0

    fun available(): Boolean = Biometrics.deviceSecure(context)

    /** Confirm it's the user, then open the vault. */
    suspend fun unlock(): Boolean {
        if (_unlocked.value) return true
        if (!available()) return false
        if (!Biometrics.confirm(context, "Open the vault", "Your private photos stay encrypted on this phone")) return false
        return try {
            _items.value = readIndex()
            _unlocked.value = true
            true
        } catch (_: UserNotAuthenticatedException) {
            false
        }
    }

    fun lock() {
        _unlocked.value = false
        _items.value = emptyList()
        synchronized(thumbs) {
            thumbs.values.forEach { it.recycle() }
            thumbs.clear()
        }
    }

    /**
     * Copy [uris] into the vault. The caller then asks Android to delete the
     * originals; if the user says no, [remove] the copies again.
     */
    suspend fun add(uris: List<Uri>, kind: String? = null): List<VaultItem> = withContext(Dispatchers.IO) {
        if (!_unlocked.value) throw VaultLockedException()
        val added = uris.mapNotNull { uri ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            val id = UUID.randomUUID().toString()
            write(File(dir, "$id.photo"), bytes)
            thumbnailBytes(uri)?.let { write(File(dir, "$id.thumb"), it) }
            VaultItem(
                id = id,
                name = displayName(uri) ?: "photo.jpg",
                mime = context.contentResolver.getType(uri) ?: "image/jpeg",
                addedAt = System.currentTimeMillis(),
                bytes = bytes.size.toLong(),
                kind = kind,
            )
        }
        val all = _items.value + added
        writeIndex(all)
        _items.value = all
        added
    }

    suspend fun remove(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (!_unlocked.value) throw VaultLockedException()
        ids.forEach { id ->
            File(dir, "$id.photo").delete()
            File(dir, "$id.thumb").delete()
            synchronized(thumbs) { thumbs.remove(id)?.recycle() }
        }
        val left = _items.value.filterNot { it.id in ids }
        writeIndex(left)
        _items.value = left
    }

    suspend fun thumbnail(id: String): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(thumbs) { thumbs[id] }?.let { return@withContext it }
        if (!_unlocked.value) return@withContext null
        val bytes = runCatching { read(File(dir, "$id.thumb")) }.getOrNull() ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { b -> synchronized(thumbs) { thumbs[id] = b } }
    }

    /** The full photo, decoded no larger than [maxSide]. */
    suspend fun photo(id: String, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        if (!_unlocked.value) return@withContext null
        val bytes = runCatching { read(File(dir, "$id.photo")) }.getOrNull() ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Put a copy back in the gallery (Pictures/Burrow), and take it out of the vault. */
    suspend fun export(item: VaultItem): Uri? = withContext(Dispatchers.IO) {
        if (!_unlocked.value) throw VaultLockedException()
        val bytes = read(File(dir, "${item.id}.photo"))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, item.name)
            put(MediaStore.Images.Media.MIME_TYPE, item.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Burrow")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        remove(listOf(item.id))
        uri
    }

    /** Delete everything, used by "wipe everything". Works while locked: no decryption needed. */
    fun wipe() {
        lock()
        dir.listFiles()?.forEach { it.delete() }
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun readIndex(): List<VaultItem> {
        if (!indexFile.exists()) {
            // Touch the key so a missing authentication surfaces now, not on the first add.
            write(indexFile, "[]".toByteArray())
            return emptyList()
        }
        return json.decodeFromString(ListSerializer(VaultItem.serializer()), String(read(indexFile)))
    }

    private fun writeIndex(items: List<VaultItem>) =
        write(indexFile, json.encodeToString(ListSerializer(VaultItem.serializer()), items).toByteArray())

    private fun thumbnailBytes(uri: Uri): ByteArray? = runCatching {
        val b = context.contentResolver.loadThumbnail(uri, Size(384, 384), null)
        ByteArrayOutputStream().use { out ->
            b.compress(Bitmap.CompressFormat.JPEG, 85, out)
            b.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun write(file: File, plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain)
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { out ->
            out.write(cipher.iv.size)
            out.write(cipher.iv)
            out.write(sealed)
        }
        tmp.renameTo(file)
    }

    private fun read(file: File): ByteArray {
        val all = file.readBytes()
        val ivSize = all[0].toInt()
        val iv = all.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(all, 1 + ivSize, all.size - 1 - ivSize)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(AUTH_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .setInvalidatedByBiometricEnrollment(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "burrow-vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** How long one confirmation keeps the key usable: enough to add a sweep's worth of photos. */
        const val AUTH_SECONDS = 120
    }
}
