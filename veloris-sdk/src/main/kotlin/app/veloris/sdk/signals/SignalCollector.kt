package app.veloris.sdk.signals

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.veloris.sdk.models.VelorisConfig
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

// ── Signal data classes (shared with SignalUploader) ──────────────────────────

internal data class KeystrokeEvent(val keyDuration: Long, val interKeyInterval: Long, val timestamp: Long)
internal data class ScrollEvent(val velocityX: Double, val velocityY: Double, val timestamp: Long)
internal data class TiltEvent(val pitch: Double, val roll: Double, val yaw: Double, val timestamp: Long)
internal data class TouchEvent(val pressure: Double, val size: Double, val timestamp: Long)

internal data class SignalBatch(
    val sessionId: String,
    val keystrokes: List<KeystrokeEvent>,
    val scrolls: List<ScrollEvent>,
    val tilts: List<TiltEvent>,
    val touches: List<TouchEvent>
)

// ── SignalCollector ───────────────────────────────────────────────────────────

internal class SignalCollector(
    context: Context,
    private val config: VelorisConfig
) : SensorEventListener {

    private var sessionId: String? = null
    private var sessionToken: String? = null
    private var isRunning = false
    private var isPaused = false

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val keystrokeBuffer = LinkedBlockingQueue<KeystrokeEvent>(200)
    private val scrollBuffer    = LinkedBlockingQueue<ScrollEvent>(200)
    private val tiltBuffer      = LinkedBlockingQueue<TiltEvent>(200)
    private val touchBuffer     = LinkedBlockingQueue<TouchEvent>(200)

    private val maxBatchSize = 50
    private val flushIntervalMs = 10_000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null

    fun start(sessionId: String, token: String) {
        this.sessionId    = sessionId
        this.sessionToken = token
        isRunning = true
        isPaused  = false
        startSensors()
        startFlushLoop()
    }

    fun pause() {
        if (!isRunning) return
        isPaused = true
        stopSensors()
        flushJob?.cancel()
    }

    fun resume() {
        if (!isRunning || !isPaused) return
        isPaused = false
        startSensors()
        startFlushLoop()
    }

    fun stop() {
        isRunning = false
        isPaused  = false
        stopSensors()
        flushJob?.cancel()
        scope.launch { flush() }
        sessionId    = null
        sessionToken = null
    }

    private fun startSensors() {
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun stopSensors() { sensorManager.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRunning || isPaused) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            tiltBuffer.offer(TiltEvent(
                pitch     = event.values[0].toDouble(),
                roll      = event.values[1].toDouble(),
                yaw       = event.values[2].toDouble(),
                timestamp = System.currentTimeMillis()
            ))
            checkBatchThreshold()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun recordKeystroke(keyDuration: Long, interKeyInterval: Long) {
        keystrokeBuffer.offer(KeystrokeEvent(keyDuration, interKeyInterval, System.currentTimeMillis()))
        checkBatchThreshold()
    }

    fun recordScroll(velocityX: Float, velocityY: Float) {
        scrollBuffer.offer(ScrollEvent(velocityX.toDouble(), velocityY.toDouble(), System.currentTimeMillis()))
        checkBatchThreshold()
    }

    fun recordTouch(pressure: Float, size: Float) {
        touchBuffer.offer(TouchEvent(pressure.toDouble(), size.toDouble(), System.currentTimeMillis()))
        checkBatchThreshold()
    }

    private fun checkBatchThreshold() {
        if (keystrokeBuffer.size + scrollBuffer.size + tiltBuffer.size + touchBuffer.size >= maxBatchSize) {
            scope.launch { flush() }
        }
    }

    private fun startFlushLoop() {
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    private suspend fun flush() {
        val keystrokes = drainQueue(keystrokeBuffer)
        val scrolls    = drainQueue(scrollBuffer)
        val tilts      = drainQueue(tiltBuffer)
        val touches    = drainQueue(touchBuffer)

        if (keystrokes.isEmpty() && scrolls.isEmpty() && tilts.isEmpty() && touches.isEmpty()) return

        val token = sessionToken ?: return
        val sid   = sessionId   ?: return

        SignalUploader.upload(
            batch = SignalBatch(sid, keystrokes, scrolls, tilts, touches),
            sessionToken = token,
            baseUrl = config.environment.baseUrl
        )
    }

    private fun <T> drainQueue(queue: LinkedBlockingQueue<T>): List<T> {
        val result = mutableListOf<T>()
        queue.drainTo(result)
        return result
    }
}
