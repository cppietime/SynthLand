package com.funguscow.synthland.synth

import javax.sound.sampled.AudioFormat
import kotlin.random.Random

interface NoteScale {
    fun sampleNotes(numNotes: Int = 1): List<Double>
}

class UniformSampleScale(private val notes: List<Double>) : NoteScale {
    override fun sampleNotes(numNotes: Int): List<Double> {
        val choices = notes.indices.toMutableList()
        val selection = mutableListOf<Double>()
        repeat(numNotes) { _ ->
            val i = Random.nextInt(choices.size)
            val idx = choices.removeAt(i)
            val note = notes[idx]
            selection.add(note)
        }
        return selection
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UniformSampleScale) {
            return false
        }
        return notes.toSet() == other.notes.toSet()
    }

    override fun hashCode(): Int {
        return notes.toSet().hashCode()
    }

    override fun toString(): String {
        return "Scale($notes)"
    }

}

enum class NoteType {
    CONSTANT,
    DRONE,
    PLUCK,
    ARPEGGIO;
}

class Voice(val instrument: Instrument, val volume: Double, val speed: Double, val scale: NoteScale, val type: NoteType, val noteProb: Double = 1.0) {

    private var lastNote: Double? = null

    private fun writeNote(format: AudioFormat, note: Note, buffers: AudioBuffers) {
        instrument.writeNote(format, note, buffers)
    }

    private fun genNotes(format: AudioFormat, numFrames: Int): List<Note> = when (type) {
        NoteType.DRONE -> if (Random.nextDouble() > noteProb) listOf() else scale.sampleNotes(1).map { midiNote ->
            Note(midiNote = midiNote, volume = volume, start = 0.0, duration = numFrames.toDouble() - instrument.envelope.numExtraSamples(null, format))
        }
        NoteType.CONSTANT -> if (Random.nextDouble() > noteProb) listOf() else (lastNote?.let{listOf(it)} ?: scale.sampleNotes(1)).map { midiNote ->
            lastNote = midiNote
            Note(midiNote = midiNote, volume = volume, start = 0.0, duration = numFrames.toDouble() - instrument.envelope.numExtraSamples(null, format))
        }
        NoteType.PLUCK -> {
            val notes = mutableListOf<Note>()
            for (idx in 0 until speed.toInt()) {
                if (Random.nextDouble() > noteProb) {
                    continue
                }
                val note = Note(scale.sampleNotes(1).first(), volume / speed, idx / speed * numFrames, numFrames / speed - instrument.envelope.numExtraSamples(null, format))
                notes.add(note)
            }
            notes
        }
        NoteType.ARPEGGIO -> {
            if (Random.nextDouble() > noteProb) {
                listOf()
            } else {
                val choices = scale.sampleNotes(3)
                val notes = mutableListOf<Note>()
                for (idx in 0 until speed.toInt()) {
                    val note = Note(
                        choices[idx % choices.size],
                        volume / speed,
                        idx / speed * numFrames,
                        numFrames / speed - instrument.envelope.numExtraSamples(null, format)
                    )
                    notes.add(note)
                }
                notes
            }
        }
    }

    fun writeMeasure(format: AudioFormat, buffers: AudioBuffers) {
        val numFrames = buffers.first().size
        val notes = genNotes(format, numFrames)
        notes.forEach {note ->
            writeNote(format, note, buffers)
        }
    }

}
