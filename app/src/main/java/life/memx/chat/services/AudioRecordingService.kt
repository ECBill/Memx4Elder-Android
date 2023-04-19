package life.memx.chat.services


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


    @SuppressLint("MissingPermission")
    private var audioRecorder: AudioRecord =
        AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)


    fun startRecording() {
        try {
            audioRecorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            return
        }
        Thread(Runnable {
            while (true) {
                val buffer = ByteArray(bufferSizeInBytes)
                val read = audioRecorder.read(buffer, 0, bufferSizeInBytes)
                if (read > 0) {
                    queue.add(buffer)
                }
            }
        }).start()
    }

    fun stopRecording() {
        try {
            audioRecorder.release()
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }
}