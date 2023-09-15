package life.memx.chat.services


import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Queue


class AudioRecording internal constructor(
    var queue: Queue<ByteArray>, private val activity: AppCompatActivity
) {
    private val TAG: String = AudioRecording::class.java.simpleName

    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION
    private val sampleRateInHz: Int = 16000
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeInBytes: Int = 32000
    private var isRecording: Boolean = false
    private var needRecording: Boolean = true
    private var audioRecorder: AudioRecord? = null


    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (!needRecording) {
            Log.d(TAG, "needRecording is false")
            return
        }
        Log.d(TAG, "startRecording")
        audioRecorder =
            AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)
        try {
            audioRecorder!!.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "startRecording error: $e")
            Toast.makeText(
                activity, "startRecording error: $e", Toast.LENGTH_LONG
            ).show();
            return
        }
        isRecording = true
        Thread(Runnable {
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: $e")
                Toast.makeText(
                    activity, "Recording error: $e", Toast.LENGTH_LONG
                ).show();
            }
        }).start()
    }

    fun stopRecording() {
        Log.d(TAG, "stopRecording")
        try {
            isRecording = false
            audioRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error: $e")
            Toast.makeText(
                activity, "stopRecording error: $e", Toast.LENGTH_LONG
            ).show();
        }
    }

    fun setNeedRecording(needRecording: Boolean) {
        this.needRecording = needRecording
    }
}