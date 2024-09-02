package life.memx.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.alibaba.fastjson.JSONException
import com.alibaba.idst.nui.AsrResult
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.INativeFileTransCallback
import com.jiangdg.ausbc.utils.ToastUtils
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.memx.chat.R
import life.memx.chat.databinding.ActivityHomeBinding
import life.memx.chat.services.AliAsrRecorder
import life.memx.chat.services.AudioRecording
import life.memx.chat.services.CameraXService
import life.memx.chat.utils.NetUtils
import life.memx.chat.utils.TimerUtil
import life.memx.chat.view.PerformanceMonitorViewModel
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
import java.nio.file.Paths
import java.util.LinkedList
import java.util.Queue
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit



class HomeActivity : AppCompatActivity() {
    private val TAG: String = HomeActivity::class.java.simpleName
    private var netUtils: NetUtils? = null
    private var dlContainer: DrawerLayout? = null
    private var uid: String = ""

    private var server_url: String = "http://106.15.239.131:7000"
    private var is_first = true

    private lateinit var viewBinding: ActivityHomeBinding
    private lateinit var performanceMonitorView: PerformanceMonitorViewModel
    @RequiresApi(Build.VERSION_CODES.R)
    private val PERMISSIONS_REQUIRED: Array<String> = arrayOf<String>(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private lateinit var pullResponseJob: Job // this is used to handle pullResponse task
    private var tempFilePath = Environment.getExternalStorageDirectory().path.toString() +
            "/Android/data/life.memx.chat/" // This is used to store temp files

    private var audioQueue: Queue<ByteArray> = LinkedList<ByteArray>()
    private var imageQueue: Queue<ByteArray> = LinkedList<ByteArray>()
    private var gpsQueue: Queue<FloatArray> = LinkedList<FloatArray>()
    private var eyeImageQueue: Queue<ByteArray> = LinkedList<ByteArray>()
    private var voiceQueue: Queue<String> = LinkedList<String>()

//    private var userInactivityTimer: Timer? = null
//    private val inactivityTimeout = 600000L

    @Volatile
    private var audio: StringBuilder = StringBuilder()

    companion object {
        // Permission codes
        private const val PERMISSIONS_REQUEST_CODE = 10
        private const val REQUEST_CAMERA = 0
        private const val REQUEST_STORAGE = 1
        private const val TIMER_SERVER_PROCESSING = 1

        // The message types shown in the state TextView
        private const val LOGMSG = 0
        private const val INFOMSG = 1
        private const val ERRMSG = 2
    }

    private var audioRecorder = AudioRecording(audioQueue, this)
    private var mediaPlayer = MediaPlayer()
    private var aliRecorder = AliAsrRecorder(this, server_url, object : INativeFileTransCallback {

        override fun onFileTransEventCallback(
            event: Constants.NuiEvent,
            resultCode: Int,
            finish: Int,
            asrResult: AsrResult,
            taskId: String
        ) {
            Log.e("ali sdk", "开始执行")
            if (event == Constants.NuiEvent.EVENT_FILE_TRANS_UPLOADED) {
                Log.e("ali sdk", "完成上传，正在转写...")
            } else if (event == Constants.NuiEvent.EVENT_FILE_TRANS_RESULT) {
                Log.e("ali sdk", "asrResult:"+asrResult.asrResult)
                val results = JSONObject(asrResult.asrResult)
                    .getJSONObject("flash_result")
                    .get("sentences") as JSONArray
                setStateText("Ali ASR: " + results.getJSONObject(0).get("text") as String, true, LOGMSG)
                for (i in 0 until results.length()) {
                    if (isInterruptKeyword(results.getJSONObject(i).get("text") as String)) {
                        handleInterrupt("ok")
                        break
                    }
                }
            } else if (event == Constants.NuiEvent.EVENT_ASR_ERROR) {
                if (resultCode == 240067) {
                    setStateText("Ali disconnected!", true, ERRMSG)
                } else if (resultCode == 240075) {
                    val errMsg = JSONObject(asrResult.asrResult).get("message")
                    setStateText(errMsg.toString(), true, INFOMSG)
                }
                Log.e("ali sdk", "error $resultCode")
            }
        }
    })

    private var imageCapturer = CameraXService(imageQueue, this)
    private var cameraSwitch: Switch? = null
    private var audioSwitch: Switch? = null
    private var userText: EditText? = null
    private var serverSpinner: Spinner? = null

    private var GPSView: TextView? = null

    private var responseText: TextView? = null
    private var responseScroll: ScrollView? = null
    private var userTextBtn: Button? = null
    private var responseQueue: Queue<String> = LinkedList<String>()
    private var stateQueue: Queue<String> = LinkedList<String>()

    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false // whether the SpeechRecognizer is listening to interruption
    private var isInterrupted = false // whether the interruption is detected
    private var updateUrl = false // whether need to update the url for long http connection
    private var currentResId: String = "no response" // the current response id
    private var banResId: String = "no ban" // the banned response id, which will not be played

    private lateinit var audioManager: AudioManager
    private val timerUtil = TimerUtil()


    private val mRecognizerListener: edu.cmu.pocketsphinx.RecognitionListener = object :
        edu.cmu.pocketsphinx.RecognitionListener {
        override fun onBeginningOfSpeech() {
            aliRecorder.startRecord()
            setStateText("State: Speech Begin", true)
        }

        override fun onEndOfSpeech() {
            setStateText("State: Speech End", true)
        }

        override fun onPartialResult(hypothesis: Hypothesis?) {
            if (hypothesis == null) {
                return
            }
            val text = hypothesis.hypstr
            Log.d(
                TAG, String.format(
                    "onPartialResult: hypothesis string: %s, prob=[%d], bestScore=[%d]",
                    text, hypothesis.prob, hypothesis.bestScore
                )
            )
            if (text.contains("ok") && isListening) {
                speechRecognizer.cancel()
                speechRecognizer.startListening("HOT_WORD_SEARCH")
            } else {
                Log.e(TAG, "onPartialResult: unexpected hypothesis string: $text")
                if (text.contains("ok") && isListening) {
                }
            }
        }

        override fun onResult(hypothesis: Hypothesis?) {
            if (hypothesis == null) {
                Log.e(TAG, "on Result: null")
                return
            }
            Log.d(TAG, "on Result: " + hypothesis.hypstr + " : " + hypothesis.bestScore)
        }

        override fun onError(e: Exception) {
            Log.e(TAG, "onError()", e)
        }

        override fun onTimeout() {
            Log.d(TAG, "onTimeout()")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun verifyPermissions(activity: Activity) = PERMISSIONS_REQUIRED.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }


    private var triggerCount = 0
    private val maxTriggers = 2

//    private fun resetInactivityTimer() {
//        userInactivityTimer?.cancel()
//        userInactivityTimer = Timer()
//        userInactivityTimer?.schedule(object : TimerTask() {
//            @RequiresApi(Build.VERSION_CODES.O)
//            override fun run() {
//                runOnUiThread {
//                    // 只有在计数器小于最大触发次数时才触发
//                    if (triggerCount < maxTriggers) {
//                        initiateAutoChat()
//                        triggerCount++
//                    } else {
//                        Log.d(TAG, "Max triggers reached, not triggering initiateAutoChat")
//                    }
//                }
//            }
//        }, inactivityTimeout)
//    }
//
//    @RequiresApi(Build.VERSION_CODES.O)
//    private fun initiateAutoChat() {
//        pushData(1)
//        Log.d(TAG, "initiateAutoChat triggered")
//        Log.d(TAG, "triggerCount:"+triggerCount.toString())
//        resetInactivityTimer()
//    }

//    // 其他重置计数器和计时器的函数
//    private fun resetTriggerCount() {
//        triggerCount = 0
//        resetInactivityTimer()
//    }


    private fun handleInterrupt(text: String) {
        setStateText("Interrupt detected: $text", true, INFOMSG)
        isListening = false
        var url = "$server_url/interrupt/$uid"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Update interruption state error")
            }

            override fun onResponse(call: Call, response: Response) {}
        })
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            }
        } catch (e: Exception) {
            Log.e("Handle interruption", "Exception: $e")
        }

        voiceQueue.clear()
        if (currentResId != "no response") {
            banResId = currentResId
        }
        isListening = true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        viewBinding.lifecycleOwner = this

        uid = intent.getStringExtra("uid") ?: ""

        initUI()
        checkPermissions()
        ToastUtils.init(this)

        netUtils?.setDelayTime(0)?.setRecyclerTime(300)?.start(findViewById(R.id.tvNetSpeed))
        GPSView = findViewById(R.id.GPS_display)

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ), 100
                )
            }
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            5000,
            3f,
            locationListener
        )
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000,
            3f,
            locationListener
        )
        Log.d(TAG, "启动时triggerCount")
//        resetTriggerCount()
//        resetInactivityTimer()
        run()
    }

    private fun initUI() {
        registerUserText()
        registerCameraSwitch()
        registerAudioSwitch()
        registerServerSpinner()
        registerPerformanceMonitor()
        deleteCache()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkPermissions() {
        if (!verifyPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE
            )
        } else {
            run()
        }
    }



    private fun startAudioAndVideo() {
        try {

            if (!audioRecorder.isRecording()) {
                Log.d(TAG, "Attempting to start audio recording")
                audioRecorder.setNeedRecording(true)
                audioRecorder.startRecording()
                Log.d(TAG, "Audio recording started successfully")
            } else {
                Log.d(TAG, "Audio recorder is already running")
            }
            imageCapturer.setNeedCapturing(true)


            // 检查并启动媒体播放器
            if (!mediaPlayer.isPlaying) {
                Log.d(TAG, "Attempting to start media player")
                mediaPlayer.reset()

                // 确保 voiceQueue 不为空
                if (voiceQueue.isNotEmpty()) {
                    val audioFilePath = voiceQueue.remove() // 从 voiceQueue 获取音频文件路径
                    Log.d(TAG, "Audio file path: $audioFilePath")
                    mediaPlayer.setDataSource(audioFilePath)
                    Log.d(TAG, "Media player data source set successfully")
                    mediaPlayer.prepare()
                    Log.d(TAG, "Media player prepared successfully")
                    mediaPlayer.start()
                    Log.d(TAG, "Media player started successfully")
                } else {
                    Log.e(TAG, "Voice queue is empty")
                }
            } else {
                Log.d(TAG, "Media player is already playing")
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException while starting media player: ${e.message}", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException while starting media player: ${e.message}", e)
        } catch (e: NoSuchElementException) {
            Log.e(TAG, "NoSuchElementException while starting media player: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio or video: ${e.message}", e)
        }
    }


    private fun stopAudioAndVideo() {
        try {
            audioRecorder.setNeedRecording(false)
            audioRecorder.stopRecording()
            imageCapturer.setNeedCapturing(false)
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()

            clearVoiceQueue()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio or video: $e")
        }
    }

    private fun clearVoiceQueue() {
        Log.d(TAG, "Clearing voice queue")
        voiceQueue.clear()
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
                    ).show()
                } else {
                    imageCapturer.setNeedCapturing(false)
                    Log.d(TAG, "setNeedCapturing: false")
                    Toast.makeText(
                        applicationContext, "close camera", Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "set camera error: $e")
                Toast.makeText(
                    applicationContext, "set camera error: $e", Toast.LENGTH_LONG
                ).show()
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
                    setStateText("State: Waiting for voice.", true)
                    Toast.makeText(
                        applicationContext, "open audio", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    audioRecorder.setNeedRecording(false)
                    audioRecorder.stopRecording()
                    Log.d(TAG, "setNeedRecording: false")
                    setStateText("State: stop recording audio.", true)
                    Toast.makeText(
                        applicationContext, "close audio", Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "set audio error: $e")
                Toast.makeText(
                    applicationContext, "set audio error: $e", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun registerUserText() {
        userText = findViewById(R.id.user_text)
        userText?.setText(uid)
        userTextBtn = findViewById(R.id.upload_user_text)

        viewBinding.outerContainer.setOnClickListener { v ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(v?.windowToken, 0)
        }
    }

    private fun registerPerformanceMonitor() {
        performanceMonitorView = ViewModelProvider(this)[PerformanceMonitorViewModel::class.java]
        viewBinding.performanceMonitor = performanceMonitorView
        performanceMonitorView.reset()
    }

    private fun registerServerSpinner() {
        serverSpinner = findViewById(R.id.server_spinner)
        serverSpinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                try {
                    val server_ips = resources.getStringArray(R.array.server_ips)
                    server_url = server_ips[pos]
                    aliRecorder.setServerUrl(server_url)
                    Toast.makeText(applicationContext, "server: $server_url", Toast.LENGTH_SHORT)
                        .show()
                    if (this@HomeActivity::pullResponseJob.isInitialized) {
                        pullResponseJob.cancel()
                        pullResponseJob = GlobalScope.launch {
                            pullResponseLoop()
                        }
                    }
                    Log.i("Stream", "Update url: $updateUrl")
                } catch (e: Exception) {
                    Log.e(TAG, "set url error: $e")
                    Toast.makeText(applicationContext, "set url error: $e", Toast.LENGTH_LONG)
                        .show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        })
    }

    private fun setStateText(text: String, append: Boolean = false, type: Int = LOGMSG) {
        var htmlText: String = ""
        when (type) {
            INFOMSG -> htmlText = "<font color='yellow'>$text</font>"
            ERRMSG -> htmlText = "<font color='red'>$text</font>"
            LOGMSG -> htmlText = text
            else -> {
                htmlText = text
                Log.e(TAG, "message type not allowed!")
            }
        }

        val stateText: TextView? = findViewById(R.id.state_text)
        val stateScroll: ScrollView? = findViewById(R.id.state_scroll)
        if (append) {
            stateQueue.add(htmlText)
        } else {
            stateQueue.clear()
            stateQueue.add(htmlText)
        }

        var displaySpan: Spanned
        var displayText = ""
        if (stateQueue.size >= 30) {
            stateQueue.remove()
        }
        for (item in stateQueue) {
            displayText += "$item<br>"
        }
        displaySpan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(displayText)
        }
        runOnUiThread { stateText?.setText(displaySpan) }
        stateScroll?.fullScroll(View.FOCUS_DOWN)
    }

    private fun setResponseText(text: String) {
        responseText = findViewById(R.id.response_text)
        responseScroll = findViewById(R.id.response_scroll)
        responseQueue.add(text)

        if (responseQueue.size >= 30) {
            responseQueue.remove()
        }

        var display_text = ""
        for (item in responseQueue) {
            if (item.startsWith("<User>")) {
                if (item.substring(7).contains("User seems to be absent, say something related to him or some health tips.")) {
                    display_text += "<font color='#009900' weight='600'>System: </font>" +
                            item.substring(7) + "<br>"
                    continue
                }
                display_text += "<font color='#FF9900' weight='600'>User: </font>" +
                        item.substring(7) + "<br>"
//                resetTriggerCount()

            } else {
                display_text += "$item<br>"
            }
        }
        Log.e("display string 123", responseQueue.last())

        var displaySpan: Spanned
        displaySpan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(display_text, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(display_text)
        }
        runOnUiThread { responseText?.setText(displaySpan) }
        responseScroll?.fullScroll(View.FOCUS_DOWN)
    }





    private var isFirstStart = true

    override fun onStart() {
        super.onStart()
        if (isFirstStart) {
            // 此处执行初始化操作
            isFirstStart = false
        }
    }

    override fun onStop() {
        super.onStop()
        setStateText("State: stop event triggered.", true)
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstStart) {
            startAudioAndVideo()
            setStateText("State: resumed", true)
        }
    }

    override fun onPause() {
        super.onPause()
        stopAudioAndVideo()
        setStateText("State: paused", true)
    }


    private fun run() {
        imageCapturer.startCapturing()
        imageCapturer.setImageSize(640, 480)
        aliRecorder.getTokenThenInitAliSDK()
        aliRecorder.initRecorder()
        audioRecorder.startRecording()

        val assets = Assets(application)
        val assetsDir = assets.syncAssets()
        speechRecognizer = SpeechRecognizerSetup.defaultSetup()
            .setAcousticModel(File(assetsDir, "models/en-us-ptm-8khz"))
            .setDictionary(File(assetsDir, "models/lm/words.dic"))
            .setKeywordThreshold(1.0E-10F) // set lower to make 'stop' easier to be detected
            .setSampleRate(8000)
            .recognizer
        speechRecognizer.addKeyphraseSearch("HOT_WORD_SEARCH", "ok")
        speechRecognizer.addListener(mRecognizerListener)
        if (speechRecognizer == null) {
            Toast.makeText(
                applicationContext,
                "No speech recognition service in this device, interruption disabled",
                Toast.LENGTH_LONG
            ).show()
        }
        isListening = true
        speechRecognizer.startListening("HOT_WORD_SEARCH")
        setStateText("State: Waiting for voice.", true)

        pullResponseTask()

        Timer().schedule(object : TimerTask() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun run() {
                pushData()
            }
        }, 0, 100)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pushData(is_active: Int = 0) {
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
            data.put("is_active", is_active)
            val mAudioFile = getAudio()
            val mImageFile = getImage()
            val mGPSFile = getGPS()

            if (is_active == 1) {
                try {
                    Log.d(TAG, "push active data: $data")
                    uploadServer("$server_url/heartbeat", data, null, null)
                } catch (e: JSONException){
                    Log.e(TAG, "Error while putting active data into JSONObject", e)
                }

            }

            if (mGPSFile != null) {
                try {
                    data.put("longitude", mGPSFile.first)
                    data.put("latitude", mGPSFile.second)
                    Log.d(TAG, "GPSData for upload: $data")

                    // 执行上传操作或其他相关操作
                    Log.d(TAG, "heartbeat: $data")
                    uploadServer("$server_url/heartbeat", data, null, null)
                } catch (e: JSONException) {
                    Log.e(TAG, "Error while putting GPS data into JSONObject", e)
                }
            }

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
            ).show()
            Looper.loop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getAudio(): File? {
        try {
            val data = audioQueue.poll()
            if (data != null) {
                val f = Files.createTempFile(Paths.get(tempFilePath), "audio", ".pcm")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAudio error: $e")
            Looper.prepare()
            Toast.makeText(
                applicationContext, "getAudio error: $e", Toast.LENGTH_LONG
            ).show()
            Looper.loop()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getImage(): File? {
        try {
            val data = imageQueue.poll()
            if (data != null) {
                val f = Files.createTempFile(Paths.get(tempFilePath), "image", ".jpeg")
                Files.write(f, data)
                return f.toFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getImage error: $e")
            Looper.prepare()
            Toast.makeText(
                applicationContext, "getImage error: $e", Toast.LENGTH_LONG
            ).show()
            Looper.loop()
        }
        return null
    }

    private fun getGPS(): Pair<Float, Float>? {
        try {
            if (gpsQueue.isEmpty()) {
                return null
            }
            val data = gpsQueue.poll()
            if (data != null && data.size == 2) {
                val longitude = data[0]
                val latitude = data[1]

                Log.i(TAG, "Received GPS data: Longitude = $longitude, Latitude = $latitude")

                return Pair(longitude, latitude)
            } else {
                Log.e(TAG, "Invalid GPS data format or queue is empty: $data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while retrieving GPS data from queue", e)
        }
        return null
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

        val currentMills = System.currentTimeMillis()

        val request = Request.Builder().url(url).post(requestBody.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                response.body!!.close()
                val uploadTime = System.currentTimeMillis() - currentMills
                performanceMonitorView.setUploadDelay(uploadTime)
                voiceFile?.delete()
                sceneFile?.delete()
            }
        })
    }

    private fun pullResponseTask() {
        try {
            pullResponse()
        } catch (e: Exception) {
            Log.e(TAG, "pullResponse error: $e")
            Toast.makeText(
                applicationContext, "pullResponse error: $e", Toast.LENGTH_LONG
            ).show()
        }
        if (!this::pullResponseJob.isInitialized || pullResponseJob.isCancelled) {
            pullResponseJob = GlobalScope.launch {
                pullResponseLoop()
            }
        }

        GlobalScope.launch {
            while (true) {
                try {
                    if (voiceQueue.isEmpty()) {
                        continue
                    }

                    mediaPlayer.setDataSource(voiceQueue.remove())
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    audioRecorder.stopRecording()
                    while (mediaPlayer.isPlaying) {
                    }
                    audioRecorder.startRecording()
                    mediaPlayer.reset()
                } catch (e: Exception) {
                    Log.e(TAG, "playback error: $e")
                    Looper.prepare()
                    Toast.makeText(
                        applicationContext,
                        "playback error: $e",
                        Toast.LENGTH_LONG
                    ).show()
                    Looper.loop()
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
        val startMills = System.currentTimeMillis()
        val url = "$server_url/response/$uid?is_first=$is_first"
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
                    val text = res.getJSONObject("""message""").getString("text")
                    val voice = res.getJSONObject("message").getString("voice")
                    Log.i("onResponse voice: ", voice)
                    Log.d(TAG, "reply: $text")
                    setResponseText(text)
                    if (text == "[INTERRUPT]") {
                        voiceQueue.clear()
                    }
                    voiceQueue.add(voice)
                    is_first = false

                    // 仅在有对话内容时重置计时器
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "有对话内容时triggerCount")
//                        resetTriggerCount()
//                        resetInactivityTimer()
                    }
                }

                val requestTime = System.currentTimeMillis() - startMills
                performanceMonitorView.setPullDelay(requestTime)

                response.body!!.close()
            }
        })
    }

    private fun pullStreamResponse() {
        var startMills = System.currentTimeMillis()
        val url = "$server_url/response/stream/$uid"
        Log.i(TAG, "pullStreamResponse start: $url")
        val client = OkHttpClient.Builder().readTimeout(604800, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        val response = call.execute()

        val input = response.body!!.byteStream()
        val buffer = BufferedReader(InputStreamReader(input))

        var firstResPkgFlag = false
        var pkgCounter = 0
        var firstPkgMills = System.currentTimeMillis()
        try {
            while (true) {
                if (updateUrl) {
                    call.cancel()
                    updateUrl = false
                    break
                }

                val strBuffer = buffer.readLine() ?: break
                Log.i(TAG, "pullStreamResponse: $strBuffer")
                val responseObj = JSONObject(strBuffer)
                val status = responseObj.getInt("status")
                val res = responseObj.getJSONObject("response")
                if (status == 1) {
                    Log.i("onResponse", res.toString())
                    var resStartTime = res.getJSONObject("message").get("start_time").toString()
                    if (resStartTime != "null") {
                        currentResId = resStartTime
                        Log.e("onResponse_start time", currentResId)
                    }
                    if (resStartTime == banResId) {
                        continue
                    }
                    val text = res.getJSONObject("message").getString("text")
                    val voice = res.getJSONObject("message").getString("voice")

                    if (res.has("extra")) {
                        val extra = res.getJSONObject("extra")
                        val extraMap = mutableMapOf<String, String>()
                        val keys = extra.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            extraMap[key] = extra.getString(key)
                        }
                        performanceMonitorView.setExtraStatistics(extraMap)
                    }
                    Log.i("onResponse voice: ", voice)
                    setResponseText(text)
                    Log.d(TAG, "reply text: $text")

                    if (text.startsWith("[")) {
                        pkgCounter = 0
                        firstResPkgFlag = true
                        if (text == "[INTERRUPT]") {
                            voiceQueue.clear()
                        } else if (text == "[UNDER_PROCESSING]") {
                            timerUtil.startTimer(TIMER_SERVER_PROCESSING)
                        }
                    } else if (text.startsWith("<User>")) {
                    } else {
                        pkgCounter += 1
                        if (firstResPkgFlag) {
                            firstPkgMills = System.currentTimeMillis()
                            firstResPkgFlag = false
                        } else {
                            val pullDelay = System.currentTimeMillis() - firstPkgMills
                            performanceMonitorView.setPullDelay(pullDelay / pkgCounter)
                            firstPkgMills = System.currentTimeMillis()
                        }

                        val processingMilSecs = timerUtil.stopTimer(TIMER_SERVER_PROCESSING)
                        performanceMonitorView.setProcessingDelay(processingMilSecs)
                    }
                    if (voice.isNotEmpty()) {
                        voiceQueue.add(voice)
                        Log.d(TAG, "voice:"+voice)
//                        resetInactivityTimer()
                    }

                }
            }
        } catch (e: Exception) {
            response.body!!.close()
            Log.e(TAG, "pullStreamResponse error: $e")
        }
    }

    var locationListener = LocationListener { location ->
        updateLocationInfo(location)
    }

    private fun updateLocationInfo(location: Location) {
        val longitude = location.longitude
        val latitude = location.latitude
        val gpsData = floatArrayOf(longitude.toFloat(), latitude.toFloat())
        try {
            gpsQueue.offer(gpsData)
        } catch (e: Exception) {
            Log.e(TAG, "GpsToQueue error: $e")
        }
        GPSView?.setText("经度：$longitude, 纬度：$latitude")
        for (array in gpsQueue) {
            Log.d(TAG, "Array in gpsQueue: ${array.contentToString()}")
        }
    }

    private fun isInterruptKeyword(input: String): Boolean {
        val lowercaseInput = input.lowercase()
        val keywords = listOf("等一下", "别急", "你先听我说", "别说了", "ok")

        for (keyword in keywords) {
            if (lowercaseInput.contains(keyword.lowercase())) {
                return true
            }
        }
        return false
    }

    fun checkNet(view: View) {
        dlContainer?.openDrawer(Gravity.LEFT)
    }

    fun deleteCache() {
        try {
            val dir = this.cacheDir
            deleteDir(dir)
        } catch (e: Exception) {
            Log.e(TAG, "deleteCache error: $e")
            Toast.makeText(
                applicationContext, "deleteCache error: $e", Toast.LENGTH_LONG
            ).show()
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
    }
}
