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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.overlayButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.startButton.setOnClickListener {
            if (running) stopPointer() else startPointer()
        }
    }

    private fun startPointer() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_permission), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        if (PointerAccessibilityService.instance == null) {
            Toast.makeText(this, getString(R.string.accessibility_permission), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraPermission)
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
        if (hands.isEmpty()) return
        val landmarks = hands[0]
        val index = landmarks[8]
        val thumb = landmarks[4]

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val x = ((1f - index.x()) * screenWidth).toInt().coerceIn(0, screenWidth)
        val y = (index.y() * screenHeight).toInt().coerceIn(0, screenHeight)

        val dx = index.x() - thumb.x()
        val dy = index.y() - thumb.y()
        val distance = sqrt(dx * dx + dy * dy)
        val pinching = distance < 0.055f

        runOnUiThread {
            PointerBridge.setPosition(this, x, y, pinching)
            if (pinching && !wasPinching) {
                PointerAccessibilityService.tap(x.toFloat(), y.toFloat())
            }
        }
        wasPinching = pinching
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
