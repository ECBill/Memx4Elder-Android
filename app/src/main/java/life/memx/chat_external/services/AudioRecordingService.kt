package life.memx.chat_external.services


import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.os.Environment
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
//import com.konovalov.vad.silero.Vad
//import com.konovalov.vad.silero.VadListener
//import com.konovalov.vad.silero.config.FrameSize
//import com.konovalov.vad.silero.config.Mode
//import com.konovalov.vad.silero.config.SampleRate
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

class testRecorder internal constructor() {
    private val audioSource: Int = 6
    private val sampleRateInHz: Int = 16000
    private val channelConfig: Int = 16
    private val audioFormat: Int = 2
    private val bufferSizeInBytes: Int = 32000
    private var isRecording: Boolean = false
    private var audioRecorder: AudioRecord? = null
    private var count = 1
    private var PCMPath = Environment.getExternalStorageDirectory().path.toString()
    private var WAVPath = Environment.getExternalStorageDirectory().path.toString()

    @SuppressLint("MissingPermission")
    private fun initRecorder() {//初始化audioRecord对象
        audioRecorder =
            AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes)
    }

    fun startRecord():Int {
        if (isRecording) {
            return -1
        } else{
            audioRecorder?: initRecorder()
            Log.e("Test", "init!")
            audioRecorder?.startRecording()
            isRecording = true
            Log.e("Test", "start!")
            AudioRecordToFile().start()
            return 0
        }
    }

    fun stopRecord() {
        audioRecorder?.stop()
        audioRecorder?.release()
        isRecording = false
        audioRecorder = null
    }

    private fun writeDateTOFile() {
        var audioData = ByteArray(bufferSizeInBytes)
        Log.e("Test", PCMPath)
        PCMPath = "$PCMPath/RawAudio$count.pcm"
        WAVPath = "$WAVPath/FinalAudio$count.wav"
        val file = File(PCMPath)
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        val out = BufferedOutputStream(FileOutputStream(file))
        var length = 0
        while (isRecording && audioRecorder!=null) {
            length = audioRecorder!!.read(audioData, 0, bufferSizeInBytes)//获取音频数据
            Log.e("testRecord", length.toString())
            if (AudioRecord.ERROR_INVALID_OPERATION != length) {
                out.write(audioData, 0, length)//写入文件
                out.flush()
            }
        }
        out.close()
        count++
    }

    private fun copyWaveFile(pcmPath: String, wavPath: String) {

        var fileIn = FileInputStream(pcmPath)
        var fileOut = FileOutputStream(wavPath)
        val data = ByteArray(bufferSizeInBytes)
        val totalAudioLen = fileIn.channel.size()
        val totalDataLen = totalAudioLen + 36
        writeWaveFileHeader(fileOut, totalAudioLen, totalDataLen)
        var count = fileIn.read(data, 0, bufferSizeInBytes)
        while (count != -1) {
            fileOut.write(data, 0, count)
            fileOut.flush()
            count = fileIn.read(data, 0, bufferSizeInBytes)
        }
        fileIn.close()
        fileOut.close()
    }

    //添加WAV格式的文件头
    private fun writeWaveFileHeader(out:FileOutputStream , totalAudioLen:Long,
                                    totalDataLen:Long){

        val channels = 1
        val byteRate = 16 * sampleRateInHz * channels / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRateInHz and 0xff).toByte()
        header[25] = (sampleRateInHz shr 8 and 0xff).toByte()
        header[26] = (sampleRateInHz shr 16 and 0xff).toByte()
        header[27] = (sampleRateInHz shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private inner class AudioRecordToFile : Thread() {
        override fun run() {
            super.run()
            writeDateTOFile()
            copyWaveFile(PCMPath, WAVPath)
        }
    }
}

//class VadDetector (private val activity: Context) {
//    private lateinit var audioRecord: AudioRecord
//    private val sampleRate = 16000 // 采样率，可以根据需要进行调整
//    private val channelConfig = AudioFormat.CHANNEL_IN_MONO // 单声道
//    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT // 16位PCM编码
//    private val bufferSize = 1536
//    private val audioArray: ShortArray = ShortArray(bufferSize)
//    private var isListening: Boolean = false
//    private val vad = Vad.builder()
//                    .setContext(activity)
//                    .setSampleRate(SampleRate.SAMPLE_RATE_8K)
//                    .setFrameSize(FrameSize.FRAME_SIZE_1536)
//                    .setMode(Mode.NORMAL)
//                    .setSilenceDurationMs(300)
//                    .setSpeechDurationMs(50)
//                    .build()
//
//    @SuppressLint("MissingPermission")
//    fun startListening() {
//        Log.e("VAD", bufferSize.toString())
//        Log.d("VadDetector", "startListening")
//        audioRecord = AudioRecord(
//            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
//            sampleRate,
//            channelConfig,
//            audioFormat,
//            bufferSize
//        )
//        try {
//            audioRecord!!.startRecording()
//        } catch (e: Exception) {
//            Log.e("VadDetector", "startRecording error: $e")
//            Toast.makeText(
//                activity, "startRecording error: $e", Toast.LENGTH_LONG
//            ).show();
//            return
//        }
//        isListening = true
//        Thread(Runnable {
//            try {
//                while (true) {
//                    if (!isListening) {
//                        break
//                    }
//                    val buffer = ShortArray(bufferSize)
//                    val readSize = audioRecord!!.read(buffer, 0, bufferSize)
//                    if (readSize > 0) {
//                        System.arraycopy(buffer, 0, audioArray, 0, readSize)
//                    }
//                    val isSpeech = vad.isSpeech(audioArray)
//                    if (isSpeech) {
//                        Log.e("VAD", "speech detected!")
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("VadDetector", "Recording error: $e")
//                Toast.makeText(
//                    activity, "Recording error: $e", Toast.LENGTH_LONG
//                ).show();
//            }
//        }).start()
//    }
//
//    fun stop() {
//        Log.e("VAD", "closed listening!")
//        isListening = false
//        audioRecord.stop()
//        audioRecord.release()
//    }
//}