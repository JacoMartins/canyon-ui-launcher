package io.github.jacomartins.canyonui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutResolverTest {

    private val now = 1_000_000_000L
    private fun key(pkg: String, cls: String = "Main") = AppKey(pkg, "$pkg.$cls", 0)

    private fun item(id: Long, k: AppKey, page: Int, x: Int, y: Int, container: Int = Container.DESKTOP, missing: Long? = null) =
        StoredItem(id, container, page, x, y, 1, 1, k, missing)

    private fun resolve(
        items: List<StoredItem>,
        installed: Set<AppKey>,
        pages: Int = 1,
        columns: Int = 5,
        rows: Int = 6,
        dock: Int = 4,
    ) = LayoutResolver.resolve(
        items, pages, columns, rows, dock, installed,
        defaultActivityFor = { pkg, user -> installed.firstOrNull { it.packageName == pkg && it.userSerial == user } },
        now = now,
    )

    @Test fun installedItemsStayWhereTheyAre() {
        val a = key("a"); val b = key("b")
        val r = resolve(listOf(item(1, a, 0, 2, 3), item(2, b, 0, 0, 0, Container.DOCK)), setOf(a, b))
        assertTrue(r.updates.isEmpty())
        assertEquals(listOf(Cell(2, 3), Cell(0, 0)), r.visible.sortedBy { it.id }.map { Cell(it.x, it.y) })
    }

    @Test fun uninstalledAppBecomesHiddenGhost() {
        val a = key("a")
        val r = resolve(listOf(item(1, a, 0, 1, 1)), installed = emptySet())
        assertTrue(r.visible.isEmpty())
        assertEquals(listOf(ItemUpdate.MarkMissing(1, now)), r.updates)
    }

    @Test fun reinstalledAppReturnsToItsCell() {
        val a = key("a")
        val r = resolve(listOf(item(1, a, 0, 3, 4, missing = now - 1000)), setOf(a))
        assertEquals(Cell(3, 4), r.visible.single().let { Cell(it.x, it.y) })
        assertEquals(listOf(ItemUpdate.ClearMissing(1)), r.updates)
    }

    @Test fun ghostIsDeletedAfterRetention() {
        val a = key("a")
        val r = resolve(listOf(item(1, a, 0, 0, 0, missing = now - LayoutResolver.DEFAULT_GHOST_RETENTION_MS - 1)), emptySet())
        assertEquals(listOf(ItemUpdate.Delete(1)), r.updates)
    }

    @Test fun reinstalledGhostYieldsToItemThatTookItsCell() {
        val a = key("a"); val b = key("b")
        val items = listOf(
            item(1, a, 0, 0, 0, missing = now - 1000), // came back
            item(2, b, 0, 0, 0), // was placed there meanwhile
        )
        val r = resolve(items, setOf(a, b))
        val byId = r.visible.associateBy { it.id }
        assertEquals(Cell(0, 0), byId.getValue(2).let { Cell(it.x, it.y) })
        assertEquals(Cell(1, 0), byId.getValue(1).let { Cell(it.x, it.y) })
        assertTrue(ItemUpdate.Move(1, Container.DESKTOP, 0, 1, 0) in r.updates)
    }

    @Test fun renamedActivityIsRetargetedWithinPackage() {
        val old = key("a", "Old"); val new = key("a", "New")
        val r = resolve(listOf(item(1, old, 0, 0, 0)), setOf(new))
        assertEquals(new, r.visible.single().key)
        assertEquals(listOf(ItemUpdate.Retarget(1, new)), r.updates)
    }

    @Test fun shrinkingTheGridMovesItemsThatNoLongerFit() {
        val a = key("a")
        val r = resolve(listOf(item(1, a, 0, 5, 6)), setOf(a), columns = 4, rows = 5)
        assertEquals(Cell(0, 0), r.visible.single().let { Cell(it.x, it.y) })
        assertEquals(listOf(ItemUpdate.Move(1, Container.DESKTOP, 0, 0, 0)), r.updates)
    }

    @Test fun overflowGoesToNextPageThenNewPage() {
        val apps = (1..3).map { key("p$it") }
        // 1x1 grid, one page: the second item spills to a new page, the third to another.
        val items = apps.mapIndexed { i, k -> item(i + 1L, k, 0, 0, 0) }
        val r = resolve(items, apps.toSet(), pages = 1, columns = 1, rows = 1)
        assertEquals(listOf(0, 1, 2), r.visible.sortedBy { it.id }.map { it.pageIndex })
    }

    @Test fun dockOverflowMovesToDesktop() {
        val a = key("a"); val b = key("b")
        val items = listOf(item(1, a, 0, 0, 0, Container.DOCK), item(2, b, 0, 4, 0, Container.DOCK))
        val r = resolve(items, setOf(a, b), dock = 4)
        val moved = r.visible.single { it.id == 2L }
        assertEquals(Container.DESKTOP, moved.container)
    }

    @Test fun newItemSlotPrefersPageThenOthersThenNew() {
        val full = (0 until 4).map { PlacedItem(it.toLong(), Container.DESKTOP, 0, it % 2, it / 2, 1, 1, key("x$it")) }
        assertEquals(1 to Cell(0, 0), LayoutResolver.findSlotForNewItem(full, 2, 2, 2, preferredPage = 0))
        assertEquals(1 to Cell(0, 0), LayoutResolver.findSlotForNewItem(full, 1, 2, 2, preferredPage = 0))
        assertEquals(0 to Cell(0, 0), LayoutResolver.findSlotForNewItem(emptyList(), 0, 2, 2, preferredPage = 3))
    }

    @Test fun appKeyRoundTrips() {
        val k = AppKey("com.a", "com.a.Main", 10)
        assertEquals(k, AppKey.parse(k.toString()))
        assertEquals(null, AppKey.parse("garbage"))
    }

    @Test fun homeOnlyPlacesMissingAppsAfterLastUsedPageAndAddsPages() {
        val keep = PlacedItem(1, Container.DESKTOP, 1, 0, 0, 1, 1, key("kept"))
        val dock = PlacedItem(2, Container.DOCK, 0, 0, 0, 1, 1, key("dock"))
        val missing = (1..4).map { key("m$it") }
        // 2x2 pages; page 1 has one item, so three fit there and the fourth opens page 2.
        val out = LayoutResolver.placeMissingApps(listOf(keep, dock), pageCount = 2, columns = 2, rows = 2, missing = missing)
        assertEquals(listOf(1, 1, 1, 2), out.map { it.pageIndex })
        assertEquals(listOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(0, 0)), out.map { it.cell })
        assertEquals(missing, out.map { it.key })
    }

    @Test fun homeOnlyWithEmptyHomeStartsOnFirstPage() {
        val out = LayoutResolver.placeMissingApps(emptyList(), 0, 4, 6, listOf(key("a")))
        assertEquals(0, out.single().pageIndex)
        assertEquals(Cell(0, 0), out.single().cell)
    }

    private fun folderItem(id: Long, page: Int, x: Int, y: Int, span: Int = 1, container: Int = Container.DESKTOP) =
        StoredItem(id, container, page, x, y, span, span, null, null, ItemType.FOLDER)

    private fun child(id: Long, folder: Long, rank: Int, k: AppKey, missing: Long? = null) =
        StoredItem(id, Container.FOLDER, 0, rank, 0, 1, 1, k, missing, ItemType.APP, folder)

    @Test fun folderChildrenAreResolvedInRankOrder() {
        val a = key("a"); val b = key("b")
        val r = resolve(listOf(folderItem(10, 0, 1, 1), child(11, 10, 1, b), child(12, 10, 0, a)), setOf(a, b))
        assertEquals(listOf(10L), r.visible.map { it.id })
        assertEquals(listOf(12L, 11L), r.children.getValue(10).map { it.id })
        assertTrue(r.updates.isEmpty())
    }

    @Test fun folderWithOnlyMissingAppsIsHiddenNotDeleted() {
        val a = key("a")
        val r = resolve(listOf(folderItem(10, 0, 0, 0), child(11, 10, 0, a)), installed = emptySet())
        assertTrue(r.visible.isEmpty())
        assertEquals(listOf(ItemUpdate.MarkMissing(11, now)), r.updates)
    }

    @Test fun emptyFolderIsDeleted() {
        val r = resolve(listOf(folderItem(10, 0, 0, 0)), emptySet())
        assertEquals(listOf(ItemUpdate.Delete(10)), r.updates)
    }

    @Test fun orphanedAppMovesOntoThePage() {
        val a = key("a")
        val r = resolve(listOf(child(11, folder = 99, rank = 0, k = a)), setOf(a))
        assertEquals(Container.DESKTOP, r.visible.single().container)
        assertTrue(r.updates.single() is ItemUpdate.Move)
    }

    @Test fun largeFolderInDockMovesToDesktop() {
        val a = key("a")
        val r = resolve(listOf(folderItem(10, 0, 0, 0, span = 2, container = Container.DOCK), child(11, 10, 0, a)), setOf(a))
        assertEquals(Container.DESKTOP, r.visible.single().container)
    }

    // --- Widgets and stacks

    private fun widget(id: Long, x: Int, y: Int, sx: Int, sy: Int, container: Int = Container.DESKTOP, parent: Long = 0) =
        StoredItem(id, container, 0, x, y, sx, sy, null, null, ItemType.WIDGET, parent)

    private fun stack(id: Long, x: Int, y: Int, sx: Int, sy: Int) =
        StoredItem(id, Container.DESKTOP, 0, x, y, sx, sy, null, null, ItemType.STACK)

    @Test fun widgetKeepsItsCellsWithoutAnAppKey() {
        val r = resolve(listOf(widget(1, 0, 0, 4, 2)), emptySet(), columns = 4)
        assertEquals(PlacedItem(1, Container.DESKTOP, 0, 0, 0, 4, 2, null, ItemType.WIDGET), r.visible.single())
        assertTrue(r.updates.isEmpty())
    }

    @Test fun stackShowsItsWidgetsInRankOrder() {
        val r = resolve(listOf(stack(10, 0, 0, 2, 2), widget(11, 1, 0, 2, 2, Container.STACK, 10), widget(12, 0, 0, 2, 2, Container.STACK, 10)), emptySet())
        assertEquals(listOf(10L), r.visible.map { it.id })
        assertEquals(listOf(12L, 11L), r.children.getValue(10).map { it.id })
        assertEquals(Container.STACK, r.children.getValue(10).first().container)
    }

    @Test fun emptyStackIsDeleted() {
        val r = resolve(listOf(stack(10, 0, 0, 2, 2)), emptySet())
        assertEquals(listOf(ItemUpdate.Delete(10)), r.updates)
    }

    @Test fun widgetWhoseStackIsGoneMovesOntoThePageAtItsSize() {
        val r = resolve(listOf(widget(11, 0, 0, 2, 2, Container.STACK, parent = 99)), emptySet())
        val v = r.visible.single()
        assertEquals(Container.DESKTOP, v.container)
        assertEquals(2, v.spanX)
        assertEquals(2, v.spanY)
    }

    @Test fun widgetNeverStaysInTheDock() {
        val r = resolve(listOf(widget(1, 0, 0, 1, 1, Container.DOCK)), emptySet())
        assertEquals(Container.DESKTOP, r.visible.single().container)
    }
}
