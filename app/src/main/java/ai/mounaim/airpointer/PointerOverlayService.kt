package ai.mounaim.airpointer

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.View

class PointerOverlayService : Service() {
    companion object { const val ACTION_UPDATE = "update_pointer" }

    private lateinit var windowManager: WindowManager
    private var pointerView: PointerView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        pointerView = PointerView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        windowManager.addView(pointerView, params)
        PointerBridge.overlayActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE) {
            val x = intent.getIntExtra("x", 0)
            val y = intent.getIntExtra("y", 0)
            val pinch = intent.getBooleanExtra("pinch", false)
            val view = pointerView ?: return START_STICKY
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = x - 28
            params.y = y - 28
            windowManager.updateViewLayout(view, params)
            view.setPinching(pinch)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pointerView?.let { windowManager.removeView(it) }
        pointerView = null
        PointerBridge.overlayActive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class PointerView(context: android.content.Context) : FrameLayout(context) {
    init { setWillNotDraw(false) }
    private var isPressed = false

    fun setPinching(pinching: Boolean) {
        if (isPressed != pinching) {
            isPressed = pinching
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(56, 56)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = if (isPressed) 0xFFFFB347.toInt() else 0xFF21D3C4.toInt()
        paint.alpha = 235
        canvas.drawCircle(28f, 28f, if (isPressed) 17f else 13f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFFF2FFFD.toInt()
        canvas.drawCircle(28f, 28f, if (isPressed) 23f else 20f, paint)
    }
}
