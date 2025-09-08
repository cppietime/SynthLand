package com.funguscow.synthland.synth

import com.funguscow.synthland.synth.IoUtil.s
import java.io.Closeable
import java.io.DataOutput
import java.io.File
import java.io.RandomAccessFile
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

object IoUtil {
    fun write(dst: DataOutput, buffer: ByteBuffer, value: Long) {
        buffer.rewind()
        buffer.putLong(value)
        dst.write(buffer.array())
    }
    fun write(dst: DataOutput, buffer: ByteBuffer, value: Int) {
        buffer.rewind()
        buffer.putInt(value)
        dst.write(buffer.array(), 0, Int.SIZE_BYTES)
    }
    fun write(dst: DataOutput, buffer: ByteBuffer, value: Short) {
        buffer.rewind()
        buffer.putShort(value)
        dst.write(buffer.array(), 0, Short.SIZE_BYTES)
    }
    val Int.s get() = toShort()
}

object WaveHeaderValues {
    val FILE_SIZE_OFFSET = 4L
    val DATA_SIZE_OFFSET = 40L
    val DATA_OFFSET = 44L
    val EXTRA_HEADER_SIZE = DATA_OFFSET - FILE_SIZE_OFFSET - 4L
}

class WaveWriter : Closeable {
    // Invariant: null when not open
    private var file: RandomAccessFile? = null
    private val buffer = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    // Invariant: 0 when not open
    private var dataSize = 0

    fun open(file: File) {
        this.file = RandomAccessFile(file, "rw")
        dataSize = 0
    }

    fun open(path: String) {
        open(File(path))
    }

    fun writeHeader(format: AudioFormat) {
        file?.also {
            it.seek(0L)
            it.writeBytes("RIFF")
            IoUtil.write(it, buffer, 0)
            it.writeBytes("WAVEfmt ")
            IoUtil.write(it, buffer, 16)
            IoUtil.write(it, buffer, 1.s)
            IoUtil.write(it, buffer, format.channels.s)
            IoUtil.write(it, buffer, format.sampleRate.toInt())
            IoUtil.write(it, buffer, format.sampleRate.toInt() * format.sampleSizeInBits * format.channels / 8)
            IoUtil.write(it, buffer, (format.sampleSizeInBits * format.channels / 8).s)
            IoUtil.write(it, buffer, format.sampleSizeInBits.s)
            it.writeBytes("data")
            IoUtil.write(it, buffer, 0)
        } ?: throw IllegalStateException("No file open")
    }

    fun writeData(array: ByteArray, len: Int, offset: Int = 0) {
        file?.write(array, offset, len)
        dataSize += len
    }

    override fun close() {
        file?.use {
            it.seek(WaveHeaderValues.FILE_SIZE_OFFSET)
            IoUtil.write(it, buffer, dataSize + WaveHeaderValues.EXTRA_HEADER_SIZE.toInt())
            it.seek(WaveHeaderValues.DATA_SIZE_OFFSET)
            IoUtil.write(it, buffer, dataSize)
            println("Data size $dataSize written")
        }
        file = null
    }
}
