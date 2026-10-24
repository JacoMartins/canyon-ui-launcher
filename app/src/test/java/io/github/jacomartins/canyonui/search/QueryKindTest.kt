package io.github.jacomartins.canyonui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryKindTest {

    @Test fun phoneNumbers() {
        assertEquals("+55 11 91234-5678", QueryKind.phone("+55 11 91234-5678"))
        assertEquals("(11) 3456-7890", QueryKind.phone("(11) 3456-7890"))
        assertEquals("190", QueryKind.phone("190"))
        assertNull(QueryKind.phone("12"))
        assertNull(QueryKind.phone("12-4"))
        assertNull(QueryKind.phone("camera"))
    }

    @Test fun emails() {
        assertEquals("ana@example.com", QueryKind.email(" ana@example.com "))
        assertNull(QueryKind.email("ana@example"))
        assertNull(QueryKind.email("ana example.com"))
    }

    @Test fun links() {
        assertEquals("https://example.com", QueryKind.url("example.com"))
        assertEquals("https://github.com/x/y", QueryKind.url("github.com/x/y"))
        assertEquals("http://a.org", QueryKind.url("http://a.org"))
        assertNull(QueryKind.url("2.5"))
        assertNull(QueryKind.url("v1.2"))
        assertNull(QueryKind.url("hello world.com"))
        assertNull(QueryKind.url("ana@example.com"))
        assertNull(QueryKind.url("camera"))
    }
}
