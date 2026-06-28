// SignalCollector.kt
// Veloris Sentinel Android SDK — Signal Collection
//
// Collects behavioural biometric signals throughout the session.
// CPU overhead target: < 2% (sensors sampled at low rate; signals batched).

package io.veloris.sdk.signals

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.veloris.sdk.models.VelorisConfig
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

internal class SignalCollector(
    context: Context,
    private val config: VelorisConfig
) : SensorEventListener {

    // MARK: - State

    private var sessionId: String? = null
    private var sessionToken: String? = null
    private var isRunning = false
    private var isPaused = false

    // MARK: - Sensor manager

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // MARK: - Signal buffers (thread-safe)

    private val keystrokeBuffer = LinkedBlockingQueue<KeystrokeEvent>(200)
    private val scrollBuffer    = LinkedBlockingQueue<ScrollEvent>(200)
    private val tiltBuffer      = LinkedBlockingQueue<TiltEvent>(200)
    private val touchBuffer     = LinkedBlockingQueue<TouchEvent>(200)

    private val maxBatchSize = 50
    private val flushIntervalMs = 10_000L

    // MARK: - Coroutine scope for background flush

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null

    // MARK: - Lifecycle

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
        scope.launch { flush() }  // final flush
        sessionId    = null
        sessionToken = null
    }

    // MARK: - Sensor management

    private fun startSensors() {
        // Sample at SENSOR_DELAY_NORMAL (~200ms) — keeps CPU overhead minimal
        gyroscope?.let     { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    // MARK: - SensorEventListener

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRunning || isPaused) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val e = TiltEvent(
                    pitch = event.values[0].toDouble(),
                    roll  = event.values[1].toDouble(),
                    yaw   = event.values[2].toDouble(),
                    timestamp = System.currentTimeMillis()
                )
                tiltBuffer.offer(e)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Accelerometer enriches motion context but we keep it lightweight
                // (stored with tilt as composite device motion signal)
            }
        }

        checkBatchThreshold()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { /* no-op */ }

    // MARK: - Manual signal recording (called from app UI layer)

    fun recordKeystroke(keyDuration: Long, interKeyInterval: Long) {
        val event = KeystrokeEvent(
            keyDuration       = keyDuration,
            interKeyInterval  = interKeyInterval,
            timestamp         = System.currentTimeMillis()
        )
        keystrokeBuffer.offer(event)
        checkBatchThreshold()
    }

    fun recordScroll(velocityX: Float, velocityY: Float) {
        val event = ScrollEvent(
            velocityX = velocityX.toDouble(),
            velocityY = velocityY.toDouble(),
            timestamp = System.currentTimeMillis()
        )
        scrollBuffer.offer(event)
        checkBatchThreshold()
    }

    fun recordTouch(pressure: Float, size: Float) {
        val event = TouchEvent(
            pressure  = pressure.toDouble(),
            size      = size.toDouble(),
            timestamp = System.currentTimeMillis()
        )
        touchBuffer.offer(event)
        checkBatchThreshold()
    }

    // MARK: - Flush logic

    private fun checkBatchThreshold() {
        val total = keystrokeBuffer.size + scrollBuffer.size + tiltBuffer.size + touchBuffer.size
        if (total >= maxBatchSize) {
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
            batch = SignalBatch(
                sessionId  = sid,
                keystrokes = keystrokes,
                scrolls    = scrolls,
                tilts      = tilts,
                touches    = touches
            ),
            sessionToken = token,
            baseUrl      = config.environment.baseUrl
        )
    }

    private fun <T> drainQueue(queue: LinkedBlockingQueue<T>): List<T> {
        val result = mutableListOf<T>()
        queue.drainTo(result)
        return result
    }
}

// MARK: - Signal data classes

internal data class KeystrokeEvent(
    val keyDuration: Long,
    val interKeyInterval: Long,
    val timestamp: Long
)

internal data class ScrollEvent(
    val velocityX: Double,
    val velocityY: Double,
    val timestamp: Long
)

internal data class TiltEvent(
    val pitch: Double,
    val roll: Double,
    val yaw: Double,
    val timestamp: Long
)

internal data class TouchEvent(
    val pressure: Double,
    val size: Double,
    val timestamp: Long
)

internal data class SignalBatch(
    val sessionId: String,
    val keystrokes: List<KeystrokeEvent>,
    val scrolls: List<ScrollEvent>,
    val tilts: List<TiltEvent>,
    val touches: List<TouchEvent>
)
