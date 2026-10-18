package com.gpratyush.musictheory.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Plays piano notes from recorded samples (Salamander Grand Piano, sampled every
 * minor third and pitch-shifted to the notes in between), the same files and
 * method as the iOS app. If the samples can't be loaded it falls back to a
 * simple built-in tone, so the app is never silent.
 *
 * Callers only use [noteOn] and [releaseAll].
 */
object PianoSynth {
    private const val TAG = "PianoSynth"
    private const val SAMPLE_RATE = 44_100
    private const val BLOCK = 256

    private class Sample(val note: Int, val frames: FloatArray, val rate: Int)

    private class Voice(
        val sample: Sample?,
        val step: Double,
        val frequency: Double,
        val gain: Double,
    ) {
        var position = 0.0
        var time = 0.0
        var releasedAt: Double? = null
    }

    private val lock = Any()
    private var samples: List<Sample> = emptyList()
    private val voices = mutableListOf<Voice>()
    @Volatile private var loaded = false
    private var thread: Thread? = null

    /** Built-in tone used only if samples are missing. */
    private val partials = doubleArrayOf(1.0, 0.45, 0.25, 0.12, 0.08, 0.04)

    /** Loads the samples ahead of time so the first note plays straight away. */
    fun prepare(context: Context) {
        val app = context.applicationContext
        Thread({ loadSamples(app); startIfNeeded() }, "piano-load").start()
    }

    /** Starts the given MIDI notes. Earlier notes keep ringing until released. */
    fun noteOn(notes: List<Int>, velocity: Double = 0.8) {
        startIfNeeded()
        synchronized(lock) {
            for (note in notes) {
                val frequency = 440 * 2.0.pow((note - 69) / 12.0)
                val nearest = samples.minByOrNull { abs(it.note - note) }
                if (nearest != null) {
                    val shift = 2.0.pow((note - nearest.note) / 12.0)
                    voices += Voice(nearest, shift * nearest.rate / SAMPLE_RATE, frequency, velocity)
                } else {
                    voices += Voice(null, 0.0, frequency, velocity * 0.6)
                }
            }
        }
    }

    /** Lets every sounding note fade out quickly, like lifting the keys. */
    fun releaseAll() {
        synchronized(lock) {
            for (v in voices) if (v.releasedAt == null) v.releasedAt = v.time
        }
    }

    @Synchronized
    private fun startIfNeeded() {
        if (thread != null) return
        thread = Thread(::runOutput, "piano-out").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun runOutput() {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, BLOCK * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track.play()
        val buffer = FloatArray(BLOCK)
        while (true) {
            render(buffer)
            track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun render(out: FloatArray) {
        val dt = 1.0 / SAMPLE_RATE
        synchronized(lock) {
            if (voices.isEmpty()) {
                out.fill(0f)
                return
            }
            for (f in out.indices) {
                var mix = 0.0
                for (v in voices) {
                    val t = v.time
                    var env = v.releasedAt?.let { exp(-(t - it) / 0.15) } ?: 1.0
                    val s = v.sample
                    if (s != null) {
                        val i = v.position.toInt()
                        if (i + 1 < s.frames.size) {
                            val frac = (v.position - i).toFloat()
                            val value = s.frames[i] + (s.frames[i + 1] - s.frames[i]) * frac
                            mix += value * env * v.gain
                        }
                        v.position += v.step
                    } else {
                        env *= min(1.0, t / 0.004) * exp(-t / 1.6)
                        var tone = 0.0
                        for (k in partials.indices) {
                            val n = k + 1.0
                            tone += partials[k] * exp(-t * n * 0.8) * sin(2 * PI * v.frequency * n * t)
                        }
                        mix += tone * env * v.gain * 0.25
                    }
                    v.time = t + dt
                }
                out[f] = tanh(mix * 0.9).toFloat() // soft limit so big chords don't clip
            }
            voices.removeAll { v ->
                val r = v.releasedAt
                when {
                    r != null && v.time - r > 1 -> true
                    v.sample != null -> v.position.toInt() + 1 >= v.sample.frames.size
                    else -> v.time > 6
                }
            }
        }
    }

    private fun loadSamples(context: Context) {
        if (loaded) return
        val names = mutableListOf("A1" to 33)
        for (octave in 2..6) {
            for ((name, pc) in listOf("C" to 0, "Ds" to 3, "Fs" to 6, "A" to 9)) {
                names += "$name$octave" to 12 * (octave + 1) + pc
            }
        }
        names += "C7" to 96

        val result = names.mapNotNull { (name, note) ->
            try {
                decode(context, "piano_$name.m4a")?.let { (frames, rate) -> Sample(note, frames, rate) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load $name", e)
                null
            }
        }
        synchronized(lock) { samples = result }
        loaded = true
        Log.i(TAG, "Loaded ${result.size} piano samples")
    }

    /** Decodes an AAC asset to mono float samples. */
    private fun decode(context: Context, asset: String): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        context.assets.openFd(asset).use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var out = FloatArray(rate * 6)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (outIndex >= 0) {
                    val buf = codec.getOutputBuffer(outIndex)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (shorts.remaining() >= channels) {
                        var sum = 0f
                        repeat(channels) { sum += shorts.get() / 32768f }
                        if (count == out.size) out = out.copyOf(out.size * 2)
                        out[count++] = sum / channels
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            codec.stop()
            codec.release()
            return out.copyOf(count) to rate
        } finally {
            extractor.release()
        }
    }
}
