package com.funguscow.synthland.synth

import javax.sound.sampled.AudioFormat
import kotlin.random.Random
import kotlin.random.nextInt

interface NoteScale {
    fun sampleNotes(numNotes: Int = 1): List<Double>
}

class UniformRandomScale(val baseNote: Double, val intervals: List<Int>) : NoteScale {
    override fun sampleNotes(numNotes: Int): List<Double> {
        val notes = mutableListOf<Double>()
        (0 until numNotes).forEach {
            val interval = Random.nextInt(-1, intervals.size)
            var note = baseNote
            for (i in 0 until interval) {
                note += intervals[i]
            }
            if (!notes.contains(note)) {
                notes.add(note)
            }
        }
        return notes
    }

}

enum class NoteType {
    CONSTANT,
    DRONE,
    PLUCK;
}

class Voice(val instrument: Instrument, val volume: Double, val speed: Double, val scale: NoteScale, val type: NoteType) {

    fun writeNote(format: AudioFormat, note: Note, buffers: AudioBuffers) {
        instrument.writeNote(format, note, buffers)
    }

    fun genNotes(format: AudioFormat, numFrames: Int): List<Note> = when (type) {
        // TODO: differentiate these
        NoteType.DRONE, NoteType.CONSTANT -> scale.sampleNotes(1).map { midiNote ->
            Note(midiNote = midiNote, volume = volume, start = 0.0, duration = numFrames.toDouble() - instrument.envelope.numExtraSamples(null, format))
        }
        NoteType.PLUCK -> {
            val notes = mutableListOf<Note>()
            for (idx in 0 until speed.toInt()) {
                if (Random.nextInt(3) > 0) {
                    // TODO: Customize this probability
                    continue
                }
                val note = Note(scale.sampleNotes(1).first(), volume / speed, idx / speed * numFrames, numFrames / speed - instrument.envelope.numExtraSamples(null, format))
                notes.add(note)
            }
            notes
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
