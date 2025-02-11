import com.funguscow.synthland.synth.step
import kotlin.test.Test
import kotlin.test.assertEquals

class StepTest {

    @Test
    fun testInc() {
        val step = 1 .. 5 step { it * 2 }
        assertEquals(step.toList(), listOf(1, 2, 4))
    }

    @Test
    fun testIncBound() {
        val step = 1 .. 16 step {it * 2}
        assertEquals(step.toList(), listOf(1, 2, 4, 8, 16))
    }

    @Test
    fun testDec() {
        val step = 15 downTo 1 step { it / 2 }
        assertEquals(step.toList(), listOf(15, 7, 3, 1))
    }

}