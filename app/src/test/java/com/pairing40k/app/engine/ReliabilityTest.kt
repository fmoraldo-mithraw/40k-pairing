package com.pairing40k.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityTest {

    @Test
    fun grosEcartsGardes_scoresSerresAttenues() {
        for (v in listOf(0.0, 3.0, 5.0, 10.0, 15.0, 17.0, 20.0)) assertEquals(v, Reliability.adjust(v, 20.0, 0.4), 1e-9)
        val a12 = Reliability.adjust(12.0, 20.0, 0.4)
        assertTrue("12 doit se rapprocher de 10 : $a12", a12 > 10.0 && a12 < 11.5)
        assertEquals(20.0 - a12, Reliability.adjust(8.0, 20.0, 0.4), 1e-9)
        assertEquals(12.0, Reliability.adjust(12.0, 20.0, 1.0), 1e-9)
    }

    @Test
    fun ordreConserve() {
        var prev = -1.0
        var v = 0.0
        while (v <= 20.0) {
            val a = Reliability.adjust(v, 20.0, 0.15)
            assertTrue(a >= prev)
            prev = a
            v += 0.25
        }
    }
}
