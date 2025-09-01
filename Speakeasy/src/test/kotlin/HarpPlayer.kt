import com.funguscow.synthland.synth.AudioBuffers
import com.funguscow.synthland.synth.AudioNormalizedBuffer
import com.funguscow.synthland.synth.Note
import com.funguscow.synthland.synth.Parser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random
import kotlin.test.Test

object HarpPlayer {

    val harpJson = """
    {
        "envelope": {
            "type": "adsr",
            "attack": 0,
            "decay": 0,
            "sustain": 1,
            "release": 0
        },
        "instrument": {
            "type": "apply",
            "filter": {
              "type": "design_iir",
              "prototype": "BUTTERWORTH",
              "degree": 3,
              "cutoff": {
                "hz": 6000,
                "sampling": 44100
              }
            },
            "generator": {
                "type": "pluck",
                "stretch": [20.0],
                "generator": {
                    "type": "noise"
                }
            }
        }
    }
""".trimIndent()

    val harp = Parser.parseInstrument(harpJson)

    val majorScale = doubleArrayOf(0.0, 2.0, 4.0, 5.0, 7.0, 9.0, 11.0)
    const val baseNote = 72.0 // C5

    const val sampleRate = 44100F
    const val bitDepth = 16
    const val channels = 1
    val format
        get() = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            bitDepth,
            channels,
            bitDepth * channels / 8,
            bitDepth * channels / 8 * sampleRate,
            false
        )

    val lineInfo = DataLine.Info(SourceDataLine::class.java, format)
    val line = AudioSystem.getLine(lineInfo) as SourceDataLine

    const val noteLength = 0.5
    const val notesPerChunk = 8
    val chunkLength = noteLength * notesPerChunk + harp.envelope.numExtraSamples(Note(0.0, 0.0, 0.0, 1.0), format)

    @Test
    fun main() {
        val scale = (0 until 3).flatMap {base -> majorScale.map{ivl -> ivl + base * 12 + baseNote}}.toDoubleArray()
        var lastBuffer = AudioBuffers(channels) { AudioNormalizedBuffer((chunkLength * sampleRate).toInt()) }
        var buffer : AudioBuffers
        line.open()
        line.start()
        val bytes = ByteArray((chunkLength * sampleRate).toInt() * channels * bitDepth / 8)
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val intbuffer = byteBuffer.asShortBuffer()
        var i = 0
        while (true) {
            val root = scale[Random.nextInt(scale.size - 4)]
            val sequence = intArrayOf(0, 2, 4, 8, 0, 2, 4, 8).mapIndexed {index, it -> Note(root + it, 0.5, index * noteLength * sampleRate, noteLength * sampleRate)}
            buffer = harp.renderSequence(format, sequence)
            println("${buffer.maxOf{it.max()}}-${buffer.minOf{it.min()}}")
            if (i >= 0) {
                val extra = harp.envelope.numExtraSamples(Note(0.0, 0.0, 0.0, 1.0), format)
                repeat (extra) {
                    buffer[it] += lastBuffer[(noteLength * notesPerChunk * sampleRate + it).toInt()]
                }
                intbuffer.rewind()
                println("${buffer[0].size}, ${bytes.size}")
                repeat(buffer[0].size) { a ->
                    buffer.forEach { b ->
                        intbuffer.put((b[a] * 32767).toInt().toShort())
                    }
                }
                println("${bytes.max()}-${bytes.min()}")
                line.write(bytes, 0, bytes.size)
            }
            lastBuffer = buffer.also{buffer = lastBuffer}
            i++
        }
    }
}