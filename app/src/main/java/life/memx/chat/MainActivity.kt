package life.memx.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import life.memx.chat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val TAG: String = MainActivity::class.java.simpleName
    private var uid: String = ""

    private var server_url: String = "https://samantha.memx.life"

    private val PERMISSIONS_REQUIRED: Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private lateinit var viewBinding: ActivityMainBinding

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewBinding.lifecycleOwner = this
        viewBinding.mainActivity = this

        uid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
        uid = sharedPreferences.getString("uid", uid).toString()

        // 设置输入框的默认值为上次保存的 ID
        viewBinding.etUserId.setText(uid)

        if (!verifyPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun verifyPermissions(activity: AppCompatActivity) = PERMISSIONS_REQUIRED.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    fun onConfirmClick() {
        val userInput = viewBinding.etUserId.text.toString()
        if (userInput.isNotEmpty()) {
            uid = userInput

            // 保存用户输入的 ID 到 SharedPreferences
            val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString("uid", uid)
            editor.apply()

            checkUserId()
        } else {
            Toast.makeText(this, "Please enter your ID", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserId() {
        val apiService = ApiClient.client.create(ApiService::class.java)
        val request = CheckIDRequest(uid)

        apiService.checkUserId(request).enqueue(object : retrofit2.Callback<CheckIDResponse> {
            override fun onResponse(call: retrofit2.Call<CheckIDResponse>, response: retrofit2.Response<CheckIDResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val checkIDResponse = response.body()!!
                    if (checkIDResponse.status == "allowed") {
                        startApp()
                    } else {
                        Toast.makeText(this@MainActivity, "Access Denied", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Response Code: ${response.code()}")
                    Log.e(TAG, "Response Message: ${response.message()}")
                    Log.e(TAG, "Response Error Body: ${response.errorBody()?.string()}")
                    Toast.makeText(this@MainActivity, "Error: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<CheckIDResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun startApp() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("uid", uid)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
            } else {
                Log.e(TAG, "Permission Denied")
            }
        }
    }
}
