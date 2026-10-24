package io.github.jacomartins.canyonui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {

    private fun calc(s: String) = Calc.evaluate(s)

    @Test fun basicOperatorsAndPrecedence() {
        assertEquals(14.0, calc("2+3*4")!!, 1e-9)
        assertEquals(20.0, calc("(2+3)*4")!!, 1e-9)
        assertEquals(2.5, calc("10 / 4")!!, 1e-9)
        assertEquals(-1.0, calc("2 - 3")!!, 1e-9)
    }

    @Test fun powerIsRightAssociative() {
        assertEquals(512.0, calc("2^3^2")!!, 1e-9)
    }

    @Test fun symbolsAndCommaDecimals() {
        assertEquals(12.0, calc("3×4")!!, 1e-9)
        assertEquals(3.0, calc("9÷3")!!, 1e-9)
        assertEquals(10.0, calc("2,5 * 4")!!, 1e-9)
        assertEquals(6.0, calc("2 x 3 =")!!, 1e-9)
    }

    @Test fun percent() {
        assertEquals(0.5, calc("50%")!!, 1e-9)
        assertEquals(20.0, calc("200*10%")!!, 1e-9)
    }

    @Test fun unaryMinus() {
        assertEquals(-6.0, calc("-2*3")!!, 1e-9)
        assertEquals(5.0, calc("2--3")!!, 1e-9)
    }

    @Test fun notACalculation() {
        assertNull(calc("42"))
        assertNull(calc("(42)"))
        assertNull(calc("whatsapp"))
        assertNull(calc("2+"))
        assertNull(calc("1/0"))
        assertNull(calc("(2+3"))
        assertNull(calc(""))
    }

    @Test fun formatting() {
        assertEquals("14", Calc.format(14.0))
        assertEquals("0.3333333333", Calc.format(1.0 / 3))
        assertEquals("0.3", Calc.format(0.1 + 0.2))
        assertEquals("-2.5", Calc.format(-2.5))
        assertEquals("1E+20", Calc.format(1e20))
    }
}
