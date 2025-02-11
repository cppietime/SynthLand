package com.funguscow.synthland.speaker

import com.funguscow.synthland.synth.audioGeneratorToWriteCallback
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

const val  SAMPLING_RATE = 44100F
const val BITS16 = 16
const val STEREO = 2
val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLING_RATE, BITS16, STEREO, BITS16 * STEREO / 8, SAMPLING_RATE * BITS16 * STEREO / 8, false)

fun sinGenerator(format: AudioFormat, n: Int): DoubleArray = DoubleArray(n) {
    sin(it * 2 * PI * 440 / format.sampleRate)
}

val sinWriter = audioGeneratorToWriteCallback(::sinGenerator)

val datalineInfo = DataLine.Info(SourceDataLine::class.java, AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100F, 16, 2, 4, 44100 * 4F, false))
val dataLine = AudioSystem.getLine(datalineInfo) as SourceDataLine
fun main() {
    dataLine.open()
    val byteArray = ByteArray(2 * 2 * 44100)
    val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
    val intBuffer = byteBuffer.asIntBuffer()
    val floatBuffer = DoubleArray(44100)
    dataLine.start()
    while (true) {
        sinWriter(format, 44100,  floatBuffer)
        intBuffer.rewind()
        repeat(44100) { intBuffer.put((floatBuffer[it] * 32767).toInt()) }
        dataLine.write(byteArray, 0, 2 * 2 * 44100)
    }
}