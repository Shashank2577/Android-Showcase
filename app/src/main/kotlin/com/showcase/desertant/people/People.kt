package com.showcase.desertant.people

import android.content.Context
import com.showcase.desertant.app
import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.VoiceSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Linking voices to people: every confirmation also teaches the person's voice. */
class People(context: Context) {
    private val db = context.app.db

    /** Say that meeting speaker [speakerId] is [profileId], and learn their voice from it. */
    suspend fun assign(speakerId: Long, profileId: Long) {
        val speaker = db.speakers().get(speakerId) ?: return
        db.speakers().assign(speakerId, profileId)
        speaker.signatureJson?.let { learn(profileId, decode(it)) }
        refreshSuggestions()
    }

    suspend fun unassign(speakerId: Long) {
        db.speakers().assign(speakerId, null)
        refreshSuggestions()
    }

    suspend fun dismissSuggestion(speakerId: Long) = db.speakers().dismissSuggestion(speakerId)

    /** Create a person from a meeting speaker. */
    suspend fun createFromSpeaker(speakerId: Long, name: String, isMe: Boolean): Long {
        val id = create(name, isMe)
        assign(speakerId, id)
        return id
    }

    suspend fun create(name: String, isMe: Boolean): Long {
        val existing = db.profiles().allNow()
        if (isMe) existing.filter { it.isMe }.forEach { db.profiles().update(it.copy(isMe = false)) }
        return db.profiles().insert(ProfileEntity(name = name.trim(), colorIndex = existing.size, isMe = isMe))
    }

    /** Teach a voice from a dedicated sample (16 kHz mono). Returns false if too little speech was heard. */
    suspend fun enroll(profileId: Long, samples: FloatArray): Boolean {
        val signature = withContext(Dispatchers.Default) { VoiceSignatures.compute(samples) } ?: return false
        learn(profileId, signature)
        refreshSuggestions()
        return true
    }

    suspend fun rename(profileId: Long, name: String) {
        db.profiles().get(profileId)?.let { db.profiles().update(it.copy(name = name.trim())) }
    }

    suspend fun delete(profileId: Long) {
        db.profiles().delete(profileId)
        refreshSuggestions()
    }

    private suspend fun learn(profileId: Long, signature: VoiceSignature) {
        val p = db.profiles().get(profileId) ?: return
        val old = p.signatureJson?.let(::decode)
        val merged = VoiceSignatures.merge(old, p.signatureCount, signature)
        db.profiles().update(p.copy(signatureJson = StoreJson.encodeToString(VoiceSignature.serializer(), merged), signatureCount = p.signatureCount + 1))
    }

    /** Re-score every unlinked speaker against the current people. */
    private suspend fun refreshSuggestions() = withContext(Dispatchers.Default) {
        val profiles = db.profiles().allNow()
        val speakers = db.speakers().unlinked()
        val signatures = speakers.mapNotNull { s -> s.signatureJson?.let { s to decode(it) } }
        val matcher = ProfileMatcher(profiles, signatures.map { it.second })
        signatures.forEach { (s, sig) ->
            val best = matcher.best(sig)
            // Once the user has said "that's not them", don't nag again.
            if (s.dismissed) return@forEach
            db.speakers().suggest(s.id, best?.first, best?.second)
        }
    }

    private fun decode(json: String) = StoreJson.decodeFromString(VoiceSignature.serializer(), json)
}
