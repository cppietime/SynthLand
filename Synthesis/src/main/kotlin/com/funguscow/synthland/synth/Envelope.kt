package com.funguscow.synthland.synth

import kotlinx.serialization.Serializable
import javax.sound.sampled.AudioFormat
import kotlin.math.max
import kotlin.math.min

@Serializable
class ADSR(private var attack: Double, private var decay: Double, private var sustain: Double, private var release: Double) : Envelope{
    override fun numExtraSamples(note: Note, format: AudioFormat): Int {
        return (release * format.sampleRate).toInt()
    }

    override fun calculateEnvelope(idx: Int, numAlive: Int, format: AudioFormat): Double {
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

    override fun setParam(key: String, value: Double?) {
        when (key) {
            "attack" -> attack = value ?: attack
            "decay" -> decay = value ?: decay
            "sustain" -> sustain = value ?: sustain
            "release" -> release = value ?: release
            else -> super.setParam(key, value)
        }
    }
}

class NoEnvelope : Envelope {
    override fun numExtraSamples(note: Note, format: AudioFormat): Int = 0
    override fun calculateEnvelope(idx: Int, numAlive: Int, format: AudioFormat): Double = 1.0
}
