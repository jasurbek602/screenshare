package com.example.screenshare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    // Tizimning ekran ulashish ruxsat dialogidan natijani qabul qilamiz
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Foydalanuvchi rozilik berdi — Foreground Service'ni ishga tushiramiz
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
        // Agar foydalanuvchi rad etsa — hech narsa boshlanmaydi (tizim tomonidan kafolatlanadi)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Android 13+ uchun bildirishnoma ruxsati
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            // Bu chaqiruv MAJBURIY ravishda tizim dialogini ochadi.
            // Kodda buni chetlab o'tish yoki avtomatik tasdiqlash imkoni yo'q —
            // bu Androidning xavfsizlik dizayni.
            val intent = projectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
        }
    }
}
