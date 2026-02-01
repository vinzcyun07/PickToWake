package com.wyn.ptw

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class WakeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null

    private lateinit var powerManager: PowerManager

    // Khi màn hình tắt, lấy mốc pitch hiện tại làm "0°"
    private var baselinePitchDeg: Float? = null

    // chỉ lắng nghe cảm biến khi screen OFF để tiết kiệm pin
    private var monitoring = false

    // Ngưỡng: mục tiêu ~90°, cho phép sai số
    private val targetDeg = 90f
    private val toleranceDeg = 12f
    private val triggerCooldownMs = 1500L
    private var lastTriggerAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // reset baseline khi màn hình tắt
                    baselinePitchDeg = null
                    startMonitoring()
                }
                Intent.ACTION_SCREEN_ON -> {
                    stopMonitoring()
                }
                Intent.ACTION_USER_PRESENT -> {
                    stopMonitoring()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        NotificationHelper.createNotificationChannel(this)
        startForeground(NotificationHelper.NOTIF_ID, NotificationHelper.buildForegroundNotification(this))

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )

        // Nếu service được start khi máy đang khóa/tắt màn hình thì bắt đầu luôn
        if (!powerManager.isInteractive) {
            startMonitoring()
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tự sống lại nếu bị kill
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitoring) return
        if (accel == null) return

        monitoring = true
        // NORMAL là đủ dùng + tiết kiệm pin
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopMonitoring() {
        if (!monitoring) return
        monitoring = false
        sensorManager.unregisterListener(this)
        baselinePitchDeg = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!monitoring) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Nếu màn hình đang bật thì không làm gì
        if (powerManager.isInteractive) {
            stopMonitoring()
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Tính pitch (độ nghiêng trước/sau) từ accelerometer.
        // pitch = atan2(-x, sqrt(y^2 + z^2)) (rad) -> deg
        val pitchRad = atan2(-x, sqrt(y * y + z * z))
        val pitchDeg = (pitchRad * (180.0 / Math.PI)).toFloat()

        // Lần đầu khi screen OFF: set baseline (mốc 0°)
        val base = baselinePitchDeg
        if (base == null) {
            baselinePitchDeg = pitchDeg
            return
        }

        // delta so với baseline, chuẩn hóa về trị tuyệt đối
        val delta = abs(pitchDeg - base)

        // Trigger khi ~90°
        if (abs(delta - targetDeg) <= toleranceDeg) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerAt >= triggerCooldownMs) {
                lastTriggerAt = now
                wakeScreen()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun wakeScreen() {
        // Nếu vì lý do nào đó màn hình đã bật thì thôi
        if (powerManager.isInteractive) return

        // WakeLock để bật màn hình (deprecated flags nhưng vẫn hoạt động rộng rãi)
        val wlFlags = (PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP)
        val wl = powerManager.newWakeLock(wlFlags, "ptw:wakelock")
        try {
            wl.acquire(1500) // giữ ngắn để tránh hao pin
        } catch (_: Throwable) {
            // nếu fail, không crash
        } finally {
            try {
                if (wl.isHeld) wl.release()
            } catch (_: Throwable) {}
        }
    }

    companion object {
        fun start(context: Context) {
            if (!SettingsStore.isEnabled(context)) return
            val i = Intent(context, WakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, WakeService::class.java)
            context.stopService(i)
        }
    }
}
