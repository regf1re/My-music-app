package com.example.musicplayer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Насколько позже звука рисуются полоски (звук проходит через буфер, поэтому картинку чуть задерживаем). */
const val VISUALIZER_DELAY_MS = 300L

/** Общая «шина»: плеер кладёт сюда спектр звука, экран плеера его читает. */
object SpectrumBus {
    const val BANDS = 32
    private const val CAPACITY = 64

    private val lock = Any()
    private val times = LongArray(CAPACITY)
    private val frames = Array(CAPACITY) { FloatArray(BANDS) }
    private var head = 0
    private var count = 0

    /** Включается, когда визуализатор виден на экране: иначе спектр не считается (экономит заряд). */
    @Volatile
    var active = false

    fun push(values: FloatArray, timeNs: Long) {
        synchronized(lock) {
            System.arraycopy(values, 0, frames[head], 0, BANDS)
            times[head] = timeNs
            head = (head + 1) % CAPACITY
            if (count < CAPACITY) count++
        }
    }

    /** Берёт спектр, который соответствует моменту «сейчас минус delayMs». */
    fun read(delayMs: Long, out: FloatArray) {
        val target = System.nanoTime() - delayMs * 1_000_000L
        synchronized(lock) {
            var i = head
            for (n in 0 until count) {
                i = (i - 1 + CAPACITY) % CAPACITY
                if (times[i] <= target) {
                    System.arraycopy(frames[i], 0, out, 0, BANDS)
                    return
                }
            }
        }
        out.fill(0f)
    }
}

private object Fft {
    /** Быстрое преобразование Фурье (на месте), размер должен быть степенью двойки. */
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenRe = cos(ang).toFloat()
            val wlenIm = sin(ang).toFloat()
            val half = len / 2
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vRe = re[b] * wRe - im[b] * wIm
                    val vIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - vRe
                    im[b] = im[a] - vIm
                    re[a] = re[a] + vRe
                    im[a] = im[a] + vIm
                    val nwRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nwRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * Обработчик звука для плеера: пропускает звук без изменений
 * и параллельно считает спектр для анимированных полосок.
 */
class SpectrumProcessor : BaseAudioProcessor() {
    private companion object {
        const val N = 1024
    }

    private var channels = 2
    private var sampleRate = 44100
    private var encoding = C.ENCODING_PCM_16BIT

    private val window = FloatArray(N)
    private var filled = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val hann = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }
    private val out = FloatArray(SpectrumBus.BANDS)
    private var edges = FloatArray(SpectrumBus.BANDS + 1)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            // Другой формат звука: обработчик отключается и звук идёт как есть
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = max(inputAudioFormat.channelCount, 1)
        sampleRate = inputAudioFormat.sampleRate
        encoding = inputAudioFormat.encoding
        filled = 0

        val fMin = 60f
        val fMax = min(16000f, sampleRate / 2f - 100f)
        edges = FloatArray(SpectrumBus.BANDS + 1) { b ->
            fMin * (fMax / fMin).pow(b.toFloat() / SpectrumBus.BANDS)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        filled = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (SpectrumBus.active) {
            analyze(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        } else {
            filled = 0
        }
        val outBuffer = replaceOutputBuffer(remaining)
        outBuffer.put(inputBuffer)
        outBuffer.flip()
    }

    private fun analyze(buf: ByteBuffer) {
        if (encoding == C.ENCODING_PCM_16BIT) {
            val frameBytes = 2 * channels
            while (buf.remaining() >= frameBytes) {
                var sum = 0f
                for (ch in 0 until channels) sum += buf.short / 32768f
                addSample(sum / channels)
            }
        } else {
            val frameBytes = 4 * channels
            while (buf.remaining() >= frameBytes) {
                var sum = 0f
                for (ch in 0 until channels) sum += buf.float
                addSample(sum / channels)
            }
        }
    }

    private fun addSample(sample: Float) {
        window[filled++] = sample
        if (filled == N) {
            computeSpectrum()
            filled = 0
        }
    }

    private fun computeSpectrum() {
        for (i in 0 until N) {
            re[i] = window[i] * hann[i]
            im[i] = 0f
        }
        Fft.transform(re, im)
        val binHz = sampleRate.toFloat() / N
        for (b in 0 until SpectrumBus.BANDS) {
            val k0 = (edges[b] / binHz).toInt().coerceIn(1, N / 2 - 1)
            val k1 = (edges[b + 1] / binHz).toInt().coerceIn(k0, N / 2 - 1)
            var peak = 0f
            for (k in k0..k1) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                if (m > peak) peak = m
            }
            val magnitude = peak / (N / 4f)
            val db = 20f * log10(magnitude + 1e-7f) + b * 0.6f
            out[b] = ((db + 75f) / 60f).coerceIn(0f, 1f)
        }
        SpectrumBus.push(out, System.nanoTime())
    }
}

/** Анимированные полоски, которые двигаются в такт музыке. */
@Composable
fun SpectrumVisualizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val bars = remember { FloatArray(SpectrumBus.BANDS) }
    val raw = remember { FloatArray(SpectrumBus.BANDS) }
    var tick by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        SpectrumBus.active = true
        onDispose { SpectrumBus.active = false }
    }

    LaunchedEffect(playing) {
        var last = 0L
        while (playing || bars.any { it > 0.004f }) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                if (playing) SpectrumBus.read(VISUALIZER_DELAY_MS, raw) else raw.fill(0f)
                for (i in bars.indices) {
                    val target = raw[i]
                    bars[i] = if (target > bars[i]) {
                        bars[i] + (target - bars[i]) * 0.55f
                    } else {
                        max(target, bars[i] - dt * 1.2f)
                    }
                }
                tick = now
            }
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE")
        val redraw = tick
        val n = SpectrumBus.BANDS
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (n - 1)) / n
        val minHeight = 3.dp.toPx()
        for (i in 0 until n) {
            val h = max(bars[i] * size.height, minHeight)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
