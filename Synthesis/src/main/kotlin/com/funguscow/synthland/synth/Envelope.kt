package com.funguscow.synthland.synth

import kotlinx.serialization.Serializable
import javax.sound.sampled.AudioFormat
import kotlin.math.max
import kotlin.math.min

@Serializable
class ADSR(private val attack: Double, private val decay: Double, private val sustain: Double, private val release: Double) {
    fun writeNote(format: AudioFormat, note: Note, generator: Generator, outputs: AudioBuffers) {
        if (format.channels != outputs.size) {
            throw IllegalArgumentException("Number of channels in format (${format.channels}) does not match size of output buffer (${outputs.size})")
        }
        if (format.channels <= 0) {
            return
        }
        val startIdx = (note.start).toInt()
        val numAlive = (note.duration).toInt()
        val numRelease = ((note.duration + release * format.sampleRate)).toInt() - numAlive
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

    private fun calculateEnvelope(idx: Int, numAlive: Int, format: AudioFormat): Double {
        // Attack
        val time = idx.toDouble() / format.sampleRate
        if (time <= attack && attack > 0) {
            return time / attack
        }

        // Decay
        if (time <= attack + decay && decay > 0) {
            return 1.0 - (time - attack) / decay * (1.0 - sustain)
        }

        // Sustain
        if (idx < numAlive) {
            return sustain
        }

        // Release
        if (release > 0) {
            val releaseTime = (idx - numAlive).toDouble() / format.sampleRate
            return max(0.0, sustain * (1.0 - releaseTime) / release)
        }
        return 0.0
    }
}