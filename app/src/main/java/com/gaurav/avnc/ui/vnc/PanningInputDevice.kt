package com.gaurav.avnc.ui.vnc

interface PanningInputDevice {
    fun setPanningListener(listener: PanningListener?)
    fun enable()
    fun disable()
    fun isEnabled(): Boolean
}

interface PanningListener {
    fun onPan(deltaYaw: Float, deltaPitch: Float)
}
