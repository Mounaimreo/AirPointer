package ai.mounaim.airpointer

import android.content.Context
import android.content.Intent

object PointerBridge {
    @Volatile var overlayActive = false

    fun setPosition(context: Context, x: Int, y: Int, pinch: Boolean) {
        val intent = Intent(context, PointerOverlayService::class.java).apply {
            action = PointerOverlayService.ACTION_UPDATE
            putExtra("x", x)
            putExtra("y", y)
            putExtra("pinch", pinch)
        }
        context.startService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, PointerOverlayService::class.java))
        overlayActive = false
    }
}
