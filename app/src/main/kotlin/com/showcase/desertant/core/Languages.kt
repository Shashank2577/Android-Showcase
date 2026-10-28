package com.showcase.desertant.core

import java.util.Locale

/** Human-readable name for an ISO 639 code, falling back to the code itself. */
fun languageName(code: String?): String {
    if (code.isNullOrBlank()) return "Unknown"
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else "$name ($code)"
}
