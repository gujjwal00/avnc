// In a suitable file, e.g., com/gaurav/avnc/ui/vnc/gl/XrViewState.kt
package com.gaurav.avnc.ui.vnc.gl

data class CameraStateData(
    val position: FloatArray,
    val lookAt: FloatArray,
    val up: FloatArray,
    val zoomLevel: Float
) {
    // FloatArray.equals compares references, so override for value comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CameraStateData
        if (!position.contentEquals(other.position)) return false
        if (!lookAt.contentEquals(other.lookAt)) return false
        if (!up.contentEquals(other.up)) return false
        if (zoomLevel != other.zoomLevel) return false
        return true
    }
    override fun hashCode(): Int {
        var result = position.contentHashCode()
        result = 31 * result + lookAt.contentHashCode()
        result = 31 * result + up.contentHashCode()
        result = 31 * result + zoomLevel.hashCode()
        return result
    }
}

data class PanningStrategyStateData(
    val panningMode: String, // e.g., "offset_surface" or "rotation"
    val offsetStrategyAngleY: Float?,
    val offsetStrategyHeightY: Float?
)
