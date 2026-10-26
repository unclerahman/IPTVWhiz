package com.iptvwhiz.app.ui.common

import java.util.Locale

/** "AL" → "Albania" in the device language; unknown codes stay as they are. */
fun countryName(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).takeIf { it.isNotBlank() && it != code } ?: code
