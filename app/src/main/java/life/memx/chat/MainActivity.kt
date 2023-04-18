package life.memx.chat


import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import life.memx.chat.services.PictureCapturingListener
import life.memx.chat.services.PictureCapturingService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.Queue
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity(), PictureCapturingListener {

    private val TAG = MainActivity::class.java.getSimpleName()

    private var uid = ""

    private var pictureService: PictureCapturingService? = null

    private var mRecorder: MediaRecorder? = null
    private var mAudioFile: File? = null
    private var mStartTime: Long = 0

    private var voiceQueue: Queue<String> = LinkedList<String>()

    private val PERMISSIONS_REQUIRED = arrayOf<String>(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        pictureService = PictureCapturingService(this)

        uid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        if (verifyPermissions(this)) {
            countDown();
            recordAudioTask();
            pullResponseTask();
        } else {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS_REQUIRED, Companion.PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun verifyPermissions(activity: Activity) = PERMISSIONS_REQUIRED.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Companion.PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Takes the user to the success fragment when permission is granted
                countDown();
                recordAudioTask();
                pullResponseTask();
            } else {
                Log.i(TAG, "Permission request denied");
                // Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun countDown() {
//        var that = this
//        object : CountDownTimer(18000000, 10000) {
//            override fun onFinish() {}
//            override fun onTick(millisUntilFinished: Long) {
//                val timestamp: Long = System.currentTimeMillis();
//                Log.i(TAG, timestamp.toString());
//                pictureService?.startCapturing(that)
////                startRecording()
//            }
//        }.start()
    }

    override fun onCaptureDone(pictureUrl: String?, pictureData: ByteArray?) {
        if (pictureData != null && pictureUrl != null) {
            Log.i(TAG, "Picture saved to $pictureUrl")
        }

//        stopRecording()
        val mImageFile = File(
            getExternalFilesDir(null).toString() + "/" + System.currentTimeMillis()
                .toString() + ".jpg"
        )
        FileOutputStream(mImageFile).use { output -> output.write(pictureData) }

        val gaze = JSONObject()
        gaze.put("timestamp", System.currentTimeMillis())
        gaze.put("confidence", 0)
        gaze.put("norm_pos_x", 0.5)
        gaze.put("norm_pos_y", 0.5)
        gaze.put("diameter", 0)

        val gazes = JSONArray()
        gazes.put(gaze)

        val data = JSONObject()
        data.put("uid", uid)
        data.put("gazes", gazes)

//        uploadServer("http://192.168.31.5:9527/heartbeat", data, mAudioFile, mImageFile)
        uploadServer("http://10.176.34.117:9527/heartbeat", data, mAudioFile, mImageFile)
    }

    private fun startRecording() {
        if (mRecorder != null) {
            return
        }
        Log.i(TAG, "startRecording")
        mRecorder = MediaRecorder()
        mRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
            mRecorder!!.setAudioEncodingBitRate(48000)
        } else {
            mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mRecorder!!.setAudioEncodingBitRate(64000)
        }
        mRecorder!!.setAudioSamplingRate(16000)

        mAudioFile = File(
            getExternalFilesDir(null).toString() + "/" + System.currentTimeMillis()
                .toString() + ".m4a"
        )
        mRecorder!!.setOutputFile(mAudioFile!!.getAbsolutePath())

        try {
            mRecorder!!.prepare()
            mRecorder!!.start()
            Log.i(TAG, "started recording to " + mAudioFile!!.getAbsolutePath())
        } catch (e: IOException) {
            Log.i(TAG, "prepare() failed: " + e)
        }
    }

    private fun stopRecording() {
        mRecorder!!.stop()
        mRecorder!!.release()
        mRecorder = null
//        if (!saveFile && mAudioFile != null) {
//            mAudioFile!!.delete()
//        }
        Log.i(TAG, "saved recording to " + mAudioFile!!.getAbsolutePath())
    }

    private fun uploadServer(url: String, data: JSONObject, voiceFile: File?, sceneFile: File?) {

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
        requestBody.addFormDataPart("data", data.toString())
        if (voiceFile != null) {
            val body = RequestBody.create("image/*".toMediaTypeOrNull(), voiceFile)
            requestBody.addFormDataPart("voice_file", voiceFile.name, body)
        }
        if (sceneFile != null) {
            val body = RequestBody.create("audio/*".toMediaTypeOrNull(), sceneFile)
            requestBody.addFormDataPart("scene_file", sceneFile.name, body)
        }
        val request = Request.Builder().url(url).post(requestBody.build()).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                response.body!!.close()
//                var responseStr = response.body!!.string()
//                val responseObj = JSONObject(responseStr)
//                val status = responseObj.getInt("status")
//                val res = responseObj.getJSONObject("response")
//
//                Log.i("onResponse", res.toString())
//
//                if (status == 1) {
//                    val text = res.getJSONObject("message").getString("text")
//                    val voice = res.getJSONObject("message").getString("voice")
//
//                    try {
//                        val mediaPlayer = MediaPlayer()
//                        mediaPlayer.setDataSource(voice)
//                        mediaPlayer.prepare();
//                        mediaPlayer.start()
//
//                    } catch (ex: Exception) {
//                        print(ex.message)
//                    }
//                }
            }
        })
    }

    private fun pullResponseTask() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                pullResponse()
            }
        }, 0, 100)
        GlobalScope.launch {
            while (true) {
                if (voiceQueue.isEmpty()) {
                    continue
                }
                val mediaPlayer = MediaPlayer()
                try {
                    mediaPlayer.setDataSource(voiceQueue.remove())
                    mediaPlayer.prepare();
                    mediaPlayer.start()
                } catch (ex: Exception) {
                    print(ex.message)
                }
                while (mediaPlayer.isPlaying) {
                }
            }
        }
    }

    private fun pullResponse() {
        var url = "http://10.176.34.117:9527/response/$uid"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                var responseStr = response.body!!.string()
                val responseObj = JSONObject(responseStr)
                val status = responseObj.getInt("status")
                val res = responseObj.getJSONObject("response")

                if (status == 1) {
                    Log.i("onResponse", res.toString())
                    val text = res.getJSONObject("message").getString("text")
                    val voice = res.getJSONObject("message").getString("voice")
                    voiceQueue.add(voice)
                }
                response.body!!.close()
            }
        })
    }

    private fun recordAudioTask() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                if (mRecorder != null) {
                    stopRecording()
                    val data = JSONObject()
                    data.put("uid", uid)
                    data.put("gazes", JSONArray())
                    uploadServer("http://10.176.34.117:9527/heartbeat", data, mAudioFile, null)
                }
                startRecording()
            }
        }, 0, 2000)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 10
    }
}