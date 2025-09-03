package com.funguscow.synthland.speaker

import com.funguscow.synthland.synth.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random
import kotlin.test.Test

object VoiceTest {

    val instrStr = """
        {
            "instrument": {
            "type": "apply",
            "filter": {
              "type": "chain",
              "filters": [
              {
                "type": "chorus",
                "frequency": 0.1,
                "depth": 300,
                "size": 10
                },
                {
                "type": "design_iir",
                "prototype": "BUTTERWORTH",
                "degree": 4,
                "cutoff": {
                  "hz": 4000,
                  "sampling": 44100
                }
                }
              ]
            },
            "generator": {
                "type": "binbeat",
                "generator": {
                  "type": "sin",
                  "generator": {
                    "type": "add",
                    "left": {
                        "type": "linear"
                    },
                    "right": {
                        "type": "modifier",
                        "frequency_multiplier": 12.0,
                        "volume": 1.0,
                        "generator": {
                            "type": "sin"
                        }
                    }
                  }
                },
                "frequency": 3.6
            }
            } 
        }
    """.trimIndent()

    val voiceStr = """
        {
          "instrument": $instrStr,
          "scale": {},
          "type": "DRONE",
          "volume": 0.125
        }
    """.trimIndent()

    val ksStr = """
        {
          "instrument": {
          "instrument": {
            "type": "apply",
            "filter": {
              "type": "chorus",
              "frequency": 0.2,
              "depth": 150.0,
              "size": 10
            },
            "generator": {
              "type": "pluck",
              "generator": {
                "type": "noise"
              },
              "stretch": [24.0],
              "drum": 0.0
            }
          }
          },
          "volume": 0.5,
          "speed": 8.0,
          "type": "PLUCK",
          "scale": {}
        }
    """.trimIndent()

    fun testParse(): Voice {
        val voice = Parser.parseVoice(Json.decodeFromString(voiceStr))
        return voice
    }

    fun testKs(): Voice = Parser.parseVoice(Json.decodeFromString(ksStr))

    @Test
    fun main() {
        val drone = VoiceTest.testParse()
        val tink = VoiceTest.testKs()
        val duration = 4.0
        val length = (44100.0 * duration).toInt()
        val channels = 2
        val format =
            AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100F, 16, channels, 2 * channels, 44100F * 2 * channels, false)
        val buffer = Array(channels) { AudioNormalizedBuffer(length.toInt()) }
        val byteArray = ByteArray(length.toInt() * 2 * channels)
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        val intBuffer = byteBuffer.asShortBuffer()
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as SourceDataLine
        line.open()
        line.start()
        while (true) {
            buffer.forEach { b -> b.fill(0.0) }
            drone.writeMeasure(format, buffer)
            tink.writeMeasure(format, buffer)
            intBuffer.rewind()
            repeat(length.toInt()) { a ->
                buffer.forEach { b ->
                    intBuffer.put((b[a] * 32767).toInt().toShort())
                }
            }
            line.write(byteArray, 0, length.toInt() * 2 * channels)
        }
        line.drain()
        line.close()
    }
}
