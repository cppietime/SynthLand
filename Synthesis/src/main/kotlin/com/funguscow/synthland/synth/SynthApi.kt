package com.funguscow.synthland.synth

import javax.sound.sampled.AudioFormat
import kotlin.math.*

typealias AudioNormalizedBuffer = DoubleArray

fun AudioNormalizedBuffer.normalize(to: Double = 1.0, amplify: Boolean = false) {
    val greatest = max(max(), -min())
    if (greatest == 0.0 || (greatest <= to && !amplify)) {
        return
    }
    val scale = to / greatest
    indices.forEach {
        this[it] *= scale
    }
}

typealias AudioBuffers = Array<AudioNormalizedBuffer>

fun AudioBuffers.normalize(to: Double = 1.0, amplify: Boolean = false, independent: Boolean = false) {
    if (independent) {
        forEach {it.normalize(to, amplify)}
        return
    }
    val greatest = maxOf { max(it.max(), -it.min()) }
    if (greatest == 0.0) {
        return
    }
    val scale = to / greatest
    forEach {
        it.indices.forEach {idx -> it[idx] *= scale}
    }
}

typealias AudioWriteCallback = (AudioFormat, Int, AudioNormalizedBuffer) -> Unit
typealias AudioGeneratorCallback = (AudioFormat, Int) -> DoubleArray

/**
 * If you write a generator instead of a writer, use this to convert it.
 */
fun audioGeneratorToWriteCallback(callbackIn: AudioGeneratorCallback): AudioWriteCallback {
    return { format, n, buffer ->
        val array = callbackIn(format, n)
        array.copyInto(buffer)
    }
}

data class Note(
    val midiNote: Double,
    val volume: Double,
    val start: Double,
    val duration: Double,
    val extra: Map<String, Any>? = null
) {
    val frequency get() = 440.0 * 2.0.pow((midiNote - 69.0) / 12.0)
}

interface Component {
    fun setParam(key: String, value: Double?) {
        println("No parameter found for key \"$key\"...")
    }
}

interface Generator : Component {
    /**
     * Write a chunk of audio data to outputs. Returns whether this generator's generate method should be called for
     * more chunks.
     */
    fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean
}

interface Filter : Component {
    fun filter(
        format: AudioFormat,
        numSamples: Int,
        inputs: AudioBuffers,
        offsetIn: Int,
        outputs: AudioBuffers,
        offsetOut: Int
    )
}

interface Envelope : Component {
    fun calculateEnvelope(idx: Int, numAlive: Int, format: AudioFormat) : Double

    fun numExtraSamples(note: Note?, format: AudioFormat) : Int
    fun writeNote(format: AudioFormat, note: Note, generator: Generator, outputs: AudioBuffers) {
        if (format.channels != outputs.size) {
            throw IllegalArgumentException("Number of channels in format (${format.channels}) does not match size of output buffer (${outputs.size})")
        }
        if (format.channels <= 0) {
            return
        }
        val startIdx = (note.start).toInt()
        val numAlive = (note.duration).toInt()
        val numRelease = ((note.duration + numExtraSamples(note, format))).toInt() - numAlive
        val totalSamples = numAlive + numRelease
        val endIdx = min( startIdx + totalSamples, outputs[0].size) // Exclusive
        val numSamples = endIdx - startIdx
        val noteBuffer = Array(format.channels) {AudioNormalizedBuffer(numSamples)}
        val livingNote = note.copy(start = Double.MIN_VALUE, duration = Double.MAX_VALUE)
        generator.generate(format, livingNote, numSamples, noteBuffer, 0)

        // Copy with envelope to output buffer
        repeat (numSamples) {
            val envelope = calculateEnvelope(it, numAlive, format)
            (noteBuffer zip outputs).forEach {(buffer, output) ->
                output[it + startIdx] += envelope * buffer[it]
            }
        }
    }
}

data class Instrument(val generator: Generator, val namespace: Map<String, Component>, val envelope: Envelope = NoEnvelope()) {
    fun writeNote(format: AudioFormat, note: Note, outputs: AudioBuffers) {
        envelope.writeNote(format, note, generator, outputs)
    }

    fun renderSequence(format: AudioFormat, notes: Iterable<Note>) : AudioBuffers {
        val notesInOrder = notes.sortedBy(Note::start)
        val lastNote = notes.maxBy {it.start + it.duration}
        val numSamples = (lastNote.start + lastNote.duration).toInt()
        val extraSamples = envelope.numExtraSamples(lastNote, format)
        val totalSamples = numSamples + extraSamples
        val buffers = AudioBuffers(format.channels) {AudioNormalizedBuffer(totalSamples)}
        notesInOrder.forEach {
            envelope.writeNote(format, it, generator, buffers)
        }
        return buffers
    }
}

data class Complex(val real: Double, val imag: Double = 0.0) {
    val mag2 get() = real * real + imag * imag
    val mag get() = sqrt(mag2)
    val arg get() = atan2(imag, real)
    val conjugate get() = Complex(real, -imag)
    val reciprocal get() = conjugate / mag2

    companion object {
        val ONE = Complex(1.0, 0.0)
        val ZERO = Complex(0.0, 0.0)
        val I = Complex(0.0, 1.0)
    }

    operator fun unaryMinus() = Complex(-real, -imag)

    operator fun plus(other: Number) = Complex(real + other.toDouble(), imag)
    operator fun minus(other: Number) = plus(-other.toDouble())
    operator fun times(other: Number) = Complex(real * other.toDouble(), imag * other.toDouble())
    operator fun div(other: Number) = times(1.0 / other.toDouble())

    operator fun plus(other: Complex) = Complex(real + other.real, imag + other.imag)
    operator fun minus(other: Complex) = Complex(real - other.real, imag - other.imag)
    operator fun times(other: Complex) =
        Complex(real * other.real - imag * other.imag, real * other.imag + imag * other.real)

    operator fun div(other: Complex) = times(other.conjugate) / other.mag2

    fun exp() = exp(real) * Complex(cos(imag), sin(imag))

    fun log() = Complex(ln(mag2) / 2, arg)

    fun pow(exponent: Number) = (log() * exponent).exp()

    fun sqrt() = pow(0.5)

    fun near(other: Complex, tolerance: Double = 1e-6) = minus(other).mag2 < tolerance * tolerance

    val isReal get() = imag.absoluteValue <= 1e-9

    override fun toString(): String = "($real + ${imag}i)"
}

operator fun Number.plus(other: Complex) = other + this
operator fun Number.minus(other: Complex) = -other + this
operator fun Number.times(other: Complex) = other * this
operator fun Number.div(other: Complex) = other.reciprocal * this

infix fun IntProgression.step(next: (Int) -> Int) = generateSequence(first) { prev ->
    next(prev).let {
        if ((first < last && it <= last) || (first > last && it >= last)) it else null
    }
}

typealias ComplexArray = Array<Complex>

fun ComplexArray.sum() = reduceOrNull(Complex::plus) ?: Complex.ZERO
fun ComplexArray.product() = reduceOrNull(Complex::times) ?: Complex.ONE
fun ComplexArray.matchUnordered(other: ComplexArray, tolerance: Double = 1e-6) = all {x -> count {x.near(it, tolerance)} == other.count {x.near(it, tolerance)}}
fun ComplexArray.polyFromRoots() : ComplexArray {
    val coefficients = mutableListOf(Complex.ONE)
    forEach {root ->
        val byRoot = coefficients.map((-root)::times)
        coefficients.add(Complex.ZERO)
        byRoot.forEachIndexed {i, c ->
            coefficients[i + 1] += c
        }
    }
    return coefficients.toTypedArray()
}
fun ComplexArray.real() = map {it.real}.toDoubleArray()
