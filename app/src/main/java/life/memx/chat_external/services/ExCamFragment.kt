package life.memx.chat_external.services

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.CaptureMediaView
import com.jiangdg.ausbc.widget.IAspectRatio
import life.memx.chat_external.databinding.FragmentDemoBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Queue

class ExCamFragment internal constructor(
    var queue: Queue<ByteArray>
) : CameraFragment(), View.OnClickListener, CaptureMediaView.OnViewClickListener {
    private var mMoreMenu: PopupWindow? = null


    private var mCameraMode = CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC

    private lateinit var mViewBinding: FragmentDemoBinding
    private var needCapturing = true
    private lateinit var cacheDir: String
    private val rotate = 270 // TODO


    fun startCapturing() {

        val handler = Handler(Looper.getMainLooper())

        val runTask: Runnable = object : Runnable {
            override fun run() {
                handler.postDelayed(this, 10000) // 拍照间隔
                if (!needCapturing) {
                    Log.i(TAG, "Don't need capturing")
                    return
                }
                if (!isCameraOpened()){
                    initView()
                }
                captureImage()

            }
        }
        handler.post(runTask)
    }

    fun setNeedCapturing(b: Boolean) {
        needCapturing = b
    }

    fun stopCapturing() {
        closeCamera()
    }

    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper()) {

            true
        }
    }



    override fun initView() {
        unRegisterMultiCamera()

        registerMultiCamera()

        super.initView()
        Logger.i(
            TAG,
            "initView, cams = ${getDeviceList()}"
        )
        Logger.i(TAG, "getDefaultCamera(): ${getDefaultCamera()}")

        mViewBinding.lensFacingBtn1.setOnClickListener(this)
        mViewBinding.captureBtn.setOnViewClickListener(this)
        switchLayoutClick()

        cacheDir = requireContext().cacheDir.absolutePath
    }

    override fun initData() {
        super.initData()

    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        Logger.w(
            TAG,
            "onCameraState: $code"
        )
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        Logger.e(
            TAG,
            "camera opened error: $msg"
        )
        ToastUtils.show("camera opened error: $msg")
    }

    private fun handleCameraClosed() {
        ToastUtils.show("camera closed success")
    }

    private fun handleCameraOpened() {

//        mViewBinding.brightnessSb.progress =
//            (getCurrentCamera() as? CameraUVC)?.getBrightness() ?: 0
//        Logger.i(
//            TAG,
//            "max = ${mViewBinding.brightnessSb.max}, progress = ${mViewBinding.brightnessSb.progress}"
//        )
//        mViewBinding.brightnessSb.setOnSeekBarChangeListener(object :
//            SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                (getCurrentCamera() as? CameraUVC)?.setBrightness(progress)
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar?) {
//
//            }
//
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {
//
//            }
//        })
        ToastUtils.show("camera opened success")
    }

    private fun switchLayoutClick() {
        updateCameraModeSwitchUI()

//        showRecentMedia()
    }

    override fun getCameraView(): IAspectRatio {
        return AspectRatioTextureView(requireContext())
    }

    override fun getCameraViewContainer(): ViewGroup {
        return mViewBinding.cameraViewContainer
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun getGravity(): Int = Gravity.CENTER

    override fun onViewClick(mode: CaptureMediaView.CaptureMode?) {
        ToastUtils.show("onViewClick!")
        if (!isCameraOpened()) {
            ToastUtils.show("camera not worked!")
            return
        }
        when (mode) {
            CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC -> {
                captureImage()
            }

            else -> {

            }
        }
    }

    private fun uploadImage(img_path: String) {
        if (rotate != 0) {
            val sourceBitmap = BitmapFactory.decodeFile(img_path)
            val matrix = Matrix()
            matrix.postRotate(rotate.toFloat())
            matrix.postScale(-1.0f, 1.0f)
            val rotatedBitmap =
                Bitmap.createBitmap(
                    sourceBitmap,
                    0,
                    0,
                    sourceBitmap.width,
                    sourceBitmap.height,
                    matrix,
                    true
                )

            val out = ByteArrayOutputStream()
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)

            val bytes = out.toByteArray()
            queue.add(bytes)
        } else {
            val bytes = File(img_path).readBytes()
            queue.add(bytes)
        }

    }

    private fun captureImage() {
        val savePath = cacheDir + System.currentTimeMillis().toString() + ".jpg"
        captureImage(object : ICaptureCallBack {
            override fun onBegin() {
//                mTakePictureTipView.show("", 100)
            }

            override fun onError(error: String?) {
                Logger.e(TAG, error ?: "未知异常")
                ToastUtils.show(error ?: "未知异常")
            }

            override fun onComplete(path: String?) {
                if (path != null){
                    uploadImage(path)
                }
                closeCamera()
            }
        }, savePath = savePath)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onClick(v: View?) {
//        if (! isCameraOpened()) {
//            ToastUtils.show("camera not worked!")
//            return
//        }
        Logger.w(
            TAG,
            "onClick, cams = ${getDeviceList()}"
        )
        clickAnimation(v!!, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                when (v) {
                    mViewBinding.lensFacingBtn1 -> {
                        getCurrentCamera()?.let { strategy ->
                            if (strategy is CameraUVC) {
                                showUsbDevicesDialog(getDeviceList(), strategy.getUsbDevice())
                                return
                            }
                        }
                    }


                    else -> {
                    }
                }
            }
        })
    }

    @SuppressLint("CheckResult")
    private fun showUsbDevicesDialog(
        usbDeviceList: MutableList<UsbDevice>?,
        curDevice: UsbDevice?
    ) {
        ToastUtils.show("showUsbDevicesDialog")
        if (usbDeviceList.isNullOrEmpty()) {
            ToastUtils.show("Get usb device failed")
            return
        }
        val list = arrayListOf<String>()
        var selectedIndex: Int = -1
        for (index in (0 until usbDeviceList.size)) {
            val dev = usbDeviceList[index]
            val devName =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !dev.productName.isNullOrEmpty()) {
                    "${dev.productName}(${curDevice?.deviceId})"
                } else {
                    dev.deviceName
                }
            val curDevName =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !curDevice?.productName.isNullOrEmpty()) {
                    "${curDevice!!.productName}(${curDevice.deviceId})"
                } else {
                    curDevice?.deviceName
                }
            if (devName == curDevName) {
                selectedIndex = index
            }
            list.add(devName)
        }
        MaterialDialog(requireContext()).show {
            listItemsSingleChoice(
                items = list,
                initialSelection = selectedIndex
            ) { dialog, index, text ->
                if (selectedIndex == index) {
                    return@listItemsSingleChoice
                }
                switchCamera(usbDeviceList[index])
            }
        }
    }


    @SuppressLint("CheckResult")
    private fun showResolutionDialog() {
        mMoreMenu?.dismiss()
        getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                ToastUtils.show("Get camera preview size failed")
                return
            }
            val list = arrayListOf<String>()
            var selectedIndex: Int = -1
            for (index in (0 until previewSizes.size)) {
                val w = previewSizes[index].width
                val h = previewSizes[index].height
                getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }
            MaterialDialog(requireContext()).show {
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { dialog, index, text ->
                    if (selectedIndex == index) {
                        return@listItemsSingleChoice
                    }
                    updateResolution(previewSizes[index].width, previewSizes[index].height)
                }
            }
        }
    }


    private fun getVersionName(): String? {
        context ?: return null
        val packageManager = requireContext().packageManager
        try {
            val packageInfo = packageManager?.getPackageInfo(requireContext().packageName, 0)
            return packageInfo?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    private fun goToGalley() {
        try {
            Intent(
                Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).apply {
                startActivity(this)
            }
        } catch (e: Exception) {
            ToastUtils.show("open error: ${e.localizedMessage}")
        }
    }

    private fun updateCameraModeSwitchUI() {

        mViewBinding.captureBtn.setCaptureViewTheme(CaptureMediaView.CaptureViewTheme.THEME_WHITE)
        val height = mViewBinding.controlPanelLayout.height
        mViewBinding.captureBtn.setCaptureMode(mCameraMode)
        if (mCameraMode == CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC) {
            val translationX = ObjectAnimator.ofFloat(
                mViewBinding.controlPanelLayout,
                "translationY",
                height.toFloat(),
                0.0f
            )
            translationX.duration = 600
            translationX.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    mViewBinding.controlPanelLayout.visibility = View.VISIBLE
                }
            })
            translationX.start()
        } else {
            val translationX = ObjectAnimator.ofFloat(
                mViewBinding.controlPanelLayout,
                "translationY",
                0.0f,
                height.toFloat()
            )
            translationX.duration = 600
            translationX.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    mViewBinding.controlPanelLayout.visibility = View.INVISIBLE
                }
            })
            translationX.start()
        }
    }

    private fun clickAnimation(v: View, listener: Animator.AnimatorListener) {
        val scaleXAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.4f, 1.0f)
        val scaleYAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.4f, 1.0f)
        val alphaAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.4f, 1.0f)
        val animatorSet = AnimatorSet()
        animatorSet.duration = 150
        animatorSet.addListener(listener)
        animatorSet.playTogether(scaleXAnim, scaleYAnim, alphaAnim)
        animatorSet.start()
    }

    companion object {
        private const val TAG = "DemoFragment"
    }
}


