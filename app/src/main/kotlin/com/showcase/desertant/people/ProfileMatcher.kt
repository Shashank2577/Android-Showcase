package com.showcase.desertant.people

import com.showcase.desertant.data.ProfileEntity
import com.showcase.desertant.data.StoreJson
import com.showcase.desertant.data.VoiceSignature

/** Matches a voice against known people. Scores are 0..1; suggestions start at [THRESHOLD]. */
class ProfileMatcher(profiles: List<ProfileEntity>, others: List<VoiceSignature> = emptyList()) {
    private val known: List<Pair<Long, VoiceSignature>> = profiles.mapNotNull { p ->
        p.signatureJson?.let { json -> runCatching { StoreJson.decodeFromString(VoiceSignature.serializer(), json) }.getOrNull() }
            ?.let { p.id to it }
    }
    private val center = VoiceSignatures.center(known.map { it.second } + others)

    fun scores(signature: VoiceSignature): List<Pair<Long, Double>> =
        known.map { (id, sig) -> id to VoiceSignatures.similarity(signature, sig, center) }.sortedByDescending { it.second }

    /** The best match if it is confident and clearly ahead of the runner-up. */
    fun best(signature: VoiceSignature): Pair<Long, Double>? {
        val ranked = scores(signature)
        val top = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)?.second ?: 0.0
        return top.takeIf { it.second >= THRESHOLD && it.second - second >= 0.03 }
    }

    companion object {
        const val THRESHOLD = 0.72
    }
}
