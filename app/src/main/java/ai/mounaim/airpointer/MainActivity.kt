package ai.mounaim.airpointer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ai.mounaim.airpointer.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val cameraPermission = 100
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private var running = false
    private var wasPinching = false

    private var smoothedX = -1f
    private var smoothedY = -1f
    private var pinchStartTime = 0L
    private var pinchStartX = 0f
    private var pinchStartY = 0f
    private var longPressSent = false
    private var lastTapTime = 0L
    private var openPalmStart: Pair<Float, Float>? = null
    private var gestureCooldownUntil = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.overlayButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        binding.startButton.setOnClickListener {
            if (running) stopPointer() else startPointer()
        }
    }

    private fun startPointer() {
        if (PointerAccessibilityService.instance == null) {
            Toast.makeText(this, getString(R.string.accessibility_permission), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraPermission)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_permission), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }

        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ -> handleResult(result) }
                .setErrorListener { it.printStackTrace() }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (error: Throwable) {
            binding.status.text = "تعذر تحميل محرك تتبع اليد: ${error.message}"
            return
        }

        PointerBridge.start(this)
        startCamera()
        running = true
        binding.startButton.text = getString(R.string.stop_pointer)
        binding.status.text = "المؤشر نشط — قرّب الإبهام والسبابة للنقر"
    }

    private fun stopPointer() {
        running = false
        handLandmarker?.close()
        handLandmarker = null
        PointerBridge.stop(this)
        binding.startButton.text = getString(R.string.start_pointer)
        binding.status.text = "تم إيقاف المؤشر"
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                if (!running) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).build()
                handLandmarker?.detectAsync(mpImage, System.nanoTime() / 1_000_000)
                imageProxy.close()
            }

            provider.unbindAll()
            provider.bindToLifecycle(this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleResult(result: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult) {
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            openPalmStart = null
            return
        }
        val landmarks = hands[0]
        val index = landmarks[8]
        val thumb = landmarks[4]

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val rawX = ((1f - index.x()) * screenWidth).coerceIn(0f, screenWidth.toFloat())
        val rawY = (index.y() * screenHeight).coerceIn(0f, screenHeight.toFloat())

        if (smoothedX < 0f) {
            smoothedX = rawX
            smoothedY = rawY
        } else {
            smoothedX = smoothedX * 0.38f + rawX * 0.62f
            smoothedY = smoothedY * 0.38f + rawY * 0.62f
        }
        val x = smoothedX
        val y = smoothedY

        val dx = index.x() - thumb.x()
        val dy = index.y() - thumb.y()
        val distance = sqrt(dx * dx + dy * dy)
        val pinching = distance < 0.055f
        val now = System.currentTimeMillis()

        if (pinching && pinchStartTime == 0L) {
            pinchStartTime = now
            pinchStartX = x
            pinchStartY = y
            longPressSent = false
        }

        if (pinching) {
            val holdDuration = now - pinchStartTime
            val movement = kotlin.math.abs(x - pinchStartX) + kotlin.math.abs(y - pinchStartY)
            if (holdDuration >= 650 && movement < 45f && !longPressSent) {
                PointerAccessibilityService.longPress(x, y)
                binding.status.text = "ضغطة مطولة — تحريك الإصبع للسحب"
                longPressSent = true
            }
        } else if (pinchStartTime != 0L) {
            val duration = now - pinchStartTime
            val movementX = kotlin.math.abs(x - pinchStartX)
            val movementY = kotlin.math.abs(y - pinchStartY)
            val moved = movementX + movementY

            if (duration >= 220 && moved > 55f) {
                PointerAccessibilityService.swipe(pinchStartX, pinchStartY, x, y, 360)
                binding.status.text = "سحب جوي تم تنفيذه"
            } else if (duration < 650 && !longPressSent) {
                if (now - lastTapTime in 150L..420L) {
                    PointerAccessibilityService.doubleTap(x, y)
                    binding.status.text = "نقر مزدوج"
                    lastTapTime = 0L
                } else {
                    PointerAccessibilityService.tap(x, y)
                    binding.status.text = "نقر جوي"
                    lastTapTime = now
                }
            }
            pinchStartTime = 0L
            longPressSent = false
        }

        val indexUp = landmarks[8].y() < landmarks[6].y() - 0.025f
        val middleUp = landmarks[12].y() < landmarks[10].y() - 0.025f
        val ringUp = landmarks[16].y() < landmarks[14].y() - 0.025f
        val pinkyUp = landmarks[20].y() < landmarks[18].y() - 0.025f
        val openPalm = indexUp && middleUp && ringUp && pinkyUp

        if (openPalm && now >= gestureCooldownUntil) {
            val palmCenterX = ((1f - (landmarks[5].x() + landmarks[17].x()) / 2f) * screenWidth)
            val palmCenterY = ((landmarks[5].y() + landmarks[17].y()) / 2f * screenHeight)

            if (openPalmStart == null) {
                openPalmStart = Pair(palmCenterX, palmCenterY)
            } else {
                val start = openPalmStart!!
                val deltaX = palmCenterX - start.first
                val deltaY = palmCenterY - start.second

                if (kotlin.math.abs(deltaX) > 150f && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.15f) {
                    if (deltaX < 0f) PointerAccessibilityService.back()
                    else PointerAccessibilityService.recentApps()
                    binding.status.text = if (deltaX < 0f) "إيماءة كف: رجوع" else "إيماءة كف: التطبيقات الحديثة"
                    openPalmStart = null
                    gestureCooldownUntil = now + 900
                } else if (kotlin.math.abs(deltaY) > 140f && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.15f) {
                    if (deltaY < 0f) PointerAccessibilityService.scrollDown(palmCenterX, palmCenterY)
                    else PointerAccessibilityService.scrollUp(palmCenterX, palmCenterY)
                    binding.status.text = if (deltaY < 0f) "إيماءة كف: تمرير للأسفل" else "إيماءة كف: تمرير للأعلى"
                    openPalmStart = null
                    gestureCooldownUntil = now + 900
                }
            }
        } else if (!openPalm) {
            openPalmStart = null
        }

        runOnUiThread {
            PointerBridge.setPosition(this, x.toInt(), y.toInt(), pinching)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermission && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPointer()
    }

    override fun onDestroy() {
        handLandmarker?.close()
        analyzerExecutor.shutdown()
        super.onDestroy()
    }
}
