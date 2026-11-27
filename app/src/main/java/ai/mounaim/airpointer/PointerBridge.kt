package ai.mounaim.airpointer

import android.content.Context
import android.content.Intent
import android.provider.Settings

object PointerBridge {
    fun setPosition(context: Context, x: Int, y: Int, pinching: Boolean) {
        if (Settings.canDrawOverlays(context)) {
            val intent = Intent(context, PointerOverlayService::class.java).apply {
                action = PointerOverlayService.ACTION_UPDATE
                putExtra("x", x)
                putExtra("y", y)
                putExtra("pinching", pinching)
            }
            context.startService(intent)
        } else {
            PointerAccessibilityService.updatePointer(x, y, pinching)
        }
    }

    fun start(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        context.startService(Intent(context, PointerOverlayService::class.java))
        return true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, PointerOverlayService::class.java))
        PointerAccessibilityService.hidePointer()
    }
}
