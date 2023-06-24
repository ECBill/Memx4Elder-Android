package life.memx.chat


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import life.memx.chat.services.AudioRecording
import life.memx.chat.services.CameraXService
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
    private var server_url: String = "http://10.176.34.117:9527"

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
    private var imageQueue: Queue<ByteArray> = LinkedList<ByteArray>()

    private var voiceQueue: Queue<String> = LinkedList<String>()
    //    private var voiceQueue: Queue<StringBuilder> = LinkedList<StringBuilder>()
    //    private var voiceQueue: Queue<File> = LinkedList<File>()

    @Volatile
    private var audio: StringBuilder = StringBuilder()

    private var audioRecorder = AudioRecording(audioQueue)

    //    private var imageCapturer = ImageCapturing(imageQueue, this)
    private var imageCapturer = CameraXService(imageQueue, this)

    private var cameraSwitch: Switch? = null
    private var audioSwitch: Switch? = null
    private var userText: EditText? = null
    private var serverSpinner: Spinner? = null

    private var responseText: TextView? = null
    private var userTextBtn: Button? = null
    private var responseQueue: Queue<String> = LinkedList<String>()

    private fun verifyPermissions(activity: Activity) = PERMISSIONS_REQUIRED.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        deleteCache()

        uid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
        uid = sharedPreferences.getString("uid", uid).toString()    // TODO


        Log.i(TAG, "uid: $uid")

        if (verifyPermissions(this)) {
            run()
        } else {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun registerCameraSwitch() {
        cameraSwitch = findViewById(R.id.camera_switch)
        cameraSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                imageCapturer.setNeedCapturing(true)
                Log.d(TAG, "setNeedCapturing: true")
            } else {
                imageCapturer.setNeedCapturing(false)
                Log.d(TAG, "setNeedCapturing: false")
            }
        }
    }


    private fun registerAudioSwitch() {
        audioSwitch = findViewById(R.id.audio_switch)
        audioSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                audioRecorder.setNeedRecording(true)
                audioRecorder.startRecording()
                Log.d(TAG, "setNeedRecording: true")
            } else {
                audioRecorder.setNeedRecording(false)
                audioRecorder.stopRecording()
                Log.d(TAG, "setNeedRecording: false")
            }
        }
    }

    private fun registerUsetText() {
        userText = findViewById(R.id.user_text)
        userText?.setText(uid)
        userTextBtn = findViewById(R.id.upload_user_text)
        userTextBtn?.setOnClickListener {
            val text = userText?.text.toString()
            if (text.isNotEmpty()) {
                Log.d(TAG, "user text: $text")
                uid = text
                val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("uid", uid)
                editor.commit()
                Toast.makeText(applicationContext, "设置用户: $text", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "user text is empty")
            }
        }
    }

    private fun registerServerSpinner() {
        serverSpinner = findViewById(R.id.server_spinner)
        serverSpinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val server_ips = resources.getStringArray(R.array.server_ips)
                server_url = server_ips[pos]
                Toast.makeText(applicationContext, "server: $server_url", Toast.LENGTH_SHORT)
                    .show();
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Another interface callback
            }
        })
    }

    private fun setResponseText(text: String) {
        responseText = findViewById(R.id.response_text)
        responseQueue.add(text)

        if (responseQueue.size >= 15) {
            responseQueue.remove()
        }

        var display_text = ""
        for (item in responseQueue) {
            display_text += item + "\n"
        }
        runOnUiThread { responseText?.setText(display_text) }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                run()
            } else {
                Log.e(TAG, "Permission Denied");
            }
        }
    }

    override fun onStop() {
        super.onStop()
        audioRecorder.stopRecording()
        imageCapturer.stopCapturing()
    }


    private fun run() {
        registerUsetText()
        registerCameraSwitch()
        registerAudioSwitch()
        registerServerSpinner()
        imageCapturer.startCapturing()

        imageCapturer.setImageSize(640,480) // TODO
        audioRecorder.startRecording()
        pullResponseTask()
        Timer().schedule(object : TimerTask() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun run() {
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
                data.put("timestamp", System.currentTimeMillis())
                var mAudioFile = getAudio()
                var mImageFile = getImage()
                uploadServer(
                    "$server_url/heartbeat",
                    data,
                    mAudioFile,
                    mImageFile
                )
            }
        }, 0, 1000)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getAudio(): File? {
        try {
            var data = audioQueue.poll()
            if (data != null) {
                var f = Files.createTempFile("audio", ".pcm")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getImage(): File? {
        try {
            var data = imageQueue.poll()
            if (data != null) {
                var f = Files.createTempFile("image", ".jpeg")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return null
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

    private fun pullResponseTask() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                pullResponse()
            }
        }, 0, 500)
        GlobalScope.launch {
            while (true) {
                if (voiceQueue.isEmpty()) {
                    continue
                }
                audioRecorder.stopRecording()
                val mediaPlayer = MediaPlayer()
                try {
                    mediaPlayer.setDataSource(voiceQueue.remove())
//                    val audio = voiceQueue.remove()
//                    mediaPlayer.setDataSource(audio)
                    mediaPlayer.prepare();
                    mediaPlayer.start()
                } catch (ex: Exception) {
                    print(ex.message)
                }
                while (mediaPlayer.isPlaying) {
                }
                audioRecorder.startRecording()
            }
        }
    }

    private fun pullResponse() {
        var url = "$server_url/response/$uid"
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
                    Log.i(TAG, "responseStr1: " + responseStr)
                    Log.i("onResponse", res.toString())
                    val text = res.getJSONObject("message").getString("text")
                    val voice = res.getJSONObject("message").getString("voice")
                    Log.i("onResponse voice: ", voice)
                    setResponseText(text)
                    voiceQueue.add(voice)
                }
                response.body!!.close()
            }
        })
    }

//    private fun pullResponse() {
//        var url = "$server_url/response/v2/$uid"
//        val client = OkHttpClient()
//        val request = Request.Builder().url(url).build()
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e(TAG, e.toString())
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                val status = response.headers["status"]
//
//                if (status == null) {
//                    Log.i(TAG, "status is null")
//                    response.headers.forEach {
//                        Log.i(TAG, it.toString())
//                    }
//                    response.body!!.close()
//                    return
//                }
//
//                if (status == "0") {
//                    response.body!!.close()
//                    return
//                }
//
//                val text = response.headers["response"]
//                if (text != null) {
//                    setResponseText(text)
//                }
//
//                val strBuffer = StringBuilder()
//                val input = response.body!!.byteStream()
//                val buffer = ByteArray(1024)
//                while (true) {
//                    val count = input.read(buffer)
//                    if (count == -1) {
//                        break
//                    }
//                    strBuffer.append(String(buffer, 0, count))
//                }
//                voiceQueue.add(strBuffer)
//                response.body!!.close()
//            }
//        })
//    }

//    private fun pullResponse() {
//        var url = "$server_url/response/v3/$uid"
//        val client = OkHttpClient()
//        val request = Request.Builder().url(url).build()
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e(TAG, e.toString())
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                val status = response.headers["status"]
//
//                if (status == null) {
//                    Log.i(TAG, "status is null")
//                    response.headers.forEach {
//                        Log.i(TAG, it.toString())
//                    }
//                    response.body!!.close()
//                    return
//                }
//
//                if (status == "0") {
//                    response.body!!.close()
//                    return
//                }
//
//                val text = response.headers["response"]
//                if (text != null) {
//                    setResponseText(text)
//                }
//
//                val file: File = File.createTempFile("audio", ".wav")
//                val input = response.body!!.byteStream()
//                val output = FileOutputStream(file)
//                val buffer = ByteArray(1024)
//                while (true) {
//                    val count = input.read(buffer)
//                    if (count == -1) {
//                        break
//                    }
//                    output.write(buffer, 0, count)
//                }
//                voiceQueue.add(file.absolutePath)
//
//                response.body!!.close()
//            }
//        })
//    }

    fun deleteCache() {
        try {
            val dir = this.cacheDir
            deleteDir(dir)
        } catch (e: java.lang.Exception) {
        }
    }

    fun deleteDir(dir: File?): Boolean {
        return if (dir != null && dir.isDirectory) {
            val children = dir.list()
            for (i in children.indices) {
                val success = deleteDir(File(dir, children[i]))
                if (!success) {
                    return false
                }
            }
            dir.delete()
        } else if (dir != null && dir.isFile) {
            dir.delete()
        } else {
            false
        }
    }
}

//class MainActivity : AppCompatActivity(), PictureCapturingListener {
//
//    private val TAG = MainActivity::class.java.getSimpleName()
//
//    private var uid = ""
//
//    private var pictureService: PictureCapturingService? = null
//
//    private var mRecorder: MediaRecorder? = null
//    private var mAudioFile: File? = null
//    private var mStartTime: Long = 0
//
//    private var voiceQueue: Queue<String> = LinkedList<String>()
//
//    private val PERMISSIONS_REQUIRED = arrayOf<String>(
//        Manifest.permission.READ_EXTERNAL_STORAGE,
//        Manifest.permission.WRITE_EXTERNAL_STORAGE,
//        Manifest.permission.CAMERA,
//        Manifest.permission.RECORD_AUDIO
//    )
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//
//        pictureService = PictureCapturingService(this)
//
//        uid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//
//        if (verifyPermissions(this)) {
//            countDown();
//            recordAudioTask();
//            pullResponseTask();
//        } else {
//            ActivityCompat.requestPermissions(
//                this, PERMISSIONS_REQUIRED, Companion.PERMISSIONS_REQUEST_CODE
//            )
//        }
//    }
//
//    private fun verifyPermissions(activity: Activity) = PERMISSIONS_REQUIRED.all {
//        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == Companion.PERMISSIONS_REQUEST_CODE) {
//            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Takes the user to the success fragment when permission is granted
//                countDown();
//                recordAudioTask();
//                pullResponseTask();
//            } else {
//                Log.i(TAG, "Permission request denied");
//                // Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//
//    private fun countDown() {
////        var that = this
////        object : CountDownTimer(18000000, 10000) {
////            override fun onFinish() {}
////            override fun onTick(millisUntilFinished: Long) {
////                val timestamp: Long = System.currentTimeMillis();
////                Log.i(TAG, timestamp.toString());
////                pictureService?.startCapturing(that)
//////                startRecording()
////            }
////        }.start()
//    }
//
//    override fun onCaptureDone(pictureUrl: String?, pictureData: ByteArray?) {
//        if (pictureData != null && pictureUrl != null) {
//            Log.i(TAG, "Picture saved to $pictureUrl")
//        }
//
////        stopRecording()
//        val mImageFile = File(
//            getExternalFilesDir(null).toString() + "/" + System.currentTimeMillis()
//                .toString() + ".jpg"
//        )
//        FileOutputStream(mImageFile).use { output -> output.write(pictureData) }
//
//        val gaze = JSONObject()
//        gaze.put("timestamp", System.currentTimeMillis())
//        gaze.put("confidence", 0)
//        gaze.put("norm_pos_x", 0.5)
//        gaze.put("norm_pos_y", 0.5)
//        gaze.put("diameter", 0)
//
//        val gazes = JSONArray()
//        gazes.put(gaze)
//
//        val data = JSONObject()
//        data.put("uid", uid)
//        data.put("gazes", gazes)
//
////        uploadServer("http://192.168.31.5:9527/heartbeat", data, mAudioFile, mImageFile)
//        uploadServer("http://10.176.34.117:9527/heartbeat", data, mAudioFile, mImageFile)
//    }
//
//    private fun startRecording() {
//        if (mRecorder != null) {
//            return
//        }
//        Log.i(TAG, "startRecording")
//        mRecorder = MediaRecorder()
//        mRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
//        mRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//            mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
//            mRecorder!!.setAudioEncodingBitRate(48000)
//        } else {
//            mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//            mRecorder!!.setAudioEncodingBitRate(64000)
//        }
//        mRecorder!!.setAudioSamplingRate(16000)
//
//        mAudioFile = File(
//            getExternalFilesDir(null).toString() + "/" + System.currentTimeMillis()
//                .toString() + ".m4a"
//        )
//        mRecorder!!.setOutputFile(mAudioFile!!.getAbsolutePath())
//
//        try {
//            mRecorder!!.prepare()
//            mRecorder!!.start()
//            Log.i(TAG, "started recording to " + mAudioFile!!.getAbsolutePath())
//        } catch (e: IOException) {
//            Log.i(TAG, "prepare() failed: " + e)
//        }
//    }
//
//    private fun stopRecording() {
//        mRecorder!!.stop()
//        mRecorder!!.release()
//        mRecorder = null
////        if (!saveFile && mAudioFile != null) {
////            mAudioFile!!.delete()
////        }
//        Log.i(TAG, "saved recording to " + mAudioFile!!.getAbsolutePath())
//    }
//
//    private fun uploadServer(url: String, data: JSONObject, voiceFile: File?, sceneFile: File?) {
//
//        val client = OkHttpClient()
//        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
//        requestBody.addFormDataPart("data", data.toString())
//        if (voiceFile != null) {
//            val body = RequestBody.create("image/*".toMediaTypeOrNull(), voiceFile)
//            requestBody.addFormDataPart("voice_file", voiceFile.name, body)
//        }
//        if (sceneFile != null) {
//            val body = RequestBody.create("audio/*".toMediaTypeOrNull(), sceneFile)
//            requestBody.addFormDataPart("scene_file", sceneFile.name, body)
//        }
//        val request = Request.Builder().url(url).post(requestBody.build()).build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e(TAG, e.toString())
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.body!!.close()
////                var responseStr = response.body!!.string()
////                val responseObj = JSONObject(responseStr)
////                val status = responseObj.getInt("status")
////                val res = responseObj.getJSONObject("response")
////
////                Log.i("onResponse", res.toString())
////
////                if (status == 1) {
////                    val text = res.getJSONObject("message").getString("text")
////                    val voice = res.getJSONObject("message").getString("voice")
////
////                    try {
////                        val mediaPlayer = MediaPlayer()
////                        mediaPlayer.setDataSource(voice)
////                        mediaPlayer.prepare();
////                        mediaPlayer.start()
////
////                    } catch (ex: Exception) {
////                        print(ex.message)
////                    }
////                }
//            }
//        })
//    }
//
//    private fun pullResponseTask() {
//        Timer().schedule(object : TimerTask() {
//            override fun run() {
//                pullResponse()
//            }
//        }, 0, 100)
//        GlobalScope.launch {
//            while (true) {
//                if (voiceQueue.isEmpty()) {
//                    continue
//                }
//                val mediaPlayer = MediaPlayer()
//                try {
//                    mediaPlayer.setDataSource(voiceQueue.remove())
//                    mediaPlayer.prepare();
//                    mediaPlayer.start()
//                } catch (ex: Exception) {
//                    print(ex.message)
//                }
//                while (mediaPlayer.isPlaying) {
//                }
//            }
//        }
//    }
//
//    private fun pullResponse() {
//        var url = "http://10.176.34.117:9527/response/$uid"
//        val client = OkHttpClient()
//        val request = Request.Builder().url(url).build()
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e(TAG, e.toString())
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                var responseStr = response.body!!.string()
//                val responseObj = JSONObject(responseStr)
//                val status = responseObj.getInt("status")
//                val res = responseObj.getJSONObject("response")
//
//                if (status == 1) {
//                    Log.i("onResponse", res.toString())
//                    val text = res.getJSONObject("message").getString("text")
//                    val voice = res.getJSONObject("message").getString("voice")
//                    voiceQueue.add(voice)
//                }
//                response.body!!.close()
//            }
//        })
//    }
//
//    private fun recordAudioTask() {
//        Timer().schedule(object : TimerTask() {
//            override fun run() {
//                if (mRecorder != null) {
//                    stopRecording()
//                    val data = JSONObject()
//                    data.put("uid", uid)
//                    data.put("gazes", JSONArray())
//                    uploadServer("http://10.176.34.117:9527/heartbeat", data, mAudioFile, null)
//                }
//                startRecording()
//            }
//        }, 0, 2000)
//    }
//
//    companion object {
//        private const val PERMISSIONS_REQUEST_CODE = 10
//    }
//}