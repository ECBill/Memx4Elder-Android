package life.memx.chattest.services


import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.Queue


class AudioRecording internal constructor(var queue: Queue<ByteArray>) {
    private val TAG: String = AudioRecording::class.java.simpleName

    private val audioSource: Int = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz: Int = 16000
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeInBytes: Int = 32000
    private var isRecording: Boolean = false
    private var needRecording: Boolean = true
    private var audioRecorder: AudioRecord? = null


    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (!needRecording){
            Log.d(TAG, "needRecording is false")
            return
        }
        Log.d(TAG, "startRecording")
        audioRecorder =
            AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)
        try {
            audioRecorder!!.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            return
        }
        isRecording = true
        Thread(Runnable {
            while (true) {
                if (!isRecording) {
                    break
                }
                val buffer = ByteArray(bufferSizeInBytes)
                val read = audioRecorder!!.read(buffer, 0, bufferSizeInBytes)
                if (read > 0) {
                    queue.add(buffer)
                }
            }
        }).start()
    }

    fun stopRecording() {
        Log.d(TAG, "stopRecording")
        try {
            isRecording = false
            audioRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    fun setNeedRecording(needRecording: Boolean) {
        this.needRecording = needRecording
    }
}