package com.funguscow.synthland.speaker

import com.funguscow.synthland.synth.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test

object GeneratorsTest {
    fun sinTest(): Generator = SineWave(Linear())
    fun sinSin(): Generator = SineWave(NoteModifier(SineWave(Linear())))
    fun sawTest(): Generator = SawWave(Linear())
    fun squareTest(): Generator = SquareWave(Linear(), 0.9)
    fun noiseTest(): Generator = WhiteNoise()
    fun modulatedPhase(): Generator = Addition(
        Linear(),
        NoteModifier(
            SineWave(Linear()),
            frequencyMultiplier = -12.0,
            volume = 0.2,
        )
    )

    fun constructPm(): Generator = SineWave(
        modulatedPhase()
    )

    fun superSaw(): Generator =
        SuperSaw(doubleArrayOf(1.0, 1.01, 1.005, 0.99, 0.995), modulatedPhase(), doubleArrayOf(1.0, 0.9, 0.9, 0.5, 0.5))

    fun testBwLp(): Filter {
        val (poles, zeros, gain) = IirFilter.digitalPZK(
            Prototypes::butterworthPZK,
            3,
            FilterType.LOWPASS,
            4000 * 2 * PI / 44100
        )
        val filter = IirFilter.fromPZK(poles, zeros, gain)
        return filter
    }

    fun testBiquads(): Filter {
        val (poles, zeros, gain) = IirFilter.digitalPZK(
            Prototypes::butterworthPZK,
            3,
            FilterType.LOWPASS,
            4000 * 2 * PI / 44100
        )
        val filters = BiquadFilter.fromPZKs(poles, zeros, gain)
        return Chain(*filters.toTypedArray())
    }

    fun testFir(): Filter {
        val bands = doubleArrayOf(20.0, 1000.0).map { it * 2 / 44100 }.toDoubleArray()
        return FirFilter.windowed(9, bands, WindowFuncs::hammingWindow).also { println(it.coefficients.joinToString()) }
    }

    val jsonStr = """
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
                "type": "ears",
                "left": {
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
                "right": {
                  "type": "modifier",
                  "frequency_addition": 3,
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
                  }
                }
            }
            } 
        }
    """.trimIndent()

    val jsonStrWithCopy = """
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
                "type": "ears",
                "left": {
                  "name": "left",
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
                "right": {
                  "type": "modifier",
                  "frequency_addition": 3,
                  "generator": {
                    "type": "copy_generator",
                    "copies": "left",
                    "name": "right"
                  }
                }
            }
            } 
        }
    """.trimIndent()

    val jsonStrBinBeat = """
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
                "type": "monobeat",
                "generator": {
                  "name": "left",
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
                "frequency": 8.0
            }
            } 
        }
    """.trimIndent()

    fun testParse(): Generator {
        val gen = Parser.parseInstrument(jsonStrBinBeat).generator
        return gen
    }

    fun testKs(): Generator = Apply(
        Chorus.uniform(0.2, 150.0, 10),
        KarplusStrongGenerator(WhiteNoise(), stretch = { 24.0 }, drum = 0.0)
    )

    @Test
    fun main() {
        val drone = GeneratorsTest.testParse()
        val tink = GeneratorsTest.testKs()
        val scale = doubleArrayOf(0.0, 3.0, 5.0, 7.0, 10.0)
        val duration = 4.0
        val length = (44100.0 * duration).toInt()
        val shortNotes = 8
        val shortLength = length / shortNotes
        val channels = 2
        val format =
            AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100F, 16, channels, 2 * channels, 44100F * 2 * channels, false)
        val buffer = Array(channels) { AudioNormalizedBuffer(length.toInt()) }
        val miniBuffer = Array(channels) { AudioNormalizedBuffer(shortLength.toInt()) }
        val byteArray = ByteArray(length.toInt() * 2 * channels)
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        val intBuffer = byteBuffer.asShortBuffer()
        val release = 1.0
        val adsr = ADSR(1.0, 0.0, 1.0, release)
        val droneInst = Instrument(drone, mutableMapOf(), adsr)
        val shortAdsr = ADSR(0.0, 0.0, 1.0, 0.5)
        val tinkInst = Instrument(tink, mutableMapOf(), shortAdsr)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as SourceDataLine
        line.open()
        line.start()
        while (true) {
            buffer.forEach { b -> b.fill(0.0) }
            val note =
                Note(64.0 + scale[Random.nextInt(scale.size)], 0.5, 0.0, length.toDouble() - format.sampleRate * release)
            //adsr.writeNote(format, note.copy(duration = note.duration - format.sampleRate * release), drone, buffer)
            droneInst.writeNote(format, note, buffer)
            for (sit in 0 until shortNotes) {
                miniBuffer.forEach { b -> b.fill(0.0) }
                val shortNote = Note(
                    76.0 + scale[Random.nextInt(scale.size)],
                    1.0 / shortNotes,
                    sit.toDouble() * shortLength,
                    shortLength.toDouble()
                )
                if (Random.nextInt(3) > 0) {
                    continue
                }
                //shortAdsr.writeNote(format, shortNote, tink, buffer)
                tinkInst.writeNote(format, shortNote, buffer)
            }
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
