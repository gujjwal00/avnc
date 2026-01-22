package com.gaurav.avnc.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

object Tones {
    private val notifications = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)

    /**
     * @param tone a `TONE_` constant from [ToneGenerator].
     */
    fun notify(tone: Int) {
        Log.i("Tones", "Notify with tone $tone")
        notifications.startTone(tone)
    }
}
