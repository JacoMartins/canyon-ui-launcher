package io.github.jacomartins.canyonui.model

import java.text.Normalizer

/**
 * Sorts apps into home screen categories, offline. A normal launcher has no app store data, so
 * the sources are, in order:
 *
 * 1. The bundled table (`assets/app_categories.txt`): exact package names, then package
 *    families (a publisher's prefix, longest first).
 * 2. A game declares itself well (`android:appCategory`), so that comes next.
 * 3. A guess from the words in the app's name and package name, in English and Portuguese,
 *    weighed per category (a word in the name counts more than one in the package).
 * 4. Whatever else the app declares (Android 8+), passed in as [declared] using the
 *    `ApplicationInfo.CATEGORY_*` values. "Productivity" is declared by many apps that are not
 *    (carriers, government apps), which is why it comes after the guess.
 *
 * Apps that none of these place return null: they go to Others.
 */
object AppCategories {

    /** Bump when the table or the rules change, so category folders are sorted again once. */
    const val VERSION = 2

    /** The table, in the app's assets. */
    const val FILE = "app_categories.txt"

    enum class Category(val key: String) {
        COMMUNICATION("communication"),
        ENTERTAINMENT("entertainment"),
        GAMES("games"),
        CREATION("creation"),
        PRODUCTIVITY("productivity"),
        EDUCATION_AI("education_ai"),
        TRIP("trip"),
        WELLNESS("wellness"),
        FINANCE("finance"),
        SHOPPING("shopping"),
        FOOD("food"),
        NEWS("news"),
        SERVICES("services"),
        TOOLS("tools"),

        /** Where apps without a category go when organizing; never returned by [categorize]. */
        OTHERS("others"),
        ;

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
        }
    }

    // ApplicationInfo.CATEGORY_* values (stable platform constants).
    private const val GAME = 0
    private const val AUDIO = 1
    private const val VIDEO = 2
    private const val IMAGE = 3
    private const val SOCIAL = 4
    private const val NEWS_DECL = 5
    private const val MAPS = 6
    private const val PRODUCTIVITY_DECL = 7
    private const val ACCESSIBILITY = 8

    // A word found in the app's name weighs more than one in its package name, which often
    // carries a company name instead. One package word alone is still enough to decide.
    private const val NAME_WEIGHT = 3
    private const val PACKAGE_WEIGHT = 2
    private const val MIN_SCORE = 2

    /** Package words that say nothing about the app. */
    private val STOP = setOf(
        "com", "br", "org", "net", "io", "co", "me", "de", "app", "apps", "android", "mobile", "client",
        "pro", "free", "lite", "prod", "production", "release", "www", "gp", "global", "intl", "beta", "dev",
    )

    /** The parsed table; see the header of [FILE] for its format. */
    class Table(
        val exact: Map<String, Category>,
        /** Longest prefix first, so a more specific family wins. */
        val prefixes: List<Pair<String, Category>>,
        val words: Map<String, List<Category>>,
        /** (stem, anywhere in a package word, category). */
        val stems: List<Triple<String, Boolean, Category>>,
        val phrases: List<Pair<String, Category>>,
    )

    @Volatile private var table: Table? = null

    /** Reads the table once; [read] returns the text of [FILE]. Cheap after the first call. */
    fun load(read: () -> String) {
        if (table != null) return
        synchronized(this) {
            if (table == null) table = parse(read())
        }
    }

    fun parse(text: String): Table {
        val exact = HashMap<String, Category>()
        val prefixes = ArrayList<Pair<String, Category>>()
        val words = HashMap<String, MutableList<Category>>()
        val stems = ArrayList<Triple<String, Boolean, Category>>()
        val phrases = ArrayList<Pair<String, Category>>()
        var current: Category? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                current = Category.fromKey(line.substring(1, line.length - 1))
                continue
            }
            val cat = current ?: continue
            when {
                line.startsWith("=") -> {
                    val w = plain(line.substring(1))
                    when {
                        w.startsWith("*") && w.endsWith("*") -> stems += Triple(w.trim('*'), true, cat)
                        w.endsWith("*") -> stems += Triple(w.dropLast(1), false, cat)
                        ' ' in w -> phrases += " $w " to cat
                        else -> words.getOrPut(w) { ArrayList() } += cat
                    }
                }
                line.endsWith("*") -> prefixes += line.dropLast(1) to cat
                // The first line for a package decides (a test keeps the file free of repeats).
                else -> exact.putIfAbsent(line, cat)
            }
        }
        prefixes.sortByDescending { it.first.length }
        return Table(exact, prefixes, words, stems, phrases)
    }

    fun categorize(packageName: String, label: String, declared: Int): Category? {
        val t = table
        if (t != null) {
            t.exact[packageName]?.let { return it }
            t.prefixes.firstOrNull { packageName.startsWith(it.first) }?.let { return it.second }
        }
        if (declared == GAME) return Category.GAMES
        if (t != null) guess(t, packageName, label)?.let { return it }
        return when (declared) {
            AUDIO, VIDEO -> Category.ENTERTAINMENT
            IMAGE -> Category.CREATION
            SOCIAL -> Category.COMMUNICATION
            NEWS_DECL -> Category.NEWS
            MAPS -> Category.TRIP
            PRODUCTIVITY_DECL -> Category.PRODUCTIVITY
            ACCESSIBILITY -> Category.TOOLS
            else -> null
        }
    }

    /** Scores every category by the words of the name and the package; the best one wins. */
    private fun guess(t: Table, packageName: String, label: String): Category? {
        val scores = IntArray(Category.entries.size)
        val nameWords = words(label)
        val packageWords = words(packageName).filter { it !in STOP }

        for (w in nameWords.toSet()) t.words[w]?.forEach { scores[it.ordinal] += NAME_WEIGHT }
        for (w in packageWords.toSet()) t.words[w]?.forEach { scores[it.ordinal] += PACKAGE_WEIGHT }
        for ((stem, anywhere, cat) in t.stems) {
            if (nameWords.any { it.startsWith(stem) }) scores[cat.ordinal] += NAME_WEIGHT
            if (packageWords.any { if (anywhere) stem in it else it.startsWith(stem) }) scores[cat.ordinal] += PACKAGE_WEIGHT
        }
        val text = nameWords.joinToString(" ", " ", " ")
        for ((phrase, cat) in t.phrases) if (phrase in text) scores[cat.ordinal] += NAME_WEIGHT

        // Ties go to the category listed first.
        var best = -1
        for (i in scores.indices) if (scores[i] >= MIN_SCORE && (best < 0 || scores[i] > scores[best])) best = i
        return if (best < 0) null else Category.entries[best]
    }

    /** Lower case, without accents ("Título" → "titulo"), so Portuguese names match plainly. */
    private fun plain(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    private val MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    // Where a joined name splits: "ChatGPT" → Chat|GPT, "zFont3" → z|Font|3.
    private val CAMEL = Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")

    /** The words of a name or package, each also split at case changes ("MyInsta" → myinsta, my, insta). */
    fun words(text: String): List<String> {
        val out = ArrayList<String>()
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "")
        for (raw in stripped.split(SEPARATORS)) {
            if (raw.isEmpty()) continue
            out += raw.lowercase()
            val parts = raw.split(CAMEL).filter { it.isNotEmpty() }
            if (parts.size > 1) parts.forEach { out += it.lowercase() }
        }
        return out
    }
}
