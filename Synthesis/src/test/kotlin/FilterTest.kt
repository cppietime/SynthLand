import com.funguscow.synthland.synth.*
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterTest {

    @Test
    fun butterLowpass2ZPK() {
        val expectedPoles = arrayOf(Complex(0.9556991, .04241989), Complex(0.9556991, -.04241989))
        val expectedZeros = ComplexArray(2) {-Complex.ONE}
        val expectedGain = 9.4050433119e-4

        val (poles, zeros, gain) = IirFilter.digitalPZK(Prototypes::butterworthPZK, 2, FilterType.LOWPASS, 2 * PI * 440 / 44100)

        assert(expectedPoles.matchUnordered(poles))
        assert(expectedZeros.matchUnordered(zeros))
        assertEquals(gain, expectedGain, 1e-9)
    }

    @Test
    fun butterLowpass5ZPK() {
        val expectedPoles = arrayOf(
            Complex(0.97908124, 0.05845053),
            Complex(0.97908124, -0.05845053),
            Complex(.94989177, 0.03504743),
            Complex(.94989177, -0.03504743),
            Complex(0.93919657)
        )
        val expectedZeros = ComplexArray(5) {-Complex.ONE}
        val expectedGain = 2.73823151168e-8

        val (poles, zeros, gain) = IirFilter.digitalPZK(Prototypes::butterworthPZK, 5, FilterType.LOWPASS, 2 * PI * 440 / 44100)

        assert(expectedPoles.matchUnordered(poles))
        assert(expectedZeros.matchUnordered(zeros))
        assertEquals(gain, expectedGain, 1e-9)
    }

    @Test
    fun butterHighpass2ZPK() {
        val expectedPoles = arrayOf(
            Complex(0.9556991, .04241989),
            Complex(0.9556991, -.04241989),
        )
        val expectedZeros = ComplexArray(2) {Complex.ONE}
        val expectedGain = 0.956639602

        val (poles, zeros, gain) = IirFilter.digitalPZK(Prototypes::butterworthPZK, 2, FilterType.HIGHPASS, 2 * PI * 440 / 44100)

        assert(expectedPoles.matchUnordered(poles))
        assert(expectedZeros.matchUnordered(zeros))
        assertEquals(gain, expectedGain, 1e-9)
    }

    @Test
    fun butterBandpass2ZPK() {
        val expectedPoles = arrayOf(
            Complex(0.96988298, 0.06326052),
            Complex(0.96988298, -0.06326052),
            Complex(0.91744194, 0.15391718),
            Complex(0.91744194, -0.15391718),
        )
        val expectedZeros = arrayOf(
            Complex.ONE, Complex.ONE,
            -Complex.ONE, -Complex.ONE
        )
        val expectedGain = 4.603998475022e-3

        val (poles, zeros, gain) = IirFilter.digitalPZK(Prototypes::butterworthPZK, 2, FilterType.BANDPASS, 2 * PI * 440 / 44100, 2 * PI * 1440 / 44100)

        assert(expectedPoles.matchUnordered(poles))
        assert(expectedZeros.matchUnordered(zeros))
        assertEquals(gain, expectedGain, 1e-9)
    }

    @Test
    fun rootsToPoly() {
        val expectedDen = doubleArrayOf(1.0, -1.9113982, 0.91516021)
        val expectedNum = doubleArrayOf(9.405e-4, 1.88101e-3, 9.405e-4)
        val (poles, zeros, gain) = IirFilter.digitalPZK(Prototypes::butterworthPZK, 2, FilterType.LOWPASS, 2 * PI * 440 / 44100)
        val filter = IirFilter.fromPZK(poles, zeros, gain)
        (filter.denominator zip expectedDen).forEach {(a, b) -> assertEquals(a, b, 1e-6) }
        (filter.numerator zip expectedNum).forEach {(a, b) -> assertEquals(a, b, 1e-6) }
    }

    @Test
    fun rootsToBiquads() {
        val expectedDen = doubleArrayOf(1.0, -1.88686076, 8.90574486e-1, 1.0, -1.949337, 9.53173685e-1).apply(DoubleArray::sort)
        val expectedNum = doubleArrayOf(8.90524834e-7, 1.78104967e-6, 8.90524834e-7, 1.0, 2.0, 1.0).apply(DoubleArray::sort)
        val (poles, zeros, gain) = IirFilter.digitalPZK(Prototypes::butterworthPZK, 4, FilterType.LOWPASS, 2 * PI * 440 / 44100)
        val filters = BiquadFilter.fromPZKs(poles, zeros, gain)
        val numerators = doubleArrayOf(*filters[0].numerator, *filters[1].numerator).apply(DoubleArray::sort)
        val denominators = doubleArrayOf(*filters[0].denominator, *filters[1].denominator).apply(DoubleArray::sort)
        (numerators zip expectedNum).forEach{(a, b) -> assertEquals(a, b, 1e-6) }
        (denominators zip expectedDen).forEach{(a, b) -> assertEquals(a, b, 1e-6) }
    }

    @Test
    fun firRect() {
        val expected = doubleArrayOf(0.18390088, 0.31609912, 0.31609912, 0.18390088)
        val bands = doubleArrayOf(0.0, 440.0 / 44100 * 2, 1000.0 / 44100 * 2, 8000.0 / 44100 * 2)
        val fir = FirFilter.windowed(4, bands)
        (expected zip fir.coefficients).forEach {(a, b) -> assertEquals(a, b, 1e-6) }
    }

    @Test
    fun hannWindow() {
        val expected = doubleArrayOf(0.0, 0.11697778, 0.41317591, 0.75, 0.96984631, 0.96984631, 0.75, 0.41317591, 0.11697778, 0.0)
        val n = 10
        val x = (0 until n).toList().toIntArray()
        val window = x.map{WindowFuncs.hannWindow(it, n)}.toDoubleArray()
        (expected zip window).forEach {(a, b) -> assertEquals(a, b, 1e-6) }
    }

}