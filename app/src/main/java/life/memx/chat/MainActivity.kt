package life.memx.chat


import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import life.memx.chat.services.AudioRecording
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.LinkedList
import java.util.Queue
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity() {

    private val TAG: String = MainActivity::class.java.simpleName

    private var uid: String = ""

    private val PERMISSIONS_REQUIRED: Array<String> = arrayOf<String>(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 10
    }

    private var audioQueue: Queue<ByteArray> = LinkedList<ByteArray>()
//    private var voiceQueue: Queue<String> = LinkedList<String>()
//    private var imageQueue: Queue<String> = LinkedList<String>()
//    private var gazeQueue: Queue<String> = LinkedList<String>()

    private var audioRecorder = AudioRecording(audioQueue)


    private fun verifyPermissions(activity: Activity) = PERMISSIONS_REQUIRED.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        uid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.i(TAG, uid)
        if (verifyPermissions(this)) {
            run()
        } else {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onStop() {
        super.onStop()
        audioRecorder.stopRecording()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                run()
            } else {
                Log.i(TAG, "Permission Denied");
            }
        }
    }

    private fun run() {
        audioRecorder.startRecording()
        Timer().schedule(object : TimerTask() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun run() {
                val data = JSONObject()
                data.put("uid", uid)
                data.put("gazes", JSONArray())
                var mAudioFile = getAudio()
                if (mAudioFile != null) {
                    uploadServer("http://10.176.34.117:9527/heartbeat", data, mAudioFile, null)
                }
            }
        }, 0, 1000)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getAudio(): File? {
        try {
            var data = audioQueue.poll()
            if (data != null) {
                var f= Files.createTempFile("audio", ".pcm")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return null
    }

    private fun getImage() {
//        do {
//            val image = imageQueue.poll()
//            if (image != null) {
//                Log.i(TAG, "getImage: " + image)
//            }
//        }while (imageQueue.isEmpty())
    }

    private fun getGaze() {
//        do {
//            val voice = voiceQueue.poll()
//            if (voice != null) {
//                Log.i(TAG, "getVoice: " + voice)
//            }
//        }while (voiceQueue.isEmpty())
    }

    private fun uploadServer(url: String, data: JSONObject, voiceFile: File?, sceneFile: File?) {
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)

        requestBody.addFormDataPart("data", data.toString())
        if (voiceFile != null) {
            val body = voiceFile.asRequestBody("image/*".toMediaTypeOrNull())
            requestBody.addFormDataPart("voice_file", voiceFile.name, body)
        }
        if (sceneFile != null) {
            val body = sceneFile.asRequestBody("audio/*".toMediaTypeOrNull())
            requestBody.addFormDataPart("scene_file", sceneFile.name, body)
        }

        val request = Request.Builder().url(url).post(requestBody.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                response.body!!.close()
            }
        })
    }
}