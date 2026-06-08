package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceCommandRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): Boolean {
        try {
            val audioDir = File(context.cacheDir, "audio_records").apply {
                if (!exists()) mkdirs()
            }
            currentFile = File.createTempFile("voice_input_", ".m4a", audioDir)

            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }
            Log.d("VoiceCommandRecorder", "Recording started successfully. File: ${currentFile?.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e("VoiceCommandRecorder", "Failed to start recording", e)
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            currentFile = null
            return false
        }
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d("VoiceCommandRecorder", "Recording stopped successfully. File: ${currentFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("VoiceCommandRecorder", "Error stopping recorder", e)
        } finally {
            mediaRecorder = null
        }
        return currentFile
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceCommandRecorder", "Error canceling recorder", e)
        } finally {
            mediaRecorder = null
            currentFile?.delete()
            currentFile = null
        }
    }
}
