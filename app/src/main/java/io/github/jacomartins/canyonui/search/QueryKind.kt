package io.github.jacomartins.canyonui.search

/** What a search text looks like, for the hand-off results (call, email, open link). Pure Kotlin. */
object QueryKind {

    private val PHONE = Regex("""^\+?[0-9(][0-9 ()./-]+[0-9]$""")
    private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[A-Za-z]{2,}$""")
    private val URL = Regex("""^(https?://)?([A-Za-z0-9-]+\.)+[A-Za-z]{2,}(:[0-9]+)?(/\S*)?$""", RegexOption.IGNORE_CASE)

    /** A phone number to dial: digits with the usual separators, at least 3 digits. */
    fun phone(q: String): String? {
        val t = q.trim()
        if (!PHONE.matches(t) || t.count { it.isDigit() } < 3) return null
        // A sum such as "12-4" is a calculation, not a number.
        if (Calc.evaluate(t) != null && !t.startsWith("+") && ' ' !in t && '(' !in t) return null
        return t
    }

    fun email(q: String): String? = q.trim().takeIf { EMAIL.matches(it) }

    /** A web address to open, with "https://" added when missing. */
    fun url(q: String): String? {
        val t = q.trim()
        if (' ' in t || '@' in t || !URL.matches(t)) return null
        // "2.5" or "v1.2" are not addresses: the last label must be letters.
        val host = t.substringAfter("://").substringBefore('/').substringBefore(':')
        if (host.substringAfterLast('.').any { !it.isLetter() }) return null
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }
}
