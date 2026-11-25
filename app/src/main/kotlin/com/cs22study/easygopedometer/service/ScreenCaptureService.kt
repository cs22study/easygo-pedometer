package com.cs22study.easygopedometer.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

data class CapturedScreenshot(
    val uri: Uri,
    val fileName: String,
    val displayPath: String,
    val thumbnail: Bitmap,
    val capturedAtMillis: Long = System.currentTimeMillis(),
)

object ScreenCaptureBus {
    private val _latest = MutableStateFlow<CapturedScreenshot?>(null)
    val latest: StateFlow<CapturedScreenshot?> = _latest.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun emitSuccess(screenshot: CapturedScreenshot) {
        _error.value = null
        _latest.value = screenshot
    }

    fun emitError(message: String) {
        _error.value = message
    }

    fun clearPreview() {
        _latest.value = null
    }

    fun clearError() {
        _error.value = null
    }
}

/**
 * Short-lived foreground service that captures a full physical screen screenshot (including Android SystemUI
 * status bar time & battery) via MediaProjection, saves PNG to Gallery, and shuts down immediately.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captured = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundCompat(buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
            ScreenCaptureBus.emitError("未取得螢幕擷取授權")
            stopGracefully()
            return START_NOT_STICKY
        }

        // Wait briefly so the system consent dialog completely fades out before snapping the frame.
        mainHandler.postDelayed({
            runCatching {
                startCapture(resultCode, data)
            }.onFailure { ex ->
                ScreenCaptureBus.emitError("截圖失敗：${ex.localizedMessage ?: "系統不支援"}")
                stopGracefully()
            }
        }, 350L)

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection unavailable")
        mediaProjection = projection

        val thread = HandlerThread("EasyGoScreenCapture").apply { start() }
        captureThread = thread
        val bgHandler = Handler(thread.looper)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanupResources()
            }
        }, bgHandler)

        val (width, height, densityDpi) = resolveScreenMetrics()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            if (!captured.compareAndSet(false, true)) return@setOnImageAvailableListener
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image, width, height)
                val saved = saveBitmapToGallery(bitmap)
                ScreenCaptureBus.emitSuccess(saved)
            } catch (ex: Exception) {
                ScreenCaptureBus.emitError("儲存截圖失敗：${ex.localizedMessage ?: "未知錯誤"}")
            } finally {
                image.close()
                mainHandler.post { stopGracefully() }
            }
        }, bgHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "EasyGoFullScreenCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            bgHandler,
        )
    }

    private fun resolveScreenMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val densityDpi = resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Triple(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1), densityDpi)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Triple(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1), metrics.densityDpi)
        }
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val fullWidth = width + rowPadding / pixelStride
        val raw = Bitmap.createBitmap(fullWidth, height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buffer)
        return if (fullWidth != width) {
            Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
        } else {
            raw
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): CapturedScreenshot {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "EasyGo_$stamp.png"
        val thumbW = 140
        val thumbH = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * thumbW).toInt().coerceAtLeast(140)
        val thumb = Bitmap.createScaledBitmap(bitmap, thumbW, thumbH, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/EasyGo-Pedometer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("無法建立相簿檔案")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            bitmap.recycle()
            return CapturedScreenshot(
                uri = uri,
                fileName = fileName,
                displayPath = "Pictures/EasyGo-Pedometer/$fileName",
                thumbnail = thumb,
            )
        }

        @Suppress("DEPRECATION")
        val publicPics = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "EasyGo-Pedometer")
        val dir = if (publicPics.exists() || publicPics.mkdirs()) {
            publicPics
        } else {
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "EasyGo-Pedometer").apply { mkdirs() }
        }
        val outFile = File(dir, fileName)
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        var scannedUri: Uri = Uri.fromFile(outFile)
        val scanLock = Object()
        var scanDone = false
        MediaScannerConnection.scanFile(this, arrayOf(outFile.absolutePath), arrayOf("image/png")) { _, uri ->
            if (uri != null) scannedUri = uri
            synchronized(scanLock) {
                scanDone = true
                scanLock.notifyAll()
            }
        }
        synchronized(scanLock) {
            if (!scanDone) scanLock.wait(1200L)
        }

        return CapturedScreenshot(
            uri = scannedUri,
            fileName = fileName,
            displayPath = outFile.absolutePath,
            thumbnail = thumb,
        )
    }

    private fun stopGracefully() {
        cleanupResources()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun cleanupResources() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
    }

    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "畫面截圖",
                NotificationManager.IMPORTANCE_LOW,
            )
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("EasyGo 螢幕截圖中")
            .setContentText("正在擷取完整活動畫面...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "easygo_screen_capture_channel"
        private const val NOTIFICATION_ID = 1002
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
