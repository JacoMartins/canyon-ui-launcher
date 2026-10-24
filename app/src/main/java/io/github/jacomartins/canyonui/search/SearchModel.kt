package io.github.jacomartins.canyonui.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import android.provider.ContactsContract
import android.app.SearchManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toBitmap
import io.github.jacomartins.canyonui.model.AppSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An app shortcut ("New message", "Scan QR code") of an installed app. */
class ShortcutResult(val info: ShortcutInfo, val label: String, val appLabel: String, val icon: ImageBitmap?)

/** A contact, opened in the phone's contacts app. */
class ContactResult(val id: Long, val lookupKey: String, val name: String, val photo: ImageBitmap?) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

/** Something the search hands to another app: the query on the web, a number to call... */
class HandOff(val kind: Kind, val value: String) {
    enum class Kind { CALL, EMAIL, LINK, WEB, STORE, MAPS }

    fun intent(): Intent = when (kind) {
        Kind.CALL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null))
        Kind.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", value, null))
        Kind.LINK -> Intent(Intent.ACTION_VIEW, value.toUri())
        Kind.WEB -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, value)
        Kind.STORE -> Intent(Intent.ACTION_VIEW, ("market://search?q=" + Uri.encode(value)).toUri())
        Kind.MAPS -> Intent(Intent.ACTION_VIEW, ("geo:0,0?q=" + Uri.encode(value)).toUri())
    }
}

/** Everything but apps for one query (apps are filtered by the drawer, on every key press). */
class SearchExtras(
    val query: String = "",
    val calc: String? = null,
    val shortcuts: List<ShortcutResult> = emptyList(),
    val contacts: List<ContactResult> = emptyList(),
    val settings: List<SettingPage> = emptyList(),
    /** Call, email or open a link: what the query itself is. Shown before the other results. */
    val direct: List<HandOff> = emptyList(),
    /** Web, app store and maps for the query. */
    val handOffs: List<HandOff> = emptyList(),
) {
    val isEmpty get() = calc == null && shortcuts.isEmpty() && contacts.isEmpty() && settings.isEmpty() && direct.isEmpty()
}

/**
 * Canyon search's sources besides apps, all on the phone: app shortcuts, contacts (once allowed),
 * settings pages, the calculator and hand-offs to other apps. Also keeps recent searches.
 */
@OptIn(FlowPreview::class)
class SearchModel(
    context: Context,
    scope: CoroutineScope,
    query: Flow<String>,
    /** Labels of the apps shown in the drawer, by package (hidden apps left out). */
    private val appLabels: () -> Map<String, String>,
) {
    private val app = context.applicationContext
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val prefs = app.getSharedPreferences("search", Context.MODE_PRIVATE)
    private val iconPx = (40 * app.resources.displayMetrics.density).toInt()

    private val _recents = MutableStateFlow(loadRecents())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    /** Contacts may be read (the permission is granted). Re-checked when search opens. */
    private val contactsAllowed = MutableStateFlow(hasContacts())
    private val _askContacts = MutableStateFlow(false)
    /** Offer to allow contacts: not yet granted and not turned down with "Not now". */
    val askContacts: StateFlow<Boolean> = _askContacts.asStateFlow()

    @Volatile private var shortcuts: List<ShortcutInfo>? = null

    val extras: StateFlow<SearchExtras> = combine(
        query.map { it.trim() }.distinctUntilChanged().debounce(EXTRAS_DEBOUNCE_MS),
        contactsAllowed,
    ) { q, contacts -> if (q.isEmpty()) SearchExtras() else build(q, contacts) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, SearchExtras())

    /** Search is opening: shortcuts and the contacts permission may have changed since last time. */
    fun refresh() {
        shortcuts = null
        contactsAllowed.value = hasContacts()
        _askContacts.value = !contactsAllowed.value && !prefs.getBoolean(KEY_NO_CONTACTS, false)
    }

    fun contactsDeclined() {
        prefs.edit { putBoolean(KEY_NO_CONTACTS, true) }
        _askContacts.value = false
    }

    /** A result was opened for [q]: it goes to the top of the recent searches. */
    fun remember(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        val list = (listOf(t) + _recents.value.filterNot { it.equals(t, ignoreCase = true) }).take(MAX_RECENTS)
        _recents.value = list
        prefs.edit { putString(KEY_RECENTS, list.joinToString(SEP)) }
    }

    fun clearRecents() {
        _recents.value = emptyList()
        prefs.edit { remove(KEY_RECENTS) }
    }

    private fun loadRecents(): List<String> =
        prefs.getString(KEY_RECENTS, null)?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()

    private fun hasContacts() = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun build(q: String, contacts: Boolean): SearchExtras {
        val calc = Calc.evaluate(q)?.let { Calc.format(it) }
        val direct = buildList {
            QueryKind.phone(q)?.let { add(HandOff(HandOff.Kind.CALL, it)) }
            QueryKind.email(q)?.let { add(HandOff(HandOff.Kind.EMAIL, it)) }
            QueryKind.url(q)?.let { add(HandOff(HandOff.Kind.LINK, it)) }
        }
        val handOffs = listOf(HandOff.Kind.WEB, HandOff.Kind.STORE, HandOff.Kind.MAPS).map { HandOff(it, q) }
        return SearchExtras(
            query = q,
            calc = calc,
            shortcuts = if (calc != null) emptyList() else findShortcuts(q),
            contacts = if (contacts && calc == null) findContacts(q) else emptyList(),
            settings = if (calc != null) emptyList() else findSettings(q),
            direct = direct,
            handOffs = handOffs,
        )
    }

    private fun findSettings(q: String): List<SettingPage> {
        val nq = AppSearch.normalize(q)
        return SettingsCatalog.pages
            .mapNotNull { p ->
                val label = app.getString(p.label)
                val byLabel = AppSearch.rank(label, nq)
                val byWord = p.keywords.split(' ').minOfOrNull { k -> AppSearch.rank(k, nq).let { if (it < 0) Int.MAX_VALUE else it + 3 } } ?: Int.MAX_VALUE
                val r = if (byLabel >= 0) byLabel else byWord
                if (r == Int.MAX_VALUE) null else r to p
            }
            .sortedBy { it.first }
            .take(MAX_SETTINGS)
            .map { it.second }
    }

    /** Shortcuts need the launcher to be the default home app; otherwise there are none. */
    private fun allShortcuts(): List<ShortcutInfo> {
        shortcuts?.let { return it }
        val list = try {
            if (!launcherApps.hasShortcutHostPermission()) emptyList()
            else {
                val query = LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
                launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty().filter { it.isEnabled }
            }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: IllegalStateException) {
            emptyList()
        }
        shortcuts = list
        return list
    }

    private fun findShortcuts(q: String): List<ShortcutResult> {
        val nq = AppSearch.normalize(q)
        val labels = appLabels()
        return allShortcuts()
            .mapNotNull { s ->
                val appLabel = labels[s.`package`] ?: return@mapNotNull null
                val label = (s.shortLabel ?: s.longLabel)?.toString() ?: return@mapNotNull null
                val r = AppSearch.rank(label, nq).takeIf { it >= 0 }
                    ?: s.longLabel?.toString()?.let { AppSearch.rank(it, nq) }?.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                Triple(r, s, label to appLabel)
            }
            .sortedBy { it.first }
            .distinctBy { it.second.`package` + "/" + it.second.id }
            .take(MAX_SHORTCUTS)
            .map { (_, s, l) ->
                val icon = try {
                    launcherApps.getShortcutIconDrawable(s, app.resources.displayMetrics.densityDpi)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                ShortcutResult(s, l.first, l.second, icon)
            }
    }

    private fun findContacts(q: String): List<ContactResult> {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        val out = ArrayList<ContactResult>()
        try {
            app.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < MAX_CONTACTS) {
                    val name = c.getString(2) ?: continue
                    val photo = c.getString(3)?.let { loadPhoto(it.toUri()) }
                    out += ContactResult(c.getLong(0), c.getString(1) ?: continue, name, photo)
                }
            }
        } catch (e: SecurityException) {
            contactsAllowed.value = false
        } catch (e: IllegalArgumentException) {
            // A provider that does not support filtering: no contacts.
        }
        return out
    }

    private fun loadPhoto(uri: Uri): ImageBitmap? = try {
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val EXTRAS_DEBOUNCE_MS = 60L
        const val MAX_RECENTS = 8
        const val MAX_SETTINGS = 4
        const val MAX_SHORTCUTS = 6
        const val MAX_CONTACTS = 5
        const val KEY_RECENTS = "recents"
        const val KEY_NO_CONTACTS = "contacts_declined"
        const val SEP = "\n"
    }
}
