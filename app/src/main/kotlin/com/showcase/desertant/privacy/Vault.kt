package com.showcase.desertant.privacy

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
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
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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

class VaultLockedException : Exception("The vault is locked. Open it again.")

/** What happened when the user tried to open the vault, in words the screen can show. */
sealed interface VaultOpen {
    data object Opened : VaultOpen
    data object Cancelled : VaultOpen
    data object NoScreenLock : VaultOpen

    /** The screen lock was removed or reset since the vault was made; Android destroyed its key. */
    data object KeyLost : VaultOpen
    data class Failed(val message: String) : VaultOpen
}

/** AES-256-GCM sealing of one file's bytes: `[iv length][iv][ciphertext+tag]`. */
internal object VaultCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun seal(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        val ivSize = sealed[0].toInt()
        val iv = sealed.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return cipher.doFinal(sealed, 1 + ivSize, sealed.size - 1 - ivSize)
    }
}

/**
 * Private photos, moved out of the gallery and encrypted on the phone.
 *
 * How the keys work:
 * - Photos, thumbnails and the index are sealed with a random 256-bit data key
 *   (AES-GCM, see [VaultCrypto]).
 * - That data key is stored only wrapped by a Keystore key that can't leave the
 *   phone's secure hardware and needs the user's fingerprint or screen lock for
 *   every single use. Opening the vault is one fingerprint prompt bound to that
 *   unwrap (a BiometricPrompt CryptoObject), so it works the same on every
 *   phone, with no "recently unlocked" time window to run out mid-task.
 * - The unwrapped data key lives only in memory while the vault is open and is
 *   dropped when Burrow leaves the screen.
 * - Files live in `noBackupFilesDir` (and the app opts out of backup), so the
 *   vault never reaches a cloud backup, and no other app can read them.
 */
class Vault(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val indexFile = File(dir, "index.bin")
    private val wrappedKeyFile = File(dir, "key.bin")
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var dataKey: SecretKey? = null

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val thumbs = HashMap<String, Bitmap>()

    /** How many photos are inside, readable without unlocking (the count isn't secret, the photos are). */
    val count: Int get() = dir.listFiles { f -> f.name.endsWith(".photo") }?.size ?: 0

    fun available(): Boolean = Biometrics.deviceSecure(context)

    /** One fingerprint (or PIN) opens the vault until Burrow leaves the screen. */
    suspend fun unlock(): VaultOpen {
        if (dataKey != null) return VaultOpen.Opened
        if (!available()) return VaultOpen.NoScreenLock
        return try {
            val wrapped = if (wrappedKeyFile.exists()) wrappedKeyFile.readBytes() else null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                if (wrapped == null) cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
                else cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, wrapped.copyOfRange(1, 1 + wrapped[0])))
            } catch (_: KeyPermanentlyInvalidatedException) {
                return VaultOpen.KeyLost
            }
            val answer = Biometrics.prompt(
                context, "Open the vault", "Your private photos stay encrypted on this phone", BiometricPrompt.CryptoObject(cipher),
            )
            val unlockedCipher = when (answer) {
                is Confirmation.Confirmed -> answer.crypto?.cipher ?: cipher
                Confirmation.Cancelled -> return VaultOpen.Cancelled
                is Confirmation.Failed -> return VaultOpen.Failed(answer.message)
            }
            val key = withContext(Dispatchers.IO) {
                if (wrapped == null) {
                    val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    val sealed = unlockedCipher.doFinal(raw)
                    writeAtomically(wrappedKeyFile, byteArrayOf(unlockedCipher.iv.size.toByte()) + unlockedCipher.iv + sealed)
                    SecretKeySpec(raw, "AES")
                } else {
                    val ivSize = wrapped[0].toInt()
                    SecretKeySpec(unlockedCipher.doFinal(wrapped, 1 + ivSize, wrapped.size - 1 - ivSize), "AES")
                }
            }
            dataKey = key
            if (wrapped == null) withContext(Dispatchers.IO) { migrateLegacy(key) }
            _items.value = withContext(Dispatchers.IO) { readIndex(key) }
            _unlocked.value = true
            VaultOpen.Opened
        } catch (e: KeyPermanentlyInvalidatedException) {
            VaultOpen.KeyLost
        } catch (e: Exception) {
            VaultOpen.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun lock() {
        dataKey = null
        _unlocked.value = false
        _items.value = emptyList()
        synchronized(thumbs) {
            thumbs.values.forEach { it.recycle() }
            thumbs.clear()
        }
    }

    private fun key(): SecretKey = dataKey ?: throw VaultLockedException()

    /**
     * Copy [uris] into the vault. The caller then asks Android to delete the
     * originals; if the user says no, [remove] the copies again.
     */
    suspend fun add(uris: List<Uri>, kind: String? = null): List<VaultItem> = withContext(Dispatchers.IO) {
        val key = key()
        val added = uris.mapNotNull { uri ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            val id = UUID.randomUUID().toString()
            writeAtomically(File(dir, "$id.photo"), VaultCrypto.seal(key, bytes))
            thumbnailBytes(uri)?.let { writeAtomically(File(dir, "$id.thumb"), VaultCrypto.seal(key, it)) }
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
        writeIndex(key, all)
        _items.value = all
        added
    }

    suspend fun remove(ids: Collection<String>) = withContext(Dispatchers.IO) {
        val key = key()
        ids.forEach { id ->
            File(dir, "$id.photo").delete()
            File(dir, "$id.thumb").delete()
            synchronized(thumbs) { thumbs.remove(id)?.recycle() }
        }
        val left = _items.value.filterNot { it.id in ids }
        writeIndex(key, left)
        _items.value = left
    }

    suspend fun thumbnail(id: String): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(thumbs) { thumbs[id] }?.let { return@withContext it }
        val key = dataKey ?: return@withContext null
        val bytes = runCatching { VaultCrypto.open(key, File(dir, "$id.thumb").readBytes()) }.getOrNull()
            ?: runCatching { VaultCrypto.open(key, File(dir, "$id.photo").readBytes()) }.getOrNull()
            ?: return@withContext null
        decode(bytes, 384)?.also { b -> synchronized(thumbs) { thumbs[id] = b } }
    }

    /** The full photo, decoded no larger than [maxSide]. */
    suspend fun photo(id: String, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        val key = dataKey ?: return@withContext null
        val bytes = runCatching { VaultCrypto.open(key, File(dir, "$id.photo").readBytes()) }.getOrNull() ?: return@withContext null
        decode(bytes, maxSide)
    }

    /** Put a copy back in the gallery (Pictures/Burrow), and take it out of the vault. */
    suspend fun export(item: VaultItem): Uri? = withContext(Dispatchers.IO) {
        val bytes = VaultCrypto.open(key(), File(dir, "${item.id}.photo").readBytes())
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

    /**
     * Start again with an empty vault, for when Android destroyed the key
     * (the screen lock was removed). The photos in it can't be recovered.
     */
    fun reset() = wipe()

    /** Delete everything, used by "wipe everything". Works while locked: no decryption needed. */
    fun wipe() {
        lock()
        dir.listFiles()?.forEach { it.delete() }
        runCatching { keyStore().deleteEntry(WRAPPING_KEY) }
        runCatching { keyStore().deleteEntry(LEGACY_KEY) }
    }

    /**
     * 3.0 sealed files directly with a time-window Keystore key. The prompt that
     * just opened the vault also satisfies that window, so re-seal everything
     * with the new data key once, then drop the old key.
     */
    private fun migrateLegacy(key: SecretKey) {
        val legacy = runCatching { keyStore().getKey(LEGACY_KEY, null) as? SecretKey }.getOrNull() ?: return
        dir.listFiles { f -> f.name.endsWith(".photo") || f.name.endsWith(".thumb") || f == indexFile }?.forEach { f ->
            runCatching { writeAtomically(f, VaultCrypto.seal(key, VaultCrypto.open(legacy, f.readBytes()))) }
        }
        runCatching { keyStore().deleteEntry(LEGACY_KEY) }
    }

    private fun readIndex(key: SecretKey): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return json.decodeFromString(ListSerializer(VaultItem.serializer()), String(VaultCrypto.open(key, indexFile.readBytes())))
    }

    private fun writeIndex(key: SecretKey, items: List<VaultItem>) =
        writeAtomically(indexFile, VaultCrypto.seal(key, json.encodeToString(ListSerializer(VaultItem.serializer()), items).toByteArray()))

    private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

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

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** The Keystore key that wraps the data key. Every use needs the user, through the prompt. */
    private fun wrappingKey(): SecretKey {
        (keyStore().getKey(WRAPPING_KEY, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(WRAPPING_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .setInvalidatedByBiometricEnrollment(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val WRAPPING_KEY = "burrow-vault-wrap"

        /** The 3.0 key, which used a time window instead of a per-use prompt. */
        const val LEGACY_KEY = "burrow-vault"
    }
}
