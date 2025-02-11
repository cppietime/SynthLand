package com.funguscow.synthland.synth

import javax.sound.sampled.AudioFormat
import kotlin.math.*

typealias PZK = Triple<ComplexArray, ComplexArray, Double>
typealias IirPrototype = (Int) -> PZK

enum class FilterType {
    LOWPASS,
    HIGHPASS,
    BANDPASS,
    BANDSTOP
}

object Prototypes {
    fun butterworthPZK(degree: Int) : PZK {
        val poles = (0 until degree).map {
            Complex(0.0, PI * (2 * (it + 1) + degree - 1) / (2 * degree)).exp()
        }.toTypedArray()
        val zeros = emptyArray<Complex>()
        val gain = 1.0
        return Triple(poles, zeros, gain)
    }
}

enum class PrototypeTypes(val function: IirPrototype) {
    BUTTERWORTH(Prototypes::butterworthPZK)
}

/**
 * Implements an infinite impulse response filter with specified denominator and numerator coefficients.
 * Obeys:
 * y[n] * a[0] = x[n] * b[0] + x[n - 1] * b[1] + ... - y[n - 1] * a[1] ...
 */
class IirFilter(val denominator: DoubleArray, val numerator: DoubleArray = doubleArrayOf(1.0), channels: Int = 2) : Filter {
    companion object {
        fun digitalPZK(prototype: IirPrototype, degree: Int, type: FilterType, cutoff: Double, secondary: Double? = null) : PZK {
            var (poles, zeros, gain) = prototype(degree)
            val warpedCutoff = 4 * tan(cutoff / 2)
            val warpedSecondary = secondary?.run {4 * tan(this / 2)}
            when (type) {
                FilterType.LOWPASS -> {
                    gain *= warpedCutoff.pow(poles.size - zeros.size)
                    poles = poles.map(warpedCutoff::times).toTypedArray()
                    zeros = zeros.map(warpedCutoff::times).toTypedArray()
                }
                FilterType.HIGHPASS -> {
                    gain *= ((zeros.map(Complex::unaryMinus).toTypedArray().product()) /
                            (poles.map(Complex::unaryMinus).toTypedArray().product())).real
                    poles = poles.map(warpedCutoff::div).toTypedArray()
                    zeros = arrayOf(*zeros.map(warpedCutoff::div).toTypedArray(), *ComplexArray(poles.size - zeros.size) {Complex.ZERO})
                }
                FilterType.BANDPASS -> {
                    requireNotNull(warpedSecondary)
                    val width = warpedSecondary - warpedCutoff
                    val w0 = sqrt((warpedCutoff * warpedSecondary))
                    val relDegree = poles.size - zeros.size
                    gain *= width.pow(relDegree)
                    poles = poles.map((width/2)::times).toTypedArray()
                    zeros = zeros.map((width/2)::times).toTypedArray()
                    val polesDet = poles.map{(it * it - w0 * w0).sqrt()}
                    val zerosDet = zeros.map{(it * it - w0 * w0).sqrt()}
                    poles = (poles zip polesDet).flatMap {(a, b) -> listOf(a + b, a - b)}.toTypedArray()
                    zeros = (zeros zip zerosDet).flatMap {(a, b) -> listOf(a + b, a - b)}.toTypedArray()
                    zeros = arrayOf(*zeros.map(warpedCutoff::div).toTypedArray(), *ComplexArray(relDegree) {Complex.ZERO})
                }
                FilterType.BANDSTOP -> {
                    requireNotNull(warpedSecondary)
                    val width = warpedSecondary - warpedCutoff
                    val w0 = sqrt((warpedCutoff * warpedSecondary))
                    val relDegree = poles.size - zeros.size
                    poles = poles.map((width/2)::div).toTypedArray()
                    zeros = zeros.map((width/2)::div).toTypedArray()
                    val polesDet = poles.map{(it * it - w0 * w0).sqrt()}
                    val zerosDet = zeros.map{(it * it - w0 * w0).sqrt()}
                    poles = (poles zip polesDet).flatMap {(a, b) -> listOf(a + b, a - b)}.toTypedArray()
                    zeros = (zeros zip zerosDet).flatMap {(a, b) -> listOf(a + b, a - b)}.toTypedArray()
                    zeros = arrayOf(*zeros.map(warpedCutoff::div).toTypedArray(), *ComplexArray(relDegree) {Complex(0.0, w0)}, *ComplexArray(relDegree) {Complex(0.0, -w0)})
                    gain *= (zeros.map(Complex::unaryMinus).toTypedArray().product() / poles.map(Complex::unaryMinus).toTypedArray().product()).real
                }
            }

            // Bilinear transform
            val polesZ = poles.map {(4 + it) / (4 - it)}.toTypedArray()
            val zerosZ = arrayOf(*zeros.map {(4 + it) / (4 - it)}.toTypedArray(), *ComplexArray(poles.size - zeros.size){-Complex.ONE})
            gain *= (zeros.map{4-it}.toTypedArray().product() / poles.map{4-it}.toTypedArray().product()).real

            return Triple(polesZ, zerosZ, gain)
        }

        fun fromPZK(poles: ComplexArray, zeros: ComplexArray, gain: Double, channels: Int = 2): IirFilter {
            val a = poles.polyFromRoots().real()
            val b = zeros.polyFromRoots().real().map(gain::times).toDoubleArray()
            println("${a.joinToString()}\n${b.joinToString()}")
            return IirFilter(a, b, channels)
        }
    }

    private val inputCache = Array(channels) { DoubleArray(numerator.size - 1) }
    private var inputIdx = 0
    private val outputCache = Array(channels) { DoubleArray(denominator.size - 1) }
    private var outputIdx = 0
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        assert(inputCache.size >= inputs.size) {"Filter was not initialized with the correct number of caches"}
        repeat (numSamples) {idx ->
            (inputs zip outputs).forEachIndexed { channel, (input, output) ->
                val oldInput = input[idx]
                output[idx] = oldInput * numerator[0]
                for (i in 1 until numerator.size) {
                    output[idx] += numerator[i] * inputCache[channel][(inputIdx - i).mod(numerator.size - 1)]
                }
                for (i in 1 until denominator.size) {
                    output[idx] -= denominator[i] * outputCache[channel][(outputIdx - i).mod(denominator.size - 1)]
                }
                output[idx] /= denominator[0]
                if (numerator.size > 1) {
                    inputCache[channel][inputIdx] = oldInput
                }
                if (denominator.size > 1) {
                    outputCache[channel][outputIdx] = output[idx]
                }
            }
            if (numerator.size > 1) {
                inputIdx = (inputIdx + 1) % (numerator.size - 1)
            }
            if (denominator.size > 1) {
                outputIdx = (outputIdx + 1) % (denominator.size - 1)
            }
        }
    }
}

class BiquadFilter(val denominator: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0), val numerator: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0), channels: Int = 2) : Filter {
    init {
        assert(denominator.size == 3) {"Length of biquad denominator must be 3"}
        assert(numerator.size == 3) {"Length of biquad numerator must be 3"}
    }
    companion object {
        fun fromPZK(poles: ComplexArray, zeros: ComplexArray, gain: Double, channels: Int = 2): BiquadFilter {
            assert(poles.size == 2) {"Biquad filter must have two poles"}
            assert((poles[0].isReal && poles[1].isReal) || (poles[0].conjugate.near(poles[1]))) {"Poles must be real or conjugate pairs"}
            assert(zeros.size == 2) {"Biquad filter must have two zeros"}
            assert((zeros[0].isReal && zeros[1].isReal) || (zeros[0].conjugate.near(zeros[1]))) {"Zeros must be real or conjugate pairs"}
            val denominator = doubleArrayOf(1.0, -(poles[0] + poles[1]).real, (poles[0] * poles[1]).real)
            val numerator = doubleArrayOf(gain, -gain * (zeros[0] + zeros[1]).real, gain * (zeros[0] * zeros[1]).real)
            println("${denominator.joinToString()}\n${numerator.joinToString()}")
            return BiquadFilter(denominator, numerator, channels)
        }

        fun fromPZKs(poles: ComplexArray, zeros: ComplexArray, gain: Double, channels: Int = 2): List<BiquadFilter> {
            assert(poles.size == zeros.size) {"Poles and zeros must have the same amount"}
            val polesM = poles.toMutableList()
            val zerosM = zeros.toMutableList()
            val filters = mutableListOf<BiquadFilter>()
            var gainM = gain
            while (polesM.size > 1) {
                val pole0 = polesM.removeFirst()
                val pole1 = (if (pole0.isReal) polesM.first(Complex::isReal) else polesM.first {it.conjugate.near(pole0)}).also {assert(polesM.remove(it))}
                val zero0 = zerosM.removeFirst()
                val zero1 = (if (zero0.isReal) zerosM.first(Complex::isReal) else zerosM.first {it.conjugate.near(zero0)}).also {assert(zerosM.remove(it))}
                filters.add(fromPZK(arrayOf(pole0, pole1), arrayOf(zero0, zero1), gainM, channels))
                gainM = 1.0
            }
            if (polesM.isNotEmpty()) {
                filters.add(fromPZK(arrayOf(polesM.first(), Complex.ZERO), arrayOf(zerosM.first(), Complex.ZERO), gainM))
            }
            return filters
        }
    }
    private val inputCache = Array(channels) { DoubleArray(2) }
    private val outputCache = Array(channels) { DoubleArray(2) }
    private var cacheIdx = 0
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        assert(inputCache.size >= inputs.size) {"Filter was not initialized with the correct number of caches"}
        repeat (numSamples) {idx ->
            (inputs zip outputs).forEachIndexed { channel, (input, output) ->
                val oldInput = input[idx]
                output[idx] = (oldInput * numerator[0] +
                        inputCache[channel][cacheIdx] * numerator[1] +
                        inputCache[channel][1 - cacheIdx] * numerator[2] +
                        -outputCache[channel][cacheIdx] * denominator[1] +
                        -outputCache[channel][1 - cacheIdx] * denominator[2]) / denominator[0]
                inputCache[channel][1 - cacheIdx] = oldInput
                outputCache[channel][1 - cacheIdx] = output[idx]
            }
            cacheIdx = 1 - cacheIdx
        }
    }
}

typealias WindowFunc = (Int, Int) -> Double

fun sinc(x: Double) = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

object WindowFuncs {
    fun boxcarWindow(x: Int, n: Int) = 1.0
    fun generalHannWindow(x: Int, n: Int, alpha: Double) = alpha + (alpha - 1) * cos(2 * PI * x / (n - 1))
    fun hannWindow(x: Int, n: Int) = generalHannWindow(x, n, 0.5)
    fun hammingWindow(x: Int, n: Int) = generalHannWindow(x, n, 25.0 / 46.0)
}

enum class WindowFuncType (val function: WindowFunc) {
    BOXCAR(WindowFuncs::boxcarWindow),
    HANN(WindowFuncs::hannWindow),
    HAMMING(WindowFuncs::hammingWindow)
}

class FirFilter(val coefficients: DoubleArray = doubleArrayOf(1.0), channels: Int = 2) : Filter {
    companion object {
        /**
         * passBands are each in [0, 1] relative to nyquist frequency.
         */
        fun windowed(degree: Int, passBands: DoubleArray, window: WindowFunc = {_, _ -> 1.0}, channels: Int = 2): FirFilter {
            assert(passBands.size % 2 == 0) {"Pass band size ${passBands.size} is odd"}
            val coefficients = DoubleArray(degree)
            for (i in passBands.indices step 2) {
                val left = passBands[i]
                val right = passBands[i + 1]
                for (j in coefficients.indices) {
                    val x = (j - (degree - 1.0) / 2.0)
                    coefficients[j] += right * sinc(right * x) - left * sinc(left * x)
                }
            }
            for (j in coefficients.indices) {
                coefficients[j] *= window(j, degree)
            }

            // Scale
            val target = if (passBands[0] == 0.0) 0.0 else if (passBands.last() == 1.0) 1.0 else (passBands[0] + passBands[1]) / 2
            var scale = 0.0
            coefficients.forEachIndexed { index, c ->
                val x = (index - (degree - 1.0) / 2.0)
                scale += c * cos(PI * x * target)
            }
            for (j in coefficients.indices) {
                coefficients[j] /= scale
            }
            return FirFilter(coefficients, channels)
        }
    }
    private val inputCache = Array(channels) { DoubleArray(coefficients.size - 1) }
    private var inputIdx = 0
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        assert(inputCache.size >= inputs.size) {"Filter was not initialized with the correct number of caches"}
        repeat (numSamples) {idx ->
            (inputs zip outputs).forEachIndexed { channel, (input, output) ->
                val oldInput = input[idx]
                output[idx] = oldInput * coefficients[0]
                for (i in 1 until coefficients.size) {
                    output[idx] += coefficients[i] * inputCache[channel][(inputIdx - i).mod(coefficients.size - 1)]
                }
                if (coefficients.size > 1) {
                    inputCache[channel][inputIdx] = oldInput
                }
            }
            if (coefficients.size > 1) {
                inputIdx = (inputIdx + 1) % (coefficients.size - 1)
            }
        }
    }
}

class ApplyEach(private vararg val filters: Filter) : Filter {
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        val buffer = Array(inputs.size) {AudioNormalizedBuffer(inputs[0].size)}
        val accum = Array(inputs.size) {AudioNormalizedBuffer(inputs[0].size)}
        filters.forEach {
            it.filter(format, numSamples, inputs, offsetIn, buffer, offsetOut)
            (buffer zip accum).forEach {(cb, ca) ->
                cb.forEachIndexed { index, b -> ca[index] += b }
            }
        }
        (accum zip outputs).forEach { (a, output) ->
            a.copyInto(output)
        }
    }
}

class Chain(private vararg val filters: Filter): Filter {
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        (inputs zip outputs).forEach {(input, output) -> input.copyInto(output)}
        filters.forEach {
            it.filter(format, numSamples, outputs, offsetIn, outputs, offsetIn)
        }
    }
}

class Scale(private val gain: Double) : Filter {
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        (outputs zip inputs).forEach {(output, input) ->
            input.forEachIndexed { idx, i -> output[idx] = i * gain }
        }
    }
}

class ApplyPan(private val left: Filter?, private val right: Filter?) : Filter {
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        if (format.channels != 2) {
            throw IllegalStateException("Not stereo")
        }
        val monoFormat = AudioFormat(format.encoding, format.sampleRate, format.sampleSizeInBits, 1, format.frameSize / 2, format.frameRate / 2, format.isBigEndian)
        left?.filter(monoFormat, numSamples, arrayOf(inputs[0]), offsetIn, arrayOf(outputs[0]), offsetOut)
        right?.filter(monoFormat, numSamples, arrayOf(inputs[1]), offsetIn, arrayOf(outputs[1]), offsetOut)
    }
}

class LfoDelay(private val frequency: Double, private val depth: Double, channels: Int = 2): Filter {
    private var cache = Array(channels){AudioNormalizedBuffer(ceil(depth).toInt())}
    private var cacheSwap = Array(channels){AudioNormalizedBuffer(ceil(depth).toInt())}
    private var phase = 0.0
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        // Copy last chunk into cache for next call
        (inputs zip cacheSwap).forEach {(input, swap) ->
            input.copyInto(swap, startIndex = input.size - swap.size)
        }

        // Execute LFO
        for (it in numSamples - 1 downTo 0) {
            val z = it + depth * (sin(2 * PI * (phase + it) * frequency / format.sampleRate) - 1) / 2
            (inputs zip outputs).forEachIndexed {channel, (input, output) ->
                output[it] = lerp(cache[channel], input, z)
            }
        }

        // Swap cache
        cache = cacheSwap.also {cacheSwap = cache}

        // Update phase
        phase += numSamples
    }

    private fun lerp(cache: AudioNormalizedBuffer, buffer: AudioNormalizedBuffer, z: Double) : Double {
        val idx0 = floor(z).toInt()
        val x0 = if (idx0 < 0) cache[cache.size + idx0] else buffer[idx0]
        val fraction = z - idx0
        if (fraction.absoluteValue < 1e-9) {
            return x0
        }
        val idx1 = idx0 + 1
        val x1 = if (idx1 < 0) cache[cache.size + idx1] else buffer[idx1]
        return x0 + (x1 - x0) * fraction
    }
}

class Chorus(triples: Array<Triple<Double, Double, Double>>) : Filter {
    private val scales : DoubleArray
    private val lfos : Array<LfoDelay>
    init {
        val norm = triples.sumOf(Triple<Double, Double, Double>::third)
        lfos = triples.map{LfoDelay(it.first, it.second)}.toTypedArray()
        scales = triples.map {it.third / norm}.toDoubleArray()
    }

    companion object {
        fun uniform(frequency: Double, depth: Double, size: Int): Chorus {
            val triples = (0 until size).map {Triple(frequency * it, depth, 1.0)}
            return Chorus(triples.toTypedArray())
        }
    }

    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        val buffers = Array(format.channels) {AudioNormalizedBuffer(numSamples)}
        val accum = Array(format.channels) {AudioNormalizedBuffer(numSamples)}
        (scales zip lfos).forEach { (scale, lfo) ->
            lfo.filter(format, numSamples, inputs, offsetIn, buffers, offsetOut)
            (buffers zip accum).forEach {(buffer, acc) ->
                repeat (numSamples) {acc[it] += buffer[it] * scale}
            }
        }
        (accum zip outputs).forEach {(acc, output) ->
            acc.copyInto(output)
        }
    }
}

class CopyFilter(val name: String) : Filter {
    var pointer: Filter? = null
    override fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    ) {
        if (pointer == null) {
            throw IllegalStateException("Forward filter not set")
        }
        pointer!!.filter(format, numSamples, inputs, offsetIn, outputs, offsetOut)
    }
}
