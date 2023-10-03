package life.memx.chat_external.services


import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadListener
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
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

class VadDetector (private val activity: Context) {
    private lateinit var audioRecord: AudioRecord
    private val sampleRate = 16000 // 采样率，可以根据需要进行调整
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO // 单声道
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT // 16位PCM编码
    private val bufferSize = 1536
    private val audioArray: ShortArray = ShortArray(bufferSize)
    private var isListening: Boolean = false
    private val vad = Vad.builder()
                    .setContext(activity)
                    .setSampleRate(SampleRate.SAMPLE_RATE_8K)
                    .setFrameSize(FrameSize.FRAME_SIZE_1536)
                    .setMode(Mode.NORMAL)
                    .setSilenceDurationMs(300)
                    .setSpeechDurationMs(50)
                    .build()

    @SuppressLint("MissingPermission")
    fun startListening() {
        Log.e("VAD", bufferSize.toString())
        Log.d("VadDetector", "startListening")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        try {
            audioRecord!!.startRecording()
        } catch (e: Exception) {
            Log.e("VadDetector", "startRecording error: $e")
            Toast.makeText(
                activity, "startRecording error: $e", Toast.LENGTH_LONG
            ).show();
            return
        }
        isListening = true
        Thread(Runnable {
            try {
                while (true) {
                    if (!isListening) {
                        break
                    }
                    val buffer = ShortArray(bufferSize)
                    val readSize = audioRecord!!.read(buffer, 0, bufferSize)
                    if (readSize > 0) {
                        System.arraycopy(buffer, 0, audioArray, 0, readSize)
                    }
                    val isSpeech = vad.isSpeech(audioArray)
                    if (isSpeech) {
                        Log.e("VAD", "speech detected!")
                    }
                }
            } catch (e: Exception) {
                Log.e("VadDetector", "Recording error: $e")
                Toast.makeText(
                    activity, "Recording error: $e", Toast.LENGTH_LONG
                ).show();
            }
        }).start()
    }

    fun stop() {
        Log.e("VAD", "closed listening!")
        isListening = false
        audioRecord.stop()
        audioRecord.release()
    }
}