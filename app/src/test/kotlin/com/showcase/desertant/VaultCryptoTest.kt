package com.showcase.desertant

import com.showcase.desertant.privacy.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator

class VaultCryptoTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun roundTrips() {
        val key = key()
        val plain = ByteArray(200_000) { (it * 31).toByte() }
        assertArrayEquals(plain, VaultCrypto.open(key, VaultCrypto.seal(key, plain)))
    }

    @Test fun everySealUsesAFreshNonce() {
        val key = key()
        val a = VaultCrypto.seal(key, "same".toByteArray())
        val b = VaultCrypto.seal(key, "same".toByteArray())
        assertFalse(a.contentEquals(b))
    }

    @Test fun tamperingAndWrongKeysAreRejected() {
        val key = key()
        val sealed = VaultCrypto.seal(key, "secret".toByteArray())
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertThrows(Exception::class.java) { VaultCrypto.open(key, sealed) }
        assertThrows(Exception::class.java) { VaultCrypto.open(key(), VaultCrypto.seal(key, "secret".toByteArray())) }
    }
}
