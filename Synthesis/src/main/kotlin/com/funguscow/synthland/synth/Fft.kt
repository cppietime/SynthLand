package com.funguscow.synthland.synth

import com.funguscow.synthland.synth.Complex
import com.funguscow.synthland.synth.ComplexArray
import kotlin.math.PI

/**
 * Calculate the DFT of a power-of-2 size input array and store the result in an output array.
 * Input and output may be the same array.
 */
fun fft(input: ComplexArray, output: ComplexArray) {
    assert(input.size == output.size) {"Input and output sizes do not match"}
    val n = input.size
    assert((n and (n - 1)) == 0) {"Fft size not a power of 2"}
    val leadingZeros = (n - 1).countLeadingZeroBits()

    // Bit reversal
    for (i in 0..<n) {
        val j = Integer.reverse(i) ushr leadingZeros
        if (j > i) {
            val tmp = input[i]
            output[i] = input[j]
            output[j] = tmp
        }
    }

    // LUTs
    val lut = ComplexArray(n) {Complex(0.0, -2 * PI * it / n).exp()}

    // FFT
    for (m in 1..n step {it * 2}) {
        for (k in 0 until n step m) {
            for (j in 0 until m / 2) {
                val tmp = lut[j * n / m] * output[k + j + m / 2]
                output[k + j + m / 2] = output[k + j] - tmp
                output[k + j] += tmp
            }
        }
    }
}

/**
 * Pad an input array with zeros to the next power of 2 and return its DFT.
 */
fun fft(x: ComplexArray): ComplexArray {
    val n = x.size
    var nextPow2 = 1
    while (nextPow2 < n) {
        nextPow2 = nextPow2 shl 1
    }
    val buffer = ComplexArray(nextPow2) {if (it < n) x[it] else Complex.ZERO}
    fft(buffer, buffer)
    return buffer
}

/**
 * Returns the DFT of a real-valued sequence after zero-padding to the next power of 2.
 * If n >= |x| > n/2 where n is a power of 2, the returned array will have length n/2 + 1.
 */
fun fft(x: DoubleArray) : ComplexArray {
    val n = x.size
    var nextPow2 = 1
    while (nextPow2 < n) {
        nextPow2 = nextPow2 shl 1
    }
    val buffer = ComplexArray(nextPow2) {if (it < n) Complex(x[it]) else Complex.ZERO}
    fft(buffer, buffer)
    return buffer.copyOfRange(0, nextPow2 / 2 + 1)
}

fun ifft(input: ComplexArray, output: ComplexArray) {
    assert(input.size == output.size) {"Input size ${input.size} does not match output ${output.size}"}
    input.forEachIndexed {idx, c -> output[idx] = c.conjugate}
    fft(output, output)
    output.forEachIndexed {idx, c -> output[idx] = c.conjugate / output.size}
}