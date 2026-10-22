package com.example.homelauncher.model

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
}
