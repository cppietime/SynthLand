import com.funguscow.synthland.synth.*
import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ComplexTest {

    @Test
    fun testConstructor() {
        val both = Complex(1.0, 2.0)
        assertEquals(both.real, 1.0)
        assertEquals(both.imag, 2.0)

        val real = Complex(-5.5)
        assertEquals(real.real, -5.5)
        assertEquals(real.imag, 0.0)
    }

    @Test
    fun testConsts() {
        assertEquals(Complex.ONE.real, 1.0)
        assertEquals(Complex.ONE.imag, 0.0)
        assertEquals(Complex.ZERO.real, 0.0)
        assertEquals(Complex.ZERO.imag, 0.0)
        assertEquals(Complex.I.real, 0.0)
        assertEquals(Complex.I.imag, 1.0)
    }

    @Test
    fun testProperties() {
        val c = Complex(3.0, 4.0)
        assertEquals(c.mag2, 25.0, 1e-9)
        assertEquals(c.mag, 5.0, 1e-9)
        assertEquals(c.arg, atan2(4.0, 3.0), 1e-9)
    }

    @Test
    fun testConjugate() {
        val a = Complex(2.0, 3.0)
        val conjugate = a.conjugate
        assertEquals(conjugate, Complex(2.0, -3.0))
        assertEquals(a.real, conjugate.real)
        assertEquals(a.imag, -conjugate.imag)
        assertEquals(a.mag, conjugate.mag)
        assertEquals(a.arg, -conjugate.arg)
        assertEquals(conjugate.conjugate, a)
    }

    @Test
    fun testReciprocal() {
        val a = Complex(4.0, 3.0)
        val reciprocal = a.reciprocal
        assertEquals(reciprocal, Complex(4.0 / 25, -3.0 / 25))
        assertEquals(a.mag, 1 / reciprocal.mag, 1e-9)
        assertEquals(a.arg, -reciprocal.arg)
        assertEquals(reciprocal.reciprocal, a)
    }

    @Test
    fun testUnary() {
        val a = Complex(1.0, 2.0)
        val b = -a
        assertEquals(b, Complex(-1.0, -2.0))
        assertEquals(a.real, -b.real)
        assertEquals(a.imag, -b.imag)
    }

    @Test
    fun testNumOps() {
        val a = Complex(1.0, 2.0)
        assertEquals(a + 2.0, Complex(3.0, 2.0))
        assertEquals(a - 3.0, Complex(-2.0, 2.0))
        assertEquals(a * 1.5, Complex(1.5, 3.0))
        assertEquals(a / 4, Complex(0.25, 0.5))

        assertEquals(2.0 + a, Complex(3.0, 2.0))
        assertEquals(0.5 - a, Complex(-0.5, -2.0))
        assertEquals(1.5 * a, Complex(1.5, 3.0))
        assertEquals(3.0 / a, 3.0 * a.reciprocal)
    }

    @Test
    fun testBinOps() {
        val a = Complex(3.0, -4.0)
        val b = Complex(-5.0, 13.0)

        assertEquals(a + b, Complex(-2.0, 9.0))
        assertEquals(a - b, Complex(8.0, -17.0))
        assertEquals(a * b, Complex(37.0, 59.0))
        assertEquals(a / b, Complex(-67.0 / 194, -19.0 / 194))
    }

    @Test
    fun testExp() {
        assertEquals(Complex.ZERO.exp(), Complex.ONE)
        assertEquals(Complex.ONE.exp(), Complex(E))
        assertEquals(Complex.I.exp(), Complex(cos(1.0), sin(1.0)))
        assertEquals(Complex(-3.0, 4.0).exp(), Complex(cos(4.0) / exp(3.0), sin(4.0) / exp(3.0)))
    }

    @Test
    fun testPoly() {
        val expectedCoefficients = arrayOf(
            Complex(1.0),
            Complex(-9.0),
            Complex(30.0),
            Complex(-42.0),
            Complex(20.0),
        )

        val roots = arrayOf(
            Complex.ONE,
            Complex(2.0),
            Complex(3.0, 1.0),
            Complex(3.0, -1.0),
        )
        val coefficients = roots.polyFromRoots()
        assert((coefficients zip expectedCoefficients).all {(a, b) -> a.near(b)})
    }

}