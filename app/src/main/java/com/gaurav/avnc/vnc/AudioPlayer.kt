/*
 * Copyright (c) 2026  Vortax contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Plays the signed 16-bit PCM that a QEMU VNC server streams (see QemuAudio.h).
 *
 * The VNC message loop must never wait for the sound card, otherwise the picture would freeze whenever
 * audio stalls. So [write] only queues the data, and a separate thread feeds the [AudioTrack].
 * If playback falls behind by more than [MAX_QUEUED_MS], new chunks are dropped: a gap is better than
 * sound that arrives seconds late.
 */
class AudioPlayer(val sampleRate: Int = SAMPLE_RATE, val channels: Int = CHANNELS) {

    companion object {
        const val SAMPLE_RATE = 22050   // QEMU resamples to whatever the client asks for
        const val CHANNELS = 1
        private const val MAX_QUEUED_MS = 600
        private const val TAG = "AudioPlayer"
    }

    private val bytesPerSecond = sampleRate * channels * 2
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val queuedBytes = AtomicInteger(0)

    @Volatile
    private var running = false
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        worker = thread(name = "vnc-audio", isDaemon = true) { play() }
    }

    /** Called from the VNC message thread. Never blocks. */
    fun write(pcm: ByteArray) {
        if (!running || pcm.isEmpty()) return
        if (queuedBytes.get() > bytesPerSecond * MAX_QUEUED_MS / 1000) return
        queuedBytes.addAndGet(pcm.size)
        queue.offer(pcm)
    }

    @Synchronized
    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        queue.clear()
        queuedBytes.set(0)
    }

    @Suppress("DEPRECATION") // The Builder needs API 23, minSdk is 21; this constructor works everywhere
    private fun play() {
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minSize = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minSize, bytesPerSecond / 4)      // about 250 ms: enough to ride out network jitter
        val track = try {
            AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot create AudioTrack: ${e.message}")
            running = false
            return
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "AudioTrack is not usable")
            track.release()
            running = false
            return
        }

        try {
            track.play()
            while (running) {
                val chunk = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                queuedBytes.addAndGet(-chunk.size)
                track.write(chunk, 0, chunk.size)               // blocks until the sound card took it, which is fine here
            }
        } catch (e: InterruptedException) {
            // stop() was called
        } catch (e: Exception) {
            Log.w(TAG, "Playback failed: ${e.message}")
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
        }
    }
}
