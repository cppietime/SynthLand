import com.funguscow.synthland.synth.Complex
import com.funguscow.synthland.synth.fft
import com.funguscow.synthland.synth.ifft
import kotlin.test.Test

class FftTest {

    @Test
    fun singularTest() {
        val array = Array(1) { Complex.ONE}
        fft(array, array)
        println(array.joinToString())
    }

    @Test
    fun twoOnesTest() {
        val array = Array(2) { Complex.ONE}
        fft(array, array)
        println(array.joinToString())
    }

    @Test
    fun countTest() {
        val array = doubleArrayOf(1.0, 1.0, 0.0, 0.0).map(::Complex).toTypedArray()
        fft(array, array)
        println(array.joinToString())
    }

    @Test
    fun realTest() {
        val array = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val tran = fft(array)
        println(tran.joinToString())
    }

    @Test
    fun ifftTest() {
        val array = doubleArrayOf(1.0, 1.0, 0.0, 0.0).map(::Complex).toTypedArray()
        fft(array, array)
        ifft(array, array)
        println(array.joinToString())
    }

}