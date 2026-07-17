package com.example.screenshare

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

/**
 * Ekranni tasvirini oladigan va WebSocket orqali serverga JPEG kadr sifatida
 * uzatadigan Foreground Service.
 *
 * Bu Service faqat foydalanuvchi tizim rozilik dialogida "Ruxsat berish"ni
 * bosgandan keyingina ishga tushadi (MainActivity orqali).
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // Kadr olish/qayta ishlashni asosiy (UI) oqimidan alohida bajarish uchun
    private lateinit var handlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private var webSocketClient: WebSocketClient? = null

    // TODO: bu yerga o'z serveringiz manzilini yozing.
    // Masalan, kompyuteringizda server ishlab turgan bo'lsa, uning lokal IP manzili:
    private val SERVER_URL = "ws://192.168.1.100:8080"

    private val CHANNEL_ID = "screen_share_channel"
    private val NOTIF_ID = 1001

    // Tarmoqni ortiqcha yuklamaslik uchun kadrlar orasidagi minimal interval
    private var lastFrameTime = 0L
    private val MIN_FRAME_INTERVAL_MS = 200L // taxminan 5 fps; kerak bo'lsa o'zgartiring

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1) Avval majburiy, doimiy bildirishnomani ko'rsatamiz.
        //    Android 9+ da foreground service bildirishnomasiz ishlay olmaydi —
        //    foydalanuvchi doim uzatish faol ekanini biladi.
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == Activity.RESULT_OK && data != null) {
            connectWebSocket()

            val projectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            startCapture()
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    // ---- Serverga WebSocket orqali ulanish ----
    private fun connectWebSocket() {
        webSocketClient = object : WebSocketClient(URI(SERVER_URL)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                // Ulanish muvaffaqiyatli — endi kadrlarni yuborish mumkin
            }

            override fun onMessage(message: String?) {
                // Serverdan matnli xabar kelsa shu yerda ishlov beriladi
                // (masalan, kelajakda "sifatni pasaytir" kabi buyruqlar uchun)
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                // Ulanish uzildi
            }

            override fun onError(ex: Exception?) {
                ex?.printStackTrace()
            }
        }
        webSocketClient?.connect()
    }

    private fun startCapture() {
        val metrics: DisplayMetrics = resources.displayMetrics

        // Tarmoq va protsessor yukini kamaytirish uchun kadr o'lchamini kichraytiramiz
        val scale = 0.5
        val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenShareVD",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            // Kadr chastotasini cheklaymiz — belgilangan intervaldan tez-tez yubormaymiz
            if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) {
                image.close()
                return@setOnImageAvailableListener
            }
            lastFrameTime = now

            try {
                processFrame(image)
            } finally {
                image.close()
            }
        }, backgroundHandler)
    }

    /**
     * Xom (RGBA) kadrni Bitmap'ga aylantiradi, JPEG formatiga siqadi
     * va WebSocket orqali serverga bayt oqimi sifatida yuboradi.
     */
    private fun processFrame(image: Image) {
        val client = webSocketClient ?: return
        if (!client.isOpen) return

        val bitmap = imageToBitmap(image)

        val outputStream = ByteArrayOutputStream()
        // Sifatni 0-100 oralig'ida sozlash mumkin: kichikroq son = kichikroq hajm, past sifat
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val jpegBytes = outputStream.toByteArray()

        // Binary xabar sifatida yuboramiz — server buni JPEG kadr deb qabul qiladi
        client.send(jpegBytes)

        bitmap.recycle()
    }

    /** ImageReader'dan kelgan xom piksellarni Bitmap obyektiga aylantiradi. */
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Agar padding bo'lsa, aniq o'lchamga qirqib olamiz
        return if (rowPadding == 0) {
            bitmap
        } else {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            cropped
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Ekran uzatish",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ekran uzatilmoqda")
            .setContentText("Sizning ekraningiz hozir uzatilmoqda. To'xtatish uchun ilovaga qayting.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true) // foydalanuvchi tasodifan yopib qo'ymasligi uchun
            .build()
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        webSocketClient?.close()
        handlerThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
