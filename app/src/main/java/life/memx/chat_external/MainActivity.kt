package life.memx.chat_external


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.memx.chat_external.databinding.ActivityMainBinding
import life.memx.chat_external.services.AudioRecording
import life.memx.chat_external.services.ExCamFragment
import life.memx.chat_external.utils.NetUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.LinkedList
import java.util.Queue
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val TAG: String = MainActivity::class.java.simpleName
    private var netUtils: NetUtils? = null
    private var uploadTime = 0L
    private var requestTime = 0L
    private var tvServerSpeed: TextView? = null
    private var dlContainer: DrawerLayout? = null
    private var uid: String = ""

    //    private var server_url: String = "https://gate.luzy.top"
//    private var server_url: String = "http://10.176.34.117:9528"
    private var server_url: String = "http://150.158.82.234:7000"
//    private var server_url: String = "https://samantha.memx.life"

    private var is_first = true

    private val PERMISSIONS_REQUIRED: Array<String> = arrayOf<String>(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var pullResponseJob: Job   // this is used to handle pullResponse task

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 10
        private const val REQUEST_CAMERA = 0
        private const val REQUEST_STORAGE = 1
    }

    private var mWakeLock: PowerManager.WakeLock? = null

    private var audioQueue: Queue<ByteArray> = LinkedList<ByteArray>()
    private var imageQueue: Queue<ByteArray> = LinkedList<ByteArray>()
    private var eyeImageQueue: Queue<ByteArray> = LinkedList<ByteArray>()

    private var voiceQueue: Queue<String> = LinkedList<String>()
    //    private var voiceQueue: Queue<StringBuilder> = LinkedList<StringBuilder>()
    //    private var voiceQueue: Queue<File> = LinkedList<File>()

    @Volatile
    private var audio: StringBuilder = StringBuilder()

    private var audioRecorder = AudioRecording(audioQueue, this)

    //    private var imageCapturer = ImageCapturing(imageQueue, this)
    //    private var imageCapturer = CameraXService(imageQueue, this)
    private var imageCapturer = ExCamFragment(imageQueue)
//    private var imageCapturer = MultiCameraFragment(imageQueue, eyeImageQueue)


    private var cameraSwitch: Switch? = null
    private var audioSwitch: Switch? = null
    private var userText: EditText? = null
    private var serverSpinner: Spinner? = null

    private var responseText: TextView? = null
    private var stateText: TextView? = null     // To show whether it is "waiting for interruption"
    private var userTextBtn: Button? = null
    private var responseQueue: Queue<String> = LinkedList<String>()

    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false     // whether the SpeechRecognizer is listening to interruption
    private var isInterrupted = false   // whether the interruption is detected
    private var updateUrl = false       // whether need to update the url for long http connection

    private val mRecognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            TimeUnit.SECONDS.toMillis(10)
        )
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private lateinit var audioManager: AudioManager
    private val headsetReceiver = HeadsetReceiver()

    inner class HeadsetReceiver : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.S)
        private fun getBTHeadsetDevice(audioManager: AudioManager): AudioDeviceInfo? {
            val devices = audioManager.availableCommunicationDevices
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    return device
                }
            }
            return null
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun getWireHeadsetDevice(audioManager: AudioManager): AudioDeviceInfo? {
            val devices = audioManager.availableCommunicationDevices
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    return device
                }
            }
            return null
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun getBuiltinSpeaker(audioManager: AudioManager): AudioDeviceInfo? {
            val devices = audioManager.availableCommunicationDevices
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    return device
                }
            }
            return null
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun autoSetAudioDevice() {
            Log.w(TAG, "audioManager${audioManager.availableCommunicationDevices}")
            val btHeadsetDevice = getBTHeadsetDevice(audioManager)
            val wireHeadsetDevice = getWireHeadsetDevice(audioManager)
            val builtinSpeaker = getBuiltinSpeaker(audioManager)
            val currentDevice = audioManager.communicationDevice
            if (btHeadsetDevice != null) {
                if (currentDevice == btHeadsetDevice) {
                    return
                }
                audioManager.setCommunicationDevice(btHeadsetDevice)
                Toast.makeText(
                    this@MainActivity, "Bluetooth Headset Connected", Toast.LENGTH_SHORT
                ).show()
            } else if (wireHeadsetDevice != null) {
                if (currentDevice == wireHeadsetDevice) {
                    return
                }
                audioManager.setCommunicationDevice(wireHeadsetDevice)
                Toast.makeText(
                    this@MainActivity, "Wired Headset Connected", Toast.LENGTH_SHORT
                ).show()
            } else if (builtinSpeaker != null) {
                if (currentDevice == builtinSpeaker) {
                    return
                }
                audioManager.setCommunicationDevice(builtinSpeaker)
                Toast.makeText(
                    this@MainActivity, "Use Builtin Speaker", Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                autoSetAudioDevice()
            }
        }
    }

    fun registerHeadsetListener() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val HEADPHONE_ACTIONS = arrayOf(
            Intent.ACTION_HEADSET_PLUG,  // 普通耳机插入
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        )
        val filter = IntentFilter()
        for (action in HEADPHONE_ACTIONS) {
            filter.addAction(action)
        }
        registerReceiver(headsetReceiver, filter)

    }

    private fun verifyPermissions(activity: Activity) = PERMISSIONS_REQUIRED.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("HardwareIds")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // init UI
        uid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
        uid = sharedPreferences.getString("uid", uid).toString()

        registerUserText()
        registerCameraSwitch()
        registerAudioSwitch()
        registerServerSpinner()
//        replaceDemoFragment(DemoMultiCameraFragment())
        replaceDemoFragment(imageCapturer)

        netUtils = NetUtils(this@MainActivity)
        dlContainer = findViewById(R.id.dlContainer)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        deleteCache()


        // Initialize Android native SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                Log.i("SpeechRecognition", "Speech ready!")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                when (error) {
                    1 -> Log.e("SpeechRecognition", "1: Network timeout!")
                    2 -> Log.e("SpeechRecognition", "2: Could not find Network!")
                    3 -> Log.e("SpeechRecognition", "3: Audio recording error!")
                    4 -> Log.e("SpeechRecognition", "4: Could not find Network!")
                    5 -> Log.e("SpeechRecognition", "5: Other client side errors!")
                    6 -> Log.e("SpeechRecognition", "6: Speech input timeout!")
                    7 -> Log.e("SpeechRecognition", "7: No detected & matched speech results!")
                    8 -> Log.e("SpeechRecognition", "8: RecognitionService busy!")
                    9 -> Log.e("SpeechRecognition", "9: Insufficient permissions!")
                }
            }

            override fun onResults(results: Bundle) {
                if (isListening) {
                    // at this time, speechRecognizer should still be listening
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d("SpeechRecognition", "Recognized text: ${matches?.get(0)}")

                    if (matches?.get(0) == null) {
                        // this trigger of onResults() is due to the limited API calling duration
                        speechRecognizer.startListening(mRecognitionIntent)
                    } else if (interruptKeyword(matches?.get(0)!!)) {
                        // the recognized word matches the interruption instruction, set flag
                        isInterrupted = true

                        // send a message to the server to set user status to INTERRUPT
                        var url = "$server_url/interrupt/$uid"
                        val client = OkHttpClient()
                        val request = Request.Builder().url(url).build()
                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                Log.e(TAG, "Update interruption state error")
                            }

                            override fun onResponse(call: Call, response: Response) {

                            }
                        })
                    } else {
                        // the recognized word doesn't match the interruption instruction
                        Log.i("SpeechRecognition", "Interruption instruction not match!")
                        speechRecognizer.startListening(mRecognitionIntent)
                    }
                } else {
                    // the speechRecognizer should be closed now
                }
            }

            override fun onPartialResults(partialResults: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })

        Log.i(TAG, "uid: $uid")

        if (!verifyPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE
            )
        } else {
            run()
        }

        ToastUtils.init(this)

        if (speechRecognizer == null) {
            Toast.makeText(
                applicationContext,
                "No speech recognition service in this device, interruption disabled",
                Toast.LENGTH_LONG
            ).show();
        }
        setStateText("Waiting for voice")

        registerHeadsetListener()
        netUtils?.setDelayTime(0)?.setRecyclerTime(300)?.start(findViewById(R.id.tvNetSpeed))//网络
    }

    private fun replaceDemoFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commitAllowingStateLoss()
    }

    private fun interruptKeyword(input: String): Boolean {
        val lowercaseInput = input.lowercase()
        val keywords = listOf("stop", "ok", "yes", "no", "yeah", "yep")

        for (keyword in keywords) {
            if (lowercaseInput.contains(keyword.lowercase())) {
                return true
            }
        }

        return false
    }

    private fun registerCameraSwitch() {
        cameraSwitch = findViewById(R.id.camera_switch)
        cameraSwitch?.setOnCheckedChangeListener { _, isChecked ->
            try {
                if (isChecked) {
                    imageCapturer.setNeedCapturing(true)
                    Log.d(TAG, "setNeedCapturing: true")
                    Toast.makeText(
                        applicationContext, "open camera", Toast.LENGTH_SHORT
                    ).show();
                } else {
                    imageCapturer.stopCapturing()
                    imageCapturer.setNeedCapturing(false)
                    Log.d(TAG, "setNeedCapturing: false")
                    Toast.makeText(
                        applicationContext, "close camera", Toast.LENGTH_SHORT
                    ).show();
                }
            } catch (e: Exception) {
                Log.e(TAG, "set camera error: $e")
                Toast.makeText(
                    applicationContext, "set camera error: $e", Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    private fun registerAudioSwitch() {
        audioSwitch = findViewById(R.id.audio_switch)
        audioSwitch?.setOnCheckedChangeListener { _, isChecked ->
            try {
                if (isChecked) {
                    audioRecorder.setNeedRecording(true)
                    audioRecorder.startRecording()
                    Log.d(TAG, "setNeedRecording: true")
                    setStateText("State: start recording audio.")
                    Toast.makeText(
                        applicationContext, "open audio", Toast.LENGTH_SHORT
                    ).show();
                } else {
                    audioRecorder.setNeedRecording(false)
                    audioRecorder.stopRecording()
                    Log.d(TAG, "setNeedRecording: false")
                    setStateText("State: stop recording audio.")
                    Toast.makeText(
                        applicationContext, "close audio", Toast.LENGTH_SHORT
                    ).show();
                }
            } catch (e: Exception) {
                Log.e(TAG, "set audio error: $e")
                Toast.makeText(
                    applicationContext, "set audio error: $e", Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private fun registerUserText() {

        tvServerSpeed = findViewById(R.id.tvServerSpeed)
        userText = findViewById(R.id.user_text)
        userText?.setText(uid)
        userTextBtn = findViewById(R.id.upload_user_text)
        userTextBtn?.setOnClickListener {
            try {
                val text = userText?.text.toString()
                if (text.isNotEmpty()) {
                    Log.d(TAG, "user text: $text")
                    uid = text
                    val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.putString("uid", uid)
                    editor.commit()
                    Toast.makeText(applicationContext, "设置用户: $text", Toast.LENGTH_SHORT)
                        .show();
                    // restart pull response loop
                    if (this::pullResponseJob.isInitialized) {
                        pullResponseJob.cancel()
                        pullResponseJob = GlobalScope.launch {
                            pullResponseLoop()
                        }
                        Log.i("Stream", "Update url: $updateUrl")
                    }
                } else {
                    Log.d(TAG, "user text is empty")
                    Toast.makeText(
                        applicationContext, "用户不能设置为空", Toast.LENGTH_SHORT
                    ).show();
                }
                // hide keyboard
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(userText?.windowToken, 0)
            } catch (e: Exception) {
                Log.e(TAG, "set user text error: $e")
                Toast.makeText(
                    applicationContext, "set user text error: $e", Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private fun registerServerSpinner() {
        serverSpinner = findViewById(R.id.server_spinner)
        serverSpinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                try {
                    val server_ips = resources.getStringArray(R.array.server_ips)
                    server_url = server_ips[pos]
                    Toast.makeText(applicationContext, "server: $server_url", Toast.LENGTH_SHORT)
                        .show();
                    // restart pull response loop
                    if (this@MainActivity::pullResponseJob.isInitialized) {
                        pullResponseJob.cancel()
                        pullResponseJob = GlobalScope.launch {
                            pullResponseLoop()
                        }
                    }
                    Log.i("Stream", "Update url: $updateUrl")
                } catch (e: Exception) {
                    Log.e(TAG, "set url error: $e")
                    Toast.makeText(
                        applicationContext, "set url error: $e", Toast.LENGTH_LONG
                    ).show();
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Another interface callback
            }
        })
    }

    private fun setStateText(text: String) {
        stateText = findViewById(R.id.state_text)
        runOnUiThread { stateText?.setText(text) }
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
            Log.w(TAG, "onRequestPermissionsResult ${grantResults.size}")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                run()
            } else {
                Log.e(TAG, "Permission Denied");
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mWakeLock = Utils.wakeLock(this)
    }

    override fun onStop() {
        super.onStop()
        mWakeLock?.apply {
            Utils.wakeUnLock(this)
        }
        audioRecorder.stopRecording()
        setStateText("State: stop recording audio.")
        imageCapturer.stopCapturing()
    }


    private fun run() {
        imageCapturer.startCapturing()
        // imageCapturer.setImageSize(640, 480) //TODO: set image size
        audioRecorder.startRecording()
        setStateText("State: Waiting for voice.")

        pullResponseTask()

        Timer().schedule(object : TimerTask() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun run() {
                pushData()
            }
        }, 0, 100)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pushData() {
        try {
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
            val mAudioFile = getAudio()
            val mImageFile = getImage()
            if (mAudioFile != null) {
                uploadServer(
                    "$server_url/heartbeat", data, mAudioFile, null
                )
            }
            if (mImageFile != null) {
                uploadServer(
                    "$server_url/heartbeat", data, null, mImageFile
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "pushData error: $e")
            Looper.prepare()
            Toast.makeText(
                applicationContext, "pushData error: $e", Toast.LENGTH_LONG
            ).show();
            Looper.loop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getAudio(): File? {
        try {
            val data = audioQueue.poll()
            if (data != null) {
                val f = Files.createTempFile("audio", ".pcm")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAudio error: $e")
            Looper.prepare()
            Toast.makeText(
                applicationContext, "getAudio error: $e", Toast.LENGTH_LONG
            ).show();
            Looper.loop()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getImage(): File? {
        try {
            val data = imageQueue.poll()
            if (data != null) {
                val f = Files.createTempFile("image", ".jpeg")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getImage error: $e")
            Looper.prepare()
            Toast.makeText(
                applicationContext, "getImage error: $e", Toast.LENGTH_LONG
            ).show();
            Looper.loop()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getEyeImage(): File? {
        try {
            var data = eyeImageQueue.poll()
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
            val body = voiceFile.asRequestBody("audio/*".toMediaTypeOrNull())
            requestBody.addFormDataPart("voice_file", voiceFile.name, body)
        }
        if (sceneFile != null) {
            val body = sceneFile.asRequestBody("image/*".toMediaTypeOrNull())
            requestBody.addFormDataPart("scene_file", sceneFile.name, body)
        }

        var currentMills = System.currentTimeMillis()

        val request = Request.Builder().url(url).post(requestBody.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                response.body!!.close()
                //网络
                uploadTime = System.currentTimeMillis() - currentMills
                //网络
                runOnUiThread {
                    tvServerSpeed?.text =
                        "请求服务端用时：${requestTime}毫秒\n上传耗时：${uploadTime}毫秒"
                }
            }
        })
    }

    //    private fun pushEyeImageTask() {
//        Timer().schedule(object : TimerTask() {
//            @RequiresApi(Build.VERSION_CODES.O)
//            override fun run() {
//                val data = JSONObject()
//                data.put("uid", uid)
//                data.put("timestamp", System.currentTimeMillis())
//                var mImageFile = getEyeImage()
//
//
//                val client = OkHttpClient()
//                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
//
//                requestBody.addFormDataPart("data", data.toString())
//
//                if (mImageFile != null) {
//                    val body = mImageFile.asRequestBody("image/*".toMediaTypeOrNull())
//                    requestBody.addFormDataPart("eye_file", mImageFile.name, body)
//                }
//
//                val request = Request.Builder().url(eye_server_url).post(requestBody.build()).build()
//                client.newCall(request).enqueue(object : Callback {
//                    override fun onFailure(call: Call, e: IOException) {
//                        Log.e(TAG, e.toString())
//                    }
//
//                    override fun onResponse(call: Call, response: Response) {
//                        response.body!!.close()
//                    }
//                })
//
//            }
//        }, 0, 1000)
//    }
    private fun pullResponseTask() {
        try {
            // Only to fetch the first 'hello' message when the app start up ([TURN ON])
            pullResponse()
        } catch (e: Exception) {
            Log.e(TAG, "pullResponse error: $e")
            Toast.makeText(
                applicationContext, "pullResponse error: $e", Toast.LENGTH_LONG
            ).show();
        }
        pullResponseJob = GlobalScope.launch {
            pullResponseLoop()
        }
//        Timer().schedule(object : TimerTask() {
//            override fun run() {
//                pullResponse()
//            }
//        }, 0, 500)
        GlobalScope.launch {
            while (true) {
                try {
                    var startTime = System.currentTimeMillis()
                    val timeThresh = TimeUnit.SECONDS.toMillis(0.5.toLong())
                    while (isListening) {
                        // if isListening, wait for 0.5 secs to judge the end of the response
                        if (!voiceQueue.isEmpty()) {
                            // if receive response audio within 0.5 secs, continue processing
                            break
                        }

                        if (System.currentTimeMillis() - startTime >= timeThresh) {
                            // if exceeds 0.5 secs, this is treated as the end of the response
                            // stop listening, restart recording
                            withContext(Dispatchers.Main) {
                                Log.i("SpeechRecognition", "end listening")
                                speechRecognizer.cancel()
                                isListening = false
                                Log.i("SpeechRecognition", "Speaking finished")
                                audioRecorder.startRecording()
                                setStateText("State: Waiting for voice.")
                            }
                            break
                        }
                    }

                    if (voiceQueue.isEmpty()) {
                        continue
                    }

                    if (!isListening) {
                        // Start listening for interruptions util the end of response
                        // This will only be executed at the beginning of the response
                        withContext(Dispatchers.Main) {
                            Log.i("SpeechRecognition", "Start listening for interruption")
                            isListening = true
                            speechRecognizer.startListening(mRecognitionIntent)
                        }

                        // stop recording when the response is played
                        Log.i("SpeechRecognition", "stopRecording")
                        audioRecorder.stopRecording()
                        setStateText("State: Speaking, waiting for interruption")
                    }

                    // start playing the response audio
                    val mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(voiceQueue.remove())
                    mediaPlayer.prepare();
                    mediaPlayer.start()

                    // listen to whether the interruption flag is set
                    while (mediaPlayer.isPlaying) {
                        if (isInterrupted) {
                            Log.i("SpeechRecognition", "Handling interruption")
                            // Stop playing the response audio
                            mediaPlayer.stop()
                            // Empty the voice queue（注：清不干净，因为当前语音队列并不包含回复中所有的语音包）
                            voiceQueue.clear()
                            // Reset the flag
                            isInterrupted = false
                            break
                        }
                    }
                    // Finished playing the audio, stop listening for interruption and
                    // restart recording user's voice for the next utterance
                    mediaPlayer.release()

//                    GlobalScope.launch {
//                        delay(500)
//                        withContext(Dispatchers.Main) {
//                            if (voiceQueue.isEmpty()) {
//                                Log.i("SpeechRecognition", "end listening")
//                                speechRecognizer.cancel()
//                                isListening = false
//                                Log.i("SpeechRecognition", "Speaking finished")
//                                setStateText("Waiting for voice")
//                                audioRecorder.startRecording()
//                            }
//                        }
//                    }
                } catch (e: Exception) {
                    Log.e(TAG, "playback error: $e")
                    Looper.prepare()
                    Toast.makeText(
                        applicationContext,
                        "playback error: $e",
                        Toast.LENGTH_LONG
                    ).show();
                    Looper.loop()
                } finally {
//                    withContext(Dispatchers.IO) {
//                        TimeUnit.SECONDS.sleep(1)
//                    }
                }
            }
        }
    }

    private suspend fun pullResponseLoop() {
        while (true) {
            try {
                pullStreamResponse()
            } catch (e: Exception) {
                Log.e(TAG, "pullStreamResponse thread: $e")
            } finally {
                withContext(Dispatchers.IO) {
                    TimeUnit.SECONDS.sleep(1)
                }
            }
        }
    }

    private fun pullResponse() {
        var startMills = System.currentTimeMillis()//网络
        var url = "$server_url/response/$uid?is_first=$is_first"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "pullResponse error: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                var responseStr = response.body!!.string()
                val responseObj = JSONObject(responseStr)
                val status = responseObj.getInt("status")
                val res = responseObj.getJSONObject("response")

                if (status == 1) {
                    Log.i(TAG, "responseStr: $responseStr")
                    Log.i("onResponse", res.toString())
                    val text = res.getJSONObject("""message""").getString("text")
                    val voice = res.getJSONObject("message").getString("voice")
                    Log.i("onResponse voice: ", voice)
                    setResponseText(text)
                    if (text == "[INTERRUPT]") {
                        voiceQueue.clear()
                    }
                    voiceQueue.add(voice)
                    is_first = false
                }

                //网络
                requestTime = System.currentTimeMillis() - startMills
                Log.e("HHH", "" + requestTime)
                //网络
                runOnUiThread {
                    tvServerSpeed?.text =
                        "请求服务端用时：${requestTime}毫秒\n上传耗时：${uploadTime}毫秒"
                }

                response.body!!.close()
            }
        })
    }

    private fun pullStreamResponse() {
        var startMills = System.currentTimeMillis()//网络
        val url = "$server_url/response/stream/$uid"
        Log.i(TAG, "pullStreamResponse start: $url")
        val client = OkHttpClient.Builder().readTimeout(604800, TimeUnit.SECONDS)
//            .connectTimeout(604800, TimeUnit.SECONDS).writeTimeout(604800, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
//            .retryOnConnectionFailure(true)
            .build()
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        val response = call.execute()

        runOnUiThread {
            tvServerSpeed?.text = "请求服务端用时：${requestTime}毫秒\n上传耗时：${uploadTime}毫秒"
        }
        val input = response.body!!.byteStream()
        val buffer = BufferedReader(InputStreamReader(input))
        try {
            while (true) {
                Log.i("Stream", "Update url in while loop: $updateUrl")
                if (updateUrl) {
                    Log.i("Stream update", "Update link")
                    call.cancel()
                    updateUrl = false
                    break
                }
                Log.i(TAG, "pullStreamResponse: $url")

                val strBuffer = buffer.readLine() ?: break
                Log.i(TAG, "pullStreamResponse: $strBuffer")
                val responseObj = JSONObject(strBuffer)
                val status = responseObj.getInt("status")
                val res = responseObj.getJSONObject("response")
                if (status == 1) {
                    Log.i("onResponse", res.toString())
                    val text = res.getJSONObject("message").getString("text")
                    val voice = res.getJSONObject("message").getString("voice")
                    Log.i("onResponse voice: ", voice)
                    setResponseText(text)
                    if (text == "[INTERRUPT]") {
                        voiceQueue.clear()
                    }
                    voiceQueue.add(voice)
                }
            }
        } catch (e: Exception) {
            response.body!!.close()
            Log.e(TAG, "pullStreamResponse error: $e")
        }
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
    fun checkNet(view: View) {
        dlContainer?.openDrawer(Gravity.LEFT)
    }


    fun deleteCache() {
        try {
            val dir = this.cacheDir
            deleteDir(dir)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "deleteCache error: $e")
            Toast.makeText(
                applicationContext, "deleteCache error: $e", Toast.LENGTH_LONG
            ).show();
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

    override fun onDestroy() {
        super.onDestroy()

        // 取消注册广播接收器
        unregisterReceiver(headsetReceiver)
    }
}
