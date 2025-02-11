import com.funguscow.synthland.synth.AudioNormalizedBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Scanner
import javax.sound.sampled.*
import kotlin.math.PI
import kotlin.math.sin

const val SAMPLING_RATE = 44100F
const val BITS16 = 16
const val STEREO = 2
val format = AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    SAMPLING_RATE,
    BITS16,
    STEREO,
    BITS16 * STEREO / 8,
    SAMPLING_RATE * BITS16 * STEREO / 8,
    false)
const val BUFFER_SIZE = 4096


var phase = 0.0
var frequency = 440F
fun generateBuffer(n: Int, buffer: AudioNormalizedBuffer) {
    // Some potentially state-sensitive audio generation procedure. Generating a pure sine as an example.
    repeat(n) {buffer[it] = (sin((phase + it * 2 * PI * frequency / 44100F)))}
    phase += (n * 2 * PI * frequency / 44100F)
}

const val TIME_EPSILON = 16_000

fun reader() {
    val scanner = Scanner(System.`in`)
    while (true) {
        val input = scanner.nextLine()
        try {
            frequency = input.toFloat()
        } catch (e: Exception) {}
    }
}

fun main() {
    Thread(::reader).start()

    val dataLineInfo = DataLine.Info(
        SourceDataLine::class.java,
        format)
    val dataLine = AudioSystem.getLine(dataLineInfo) as SourceDataLine
    dataLine.open()
    val byteArray = ByteArray(2 * 2 * BUFFER_SIZE)
    val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
    val intBuffer = byteBuffer.asIntBuffer()
    val audioArray = DoubleArray(BUFFER_SIZE)
    dataLine.start()

    // This just generates and writes data nonstop. Can I generate chunks of audio on-demand as they are exhausted?
    val startTime = System.nanoTime();
    var numFrames = 0L
    val timePerFrame = BUFFER_SIZE.toDouble() / SAMPLING_RATE
    while (true) {
        generateBuffer(BUFFER_SIZE, audioArray)
        intBuffer.rewind()
        repeat(BUFFER_SIZE) { intBuffer.put((audioArray[it] * 32767).toInt()) }
        dataLine.write(byteArray, 0, 2 * 2 * BUFFER_SIZE)
        val targetTime = startTime + timePerFrame * numFrames
        while (System.nanoTime() < targetTime) {
            val diff = targetTime - System.nanoTime()
            if (diff > TIME_EPSILON) {
                Thread.yield()
            }
        }
        numFrames++
    }
}