package ai.mounaim.airpointer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.accessibility.AccessibilityEvent

class PointerAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var pointerView: PointerView? = null

    companion object {
        @Volatile var instance: PointerAccessibilityService? = null
        fun tap(x: Float, y: Float) = instance?.dispatchTap(x, y)
        fun doubleTap(x: Float, y: Float) = instance?.dispatchDoubleTap(x, y)
        fun updatePointer(x: Int, y: Int, pinching: Boolean) = instance?.showPointer(x, y, pinching)
        fun hidePointer() = instance?.removePointer()
        fun longPress(x: Float, y: Float) = instance?.dispatchLongPress(x, y)
        fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 280) =
            instance?.dispatchSwipe(startX, startY, endX, endY, duration)
        fun scrollUp(x: Float, y: Float) = instance?.dispatchSwipe(x, y - 220f, x, y + 220f, 300)
        fun scrollDown(x: Float, y: Float) = instance?.dispatchSwipe(x, y + 220f, x, y - 220f, 300)
        fun back() = instance?.performGlobalAction(GLOBAL_ACTION_BACK)
        fun home() = instance?.performGlobalAction(GLOBAL_ACTION_HOME)
        fun recentApps() = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removePointer()
        instance = null
        super.onDestroy()
    }

    private fun showPointer(x: Int, y: Int, pinching: Boolean) {
        var view = pointerView
        if (view == null) {
            view = PointerView(this)
            val params = WindowManager.LayoutParams(
                56,
                56,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            windowManager.addView(view, params)
            pointerView = view
        }

        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = x - 28
        params.y = y - 28
        windowManager.updateViewLayout(view, params)
        view.setPinching(pinching)
    }

    private fun removePointer() {
        pointerView?.let(windowManager::removeView)
        pointerView = null
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchDoubleTap(x: Float, y: Float) {
        val first = Path().apply { moveTo(x, y) }
        val second = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(first, 0, 70))
            .addStroke(GestureDescription.StrokeDescription(second, 140, 70))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 500)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}

private class PointerView(context: android.content.Context) : FrameLayout(context) {
    private var isPinching = false

    init { setWillNotDraw(false) }

    fun setPinching(pinching: Boolean) {
        if (isPinching != pinching) {
            isPinching = pinching
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(56, 56)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = if (isPinching) 0xFFFFB347.toInt() else 0xFF21D3C4.toInt()
        canvas.drawCircle(28f, 28f, if (isPinching) 17f else 13f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0xFFF2FFFD.toInt()
        canvas.drawCircle(28f, 28f, if (isPinching) 23f else 20f, paint)
    }
}
