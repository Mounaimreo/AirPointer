package ai.mounaim.airpointer

object PointerBridge {
    fun setPosition(x: Int, y: Int, pinching: Boolean) =
        PointerAccessibilityService.updatePointer(x, y, pinching)

    fun stop() = PointerAccessibilityService.hidePointer()
}
