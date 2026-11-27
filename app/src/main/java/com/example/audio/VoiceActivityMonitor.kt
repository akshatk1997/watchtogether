package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Monitors real device microphone amplitude using Android's AudioRecord API when the user
 * has granted RECORD_AUDIO permission and unmuted in room voice chat.
 */
class VoiceActivityMonitor {

    private var monitorJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onLevel: (Float) -> Unit,
        onSpeakingChanged: (Boolean) -> Unit
    ) {
        stop()
        monitorJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufSize <= 0) {
                onLevel(0f)
                return@launch
            }
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize * 2
                )
            } catch (_: Exception) {
                null
            } ?: return@launch

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@launch
            }

            val buffer = ShortArray(minBufSize / 2)
            var lastSpeaking = false
            var silenceFrames = 0

            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble() / Short.MAX_VALUE.toDouble()
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / read).toFloat()
                        val normalized = (rms * 6.5f).coerceIn(0f, 1f)
                        onLevel(normalized)

                        val isVoiceActive = normalized > 0.14f
                        if (isVoiceActive) {
                            silenceFrames = 0
                            if (!lastSpeaking) {
                                lastSpeaking = true
                                onSpeakingChanged(true)
                            }
                        } else {
                            silenceFrames++
                            if (lastSpeaking && silenceFrames >= 8) {
                                lastSpeaking = false
                                onSpeakingChanged(false)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Gracefully release on audio focus loss or cancellation
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                onLevel(0f)
                if (lastSpeaking) {
                    onSpeakingChanged(false)
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
