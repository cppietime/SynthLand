package com.funguscow.synthland.synth

import javax.sound.sampled.AudioFormat
import kotlin.math.*
import kotlin.random.Random



/**
 * Generates a linear phase where each sample increment by 2PI x frequency / sample rate.
 * Note volume is ignored.
 */
class Linear : Generator {
    private var phase: Double = 0.0

    override fun generate(format: AudioFormat, note: Note, numSamples: Int, outputs: AudioBuffers, offset: Int): Boolean {
        repeat (numSamples) {idx -> outputs.forEach {output -> output[idx] = if ((phase + idx) >= note.start && (phase + idx) < note.duration) (phase + idx) * 2 * PI * note.frequency / format.sampleRate else 0.0}}
        phase += numSamples
        return phase < note.duration
    }
}

/**
 * Modifies the frequency or volume of the child generator.
 * Frequency is represented as MIDI note indices, so a value of 12.0 corresponds to raising an octave.
 */
class NoteModifier(private val generator: Generator, private val frequency: Double? = null, private val frequencyMultiplier: Double? = null, private val frequencyAddition: Double? = null, private val volume: Double? = null, private val volumeMultiplier: Double? = null) : Generator {
    init {
        if (frequency != null && (frequencyMultiplier != null || frequencyAddition != null)) {
            throw IllegalArgumentException("NoteModifier may not specify both a frequency and frequency multiplier")
        }
        if (volume != null && volumeMultiplier != null) {
            throw IllegalArgumentException("NoteModifier may not specify both a volume and volume multiplier")
        }
    }

    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val newNote: Note
        if (frequency == null && frequencyMultiplier == null && frequencyAddition == null && volume == null && volumeMultiplier == null) {
            newNote = note
        } else {
            var midiNote = frequency ?: (note.midiNote + (frequencyMultiplier ?: 0.0))
            if (frequencyAddition != null) {
                midiNote += log(1.0 + frequencyAddition / note.frequency, 2.0.pow(1.0 / 12))
            }
            val volume = volume ?: (note.volume * (volumeMultiplier ?: 1.0))
            newNote = note.copy(midiNote = midiNote, volume = volume)
        }
        return generator.generate(format, newNote, numSamples, outputs, offset)
    }
}

/**
 * Applies the sin function to the input.
 */
class SineWave(private val generator: Generator) : Generator {

    override fun generate(format: AudioFormat, note: Note, numSamples: Int, outputs: AudioBuffers, offset: Int): Boolean {
        val remaining = generator.generate(format, note, numSamples, outputs, offset)
        repeat(numSamples) {idx -> outputs.forEach { output -> output[idx] = note.volume * sin(output[idx]) }}
        return remaining
    }
}

/**
 * Adds two signals.
 */
class Addition(private val left: Generator, private val right: Generator) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val buffers = Array(format.channels) { AudioNormalizedBuffer(numSamples) }
        var remaining = left.generate(format, note, numSamples, buffers, offset)
        remaining = right.generate(format, note, numSamples, outputs, offset) || remaining
        outputs.forEachIndexed { idx, output -> repeat (numSamples) { i -> output[i] += buffers[idx][i]} }
        return remaining
    }
}

/**
 * Multiplies two signals.
 */
class Multiplication(private val left: Generator, private val right: Generator) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val buffers = Array(format.channels) { AudioNormalizedBuffer(numSamples) }
        var remaining = left.generate(format, note, numSamples, buffers, offset)
        remaining = right.generate(format, note, numSamples, outputs, offset) || remaining
        outputs.forEachIndexed { idx, output -> repeat (numSamples) { i -> output[i] *= buffers[idx][i]} }
        return remaining
    }
}

/**
 * Applies a modulus function to the input to produce a saw wave.
 */
class SawWave(private val generator: Generator) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val remaining = generator.generate(format, note, numSamples, outputs, offset)
        repeat (numSamples) {idx -> outputs.forEach {output -> output[idx] = note.volume * ((output[idx] / PI) % 2.0 - 1.0)}}
        return remaining
    }
}

/**
 *  Converts phase to a square wave with customizable duty cycle.
 */
class SquareWave(private val generator: Generator, private val duty: Double = 0.5) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val remaining = generator.generate(format, note, numSamples, outputs, offset)
        repeat (numSamples) {idx -> outputs.forEach {output -> output[idx] = note.volume * duty.sign * (if (output[idx] % (2 * PI) <= 2 * PI * duty.absoluteValue) 1.0 else -1.0)}}
        return remaining
    }
}

/**
 * Generates white noise. Note frequency is ignored.
 */
class WhiteNoise : Generator {
    private var phase = 0.0

    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        repeat (numSamples) {idx -> outputs.forEach {output -> output[idx] = if (phase >= note.start && phase < note.duration) (Random.nextDouble() * 2 - 1) * note.volume else 0.0}}
        phase += numSamples
        return phase < note.duration
    }
}

/**
 * Apply a filter to a child generator
 */
class Apply(private val filter: Filter, private val generator: Generator) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val remaining = generator.generate(format, note, numSamples, outputs, offset)
        filter.filter(format, numSamples, outputs, 0, outputs, 0)
        return remaining
    }
}

/**
 * Composes multiple detuned sawtooth generators
 */
class SuperSaw(private val detunes: DoubleArray, private val generator: Generator, scales: DoubleArray = doubleArrayOf()) : Generator {
    private val scales = if (scales.size == detunes.size) scales else DoubleArray(detunes.size) {1.0 / detunes.size}
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        val norm = 2 * note.volume / scales.sum()
        val phase = Array(outputs.size) {AudioNormalizedBuffer(numSamples)}
        val remaining = generator.generate(format, note, numSamples, phase, offset)
        outputs.forEach {it.fill(0.0)}
        repeat(numSamples) {i ->
            (detunes zip scales).forEach {(detune, scale) ->
                outputs.forEachIndexed {c, output -> output[i] += scale * ((phase[c][i] * detune / 2 / PI) % 1 - 0.5)}
            }
            outputs.forEach { output -> output[i] *= norm }
        }
        return remaining
    }
}

/**
 * Sends one generator to each ear
 */
class Ears(private val left: Generator?, private val right: Generator?) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        if (format.channels != 2) {
            throw IllegalStateException("Use of Ears on non-stereo")
        }
        val monoFormat = AudioFormat(format.encoding, format.sampleRate, format.sampleSizeInBits, 1, format.frameSize / 2, format.frameRate / 2, format.isBigEndian)
        var remaining = left?.generate(monoFormat, note, numSamples, arrayOf(outputs[0]), offset) == true
        remaining = right?.generate(monoFormat, note, numSamples, arrayOf(outputs[1]), offset) == true || remaining
        return remaining
    }
}

/**
 * Multiply each ear by a different factor
 */
class Pan(private val generator: Generator, private val left: Double, private val right: Double): Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        if (format.channels != 2) {
            throw IllegalStateException("Use of Ears on non-stereo")
        }
        return generator.generate(format, note, numSamples, outputs, offset).also {
            repeat(numSamples) {
                outputs[0][it] *= left
                outputs[1][it] *= right
            }
        }
    }
}

/**
 * Pluck generator for strings
 */
class KarplusStrongGenerator(private val impulse: Generator, private val decay: (Double) -> Double = {1.0}, private val stretch: (Double) -> Double = {1.0}, private val drum: Double = 0.0) : Generator {
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        // Calculate period
        val period = format.sampleRate / note.frequency
        val iPeriod = ceil(period).toInt()
        impulse.generate(format, note, iPeriod, outputs, offset)

        // Run loop
        for (i in iPeriod until numSamples) {
            val read = i - period
            val iRead = read.toInt()
            val fraction = read - iRead
            outputs.forEach {
                val first = it[iRead]
                val second = it[iRead + 1]
                val lerp0 = first + (second - first) * fraction
                if (Random.nextDouble() >= 1.0 - 1 / stretch(note.frequency)) {
                    val third = it[iRead + 2]
                    val lerp1 = second + (third - second) * fraction
                    it[i] = (lerp0 + lerp1) / 2 * decay(note.frequency)
                    if (Random.nextDouble() <= drum) {
                        it[i] = it[i] * -1
                    }
                } else {
                    it[i] = lerp0
                }
            }
        }
        // TODO this means nothing
        return true
    }
}

class CopyGenerator(val name: String) : Generator {
    var pointer: Generator? = null
    override fun generate(
        format: AudioFormat,
        note: Note,
        numSamples: Int,
        outputs: AudioBuffers,
        offset: Int
    ): Boolean {
        if (pointer == null) {
            throw IllegalStateException("Forward generator not set")
        }
        return pointer!!.generate(format, note, numSamples, outputs, offset)
    }
}
