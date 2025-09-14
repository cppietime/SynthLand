package com.funguscow.synthland.speaker

import com.funguscow.synthland.synth.AudioNormalizedBuffer
import com.funguscow.synthland.synth.Parser
import com.funguscow.synthland.synth.WaveWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.DoubleArray

object VoicesToWav {
    fun voicesToWav(format: AudioFormat, inFile: File, outFile: File) {
        assert(format.sampleSizeInBits == 16) { "Only supports 16 bits for now." }
        val jsonStr = FileReader(inFile).use { it.readText() }
        val jsonObj = Json.decodeFromString<JsonObject>(jsonStr)
        val voices = jsonObj["voices"]!!.jsonArray.map { Parser.parseVoice(it.jsonObject) }
        val measureLen = jsonObj["measureLength"]!!.jsonPrimitive.double
        val measures = jsonObj["measures"]!!.jsonPrimitive.int
        val length = (measureLen * format.sampleRate).toInt()

        val buffer = Array(format.channels) { AudioNormalizedBuffer(length) }
        val byteArray = ByteArray(length * format.channels * format.sampleSizeInBits / 8)
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = byteBuffer.asShortBuffer()

        val writer = WaveWriter().also {it.open(outFile)}
        writer.writeHeader(format)

        try {
            repeat(measures) {
                buffer.forEach {it.fill(0.0)}
                voices.forEach {
                    it.writeMeasure(format, buffer)
                }
                shortBuffer.rewind()
                repeat (length) { i ->
                    buffer.forEach { b ->
                        shortBuffer.put((b[i] * 32767).toInt().toShort())
                    }
                }
                writer.writeData(byteArray, length * format.channels * format.sampleSizeInBits / 8, 0)
            }
        } finally {
            writer.close()
        }
    }
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Specify in and out file paths");
        return
    }
    val infile = File(args[0])
    val outfile = File(args[1])
    val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f * 4, false)
    VoicesToWav.voicesToWav(format, infile, outfile)
}
