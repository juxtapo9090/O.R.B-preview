package com.phantom.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.phantom.ai.R
import com.phantom.ai.data.SettingsRepository
import com.phantom.ai.ui.overlay.ChatEventBus
import com.phantom.ai.ui.overlay.ChatMessage
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.http.ContentType
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.Locale

class BackSocketService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var server: NettyApplicationEngine? = null
    private lateinit var settingsRepo: SettingsRepository
    private var tts: TextToSpeech? = null
    
    // MediaProjection properties
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0
    
    companion object {
        const val CHANNEL_ID = "phantom_backsocket"
        const val NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        tts = TextToSpeech(this, this)
        
        serviceScope.launch {
            val port = settingsRepo.settingsFlow.first().backSocketPort
            startServer(port)
        }
    }

    private fun startServer(port: Int) {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/health") {
                    call.respond(HealthResponse("alive", "O.R.B.", "0.2.0-orb"))
                }
                post("/notify") {
                    val req = call.receive<NotifyRequest>()
                    val msg = if (req.title.isNullOrBlank()) req.message else "${req.title}: ${req.message}"
                    
                    // 1. Chat panel injection (existing)
                    ChatEventBus.postMessage(ChatMessage("📞 $msg", isUser = false))
                    
                    // 2. System popup notification (NEW)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    val popupNotification = NotificationCompat.Builder(this@BackSocketService, "phantom_alerts")
                        .setContentTitle(req.title ?: "📞 O.R.B.")
                        .setContentText(req.message)
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_SOUND)
                        .setAutoCancel(true)
                        .build()
                    notificationManager.notify(System.currentTimeMillis().toInt(), popupNotification)
                    
                    call.respond(mapOf("status" to "ok"))
                }
                post("/tts") {
                    val req = call.receive<TtsRequest>()
                    val speed = req.speed ?: 1.0f
                    tts?.setSpeechRate(speed)
                    tts?.speak(req.text, TextToSpeech.QUEUE_FLUSH, null, "tts_req")
                    call.respond(mapOf("status" to "spoken"))
                }
                get("/status") {
                    call.respond(getPhoneStatus())
                }
                get("/screenshot") {
                    if (mediaProjection == null || imageReader == null) {
                        call.respond(mapOf("error" to "MediaProjection not granted. Please launch the O.R.B app and hit the Start capture button first."))
                        return@get
                    }
                    try {
                        // Attempt to acquire latest frame (can be null if screen is completely static since last capture)
                        val image = imageReader?.acquireLatestImage()
                        if (image == null) {
                            call.respond(mapOf("error" to "Failed to acquire image. Screen might be off or hasn't updated."))
                            return@get
                        }
                        
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * screenWidth
                        
                        // Create bitmap
                        val bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        
                        // Crop padding
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                        
                        // Compress to WebP
                        val stream = ByteArrayOutputStream()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            croppedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                        } else {
                            @Suppress("DEPRECATION")
                            croppedBitmap.compress(Bitmap.CompressFormat.WEBP, 80, stream)
                        }
                        val byteArray = stream.toByteArray()
                        
                        call.respondBytes(byteArray, ContentType.parse("image/webp"))
                    } catch (e: Exception) {
                        Log.e("PhantomEyes", "Error capturing screen", e)
                        call.respond(mapOf("error" to "Capture failed: \${e.message}"))
                    }
                }
                get("/context") {
                    call.respond(mapOf(
                        "foreground_app" to PhantomContext.foregroundApp,
                        "screen_text" to PhantomContext.screenText,
                        "last_notification" to PhantomContext.lastNotification,
                        "last_notification_app" to PhantomContext.lastNotificationApp
                    ))
                }
                post("/logcat") {
                    val req = call.receive<LogcatRequest>()
                    val output = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        LogcatBridge.capture(req.pkg ?: "", req.lines ?: 100)
                    }
                    call.respond(mapOf("output" to output, "pkg" to (req.pkg ?: "all")))
                }
            }
        }.start(wait = false)
        Log.i("BackSocket", "Server started on port $port")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.phantom.ai.INIT_MEDIA_PROJECTION") {
            val resultCode = intent.getIntExtra("result_code", 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("result_data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("result_data")
            }
            
            if (resultCode == android.app.Activity.RESULT_OK && resultData != null) {
                initMediaProjection(resultCode, resultData)
            }
        }
        return START_STICKY
    }
    
    private fun initMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d("PhantomEyes", "MediaProjection stopped by system")
            }
        }, null)
        
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics) // RealMetrics not needed if we want safe bounds
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PhantomScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        Log.d("PhantomEyes", "MediaProjection initialized successfully. Ready for screenshots.")
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        server?.stop(1000, 2000)
        tts?.stop()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Channel for the persistent background service notification
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Back Socket Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Remote bridge service"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
            
            // Add second channel for popup alerts (heads-up)
            val alertChannel = NotificationChannel(
                "phantom_alerts",
                "Bridge Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Popup alerts from remote agents"
                enableVibration(true)
            }
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("🔌 O.R.B. Back Socket")
        .setContentText("Listening for remote bridge commands")
        .setSmallIcon(android.R.drawable.stat_sys_warning) // Using standard system icon
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun getPhoneStatus(): PhoneStatus {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        val isCharging: Boolean = batteryStatus?.let { intent ->
            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "vpn"
                else -> "none"
            }
        } else {
            "unknown"
        }

        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isScreenOn = pm.isInteractive

        return PhoneStatus(
            battery = batteryPct?.toInt() ?: -1,
            charging = isCharging,
            network = networkStr,
            screen_on = isScreenOn
        )
    }
}

@Serializable
data class HealthResponse(val status: String, val app: String, val version: String)

@Serializable
data class NotifyRequest(val message: String, val title: String? = null)

@Serializable
data class TtsRequest(val text: String, val speed: Float? = null)

@Serializable
data class PhoneStatus(val battery: Int, val charging: Boolean, val network: String, val screen_on: Boolean)

@Serializable
data class LogcatRequest(val pkg: String? = null, val lines: Int? = null)
