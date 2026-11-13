package com.iptvwhiz.app.data

/** Default playlist names (pure, so they can be tested without Android). */
object PlaylistNames {
    const val DEFAULT = "My playlist"

    private val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    /**
     * A server name only reads well when it is a name: "10.0.2.2", "localhost" or "[::1]" tells the
     * owner nothing, so those get [DEFAULT] instead.
     */
    fun fromHost(host: String?): String? {
        val bare = host?.substringBefore(':')?.trim('[', ']')?.takeIf { it.isNotBlank() } ?: return null
        if (bare == "localhost" || ipv4.matches(bare) || ':' in host.trim('[', ']')) return null
        return host
    }

    /** [base], or "[base] 2", "[base] 3"… when another playlist already uses that name. */
    fun unique(base: String, existing: Collection<String>): String {
        val taken = existing.map { it.trim().lowercase() }.toSet()
        if (base.trim().lowercase() !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it.lowercase() !in taken }
    }
}
