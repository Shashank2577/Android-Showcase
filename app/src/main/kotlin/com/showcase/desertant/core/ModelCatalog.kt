package com.showcase.desertant.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

enum class ModelCategory(val title: String) {
    AUDIO("Audio"),
    VISION("Vision"),
    TEXT("Text"),
    INK("Ink"),
}

/** Every Desert Ant model that ships an Android SDK. */
enum class ModelKind(
    val title: String,
    val tagline: String,
    val description: String,
    val category: ModelCategory,
    /** Approximate download size, from the model docs. */
    val sizeLabel: String,
    /** Tongue ships its weights inside the artifact and never downloads. */
    val bundled: Boolean,
    val icon: ImageVector,
    val artifact: String,
) {
    VOZ(
        "Voz", "Speech to text with word timestamps",
        "On-device speech recognition in 25 languages. Returns the full transcript and every word with its start and end time (80 ms resolution).",
        ModelCategory.AUDIO, "337 MB", false, Icons.Outlined.Mic, "ai.desertant:voz",
    ),
    EAR(
        "Ear", "Spoken language identification",
        "Names the language of a recording across 102 languages, from up to three 30 s windows, so you can route audio to the right recognizer.",
        ModelCategory.AUDIO, "~10 MB", false, Icons.Outlined.Hearing, "ai.desertant:ear",
    ),
    WHO(
        "Who", "Speaker diarization",
        "Labels who spoke when: per-speaker turns with timestamps, ready to merge with a transcript.",
        ModelCategory.AUDIO, "32 MB", false, Icons.Outlined.PeopleAlt, "ai.desertant:who",
    ),
    CLEAR(
        "Clear", "Speech enhancement",
        "Denoises, dereverbs and loudness-normalizes speech to podcast-ready 48 kHz, with streaming-platform loudness presets.",
        ModelCategory.AUDIO, "9 MB", false, Icons.Outlined.GraphicEq, "ai.desertant:clear",
    ),
    EYE(
        "Eye", "Photo understanding",
        "Scenes and 600+ tags, aesthetic/keeper/cover scores, faces with expressions and pose, people, pets, smart crops, sharpness, plus duplicate grouping and best-shot picking across a library.",
        ModelCategory.VISION, "66 MB", false, Icons.Outlined.PhotoCamera, "ai.desertant:eye",
    ),
    MODERATOR(
        "Moderator", "NSFW image detection",
        "Flags nudity before upload or display, with per-region scores, a policy switch and speed/accuracy trade-off. Trained only on licensed and synthetic data.",
        ModelCategory.VISION, "10 MB", false, Icons.Outlined.VisibilityOff, "ai.desertant:moderator",
    ),
    REDACT(
        "Redact", "PII detection & redaction",
        "Finds names, addresses, emails, phone numbers, cards, IDs and more in 27 languages and swaps them for restorable placeholders, so text can go to an LLM and come back.",
        ModelCategory.TEXT, "12 MB", false, Icons.Outlined.Shield, "ai.desertant:redact",
    ),
    GIST(
        "Gist", "Topic tagging",
        "Multi-label topics from a 36-topic taxonomy across 101 languages, plus channel-level roll-ups over many posts.",
        ModelCategory.TEXT, "15 MB", false, Icons.Outlined.Category, "ai.desertant:gist",
    ),
    EMO(
        "Emo", "Emoji suggestion",
        "Suggests emoji for a phrase as you type, in 22 languages, with skin-tone preference.",
        ModelCategory.TEXT, "10 MB", false, Icons.Outlined.EmojiEmotions, "ai.desertant:emo",
    ),
    TONGUE(
        "Tongue", "Text language identification",
        "Identifies the language of a few words across 84 languages. Pure Kotlin, weights bundled: no download, no native code.",
        ModelCategory.TEXT, "2 MB (bundled)", true, Icons.Outlined.Translate, "ai.desertant:tongue",
    ),
    SHAPES(
        "Shapes", "Sketch to shape",
        "Turns a hand-drawn stroke into a clean line, curve, arrow, rectangle, ellipse, polygon, star, heart or cloud, and recognizes scribbles as an erase gesture.",
        ModelCategory.INK, "1 MB", false, Icons.Outlined.Gesture, "ai.desertant:shapes",
    );

    val route: String get() = "model/${name.lowercase()}"
}

/** Composite demos that chain several models together. */
enum class Pipeline(
    val title: String,
    val description: String,
    val models: List<ModelKind>,
    val icon: ImageVector,
) {
    MEETING(
        "Private meeting notes",
        "Record or pick a conversation: clean it up, check the language, transcribe it, label who said what, strip PII and tag the topics — all on the device.",
        listOf(ModelKind.CLEAR, ModelKind.EAR, ModelKind.VOZ, ModelKind.WHO, ModelKind.REDACT, ModelKind.GIST),
        Icons.Outlined.AutoFixHigh,
    ),
    MESSAGE(
        "Smart compose",
        "Type a message: detect its language, redact it before it leaves the phone, tag its topics and suggest emoji.",
        listOf(ModelKind.TONGUE, ModelKind.REDACT, ModelKind.GIST, ModelKind.EMO),
        Icons.Outlined.AutoFixHigh,
    );

    val route: String get() = "pipeline/${name.lowercase()}"
}

/** Languages Voz is trained on (mirrors `Voz.supportedLanguages` in the Swift SDK). */
val VOZ_LANGUAGES: Set<String> = setOf(
    "bg", "cs", "da", "de", "el", "en", "es", "et", "fi",
    "fr", "hr", "hu", "it", "lt", "lv", "mt", "nl", "pl",
    "pt", "ro", "ru", "sk", "sl", "sv", "uk",
)
