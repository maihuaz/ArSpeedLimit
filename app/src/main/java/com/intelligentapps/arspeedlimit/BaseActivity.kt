package com.intelligentapps.arspeedlimit

import android.Manifest.permission.*
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.mapbox.vision.mobile.core.utils.SystemInfoUtils
import com.mapbox.vision.utils.VisionLogger


abstract class BaseActivity : AppCompatActivity() {
    protected abstract fun onPermissionsGranted()
    protected abstract fun initViews()
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SystemInfoUtils.isVisionSupported()) {
            val textView = TextView(this)
            val padding = dpToPx(20f).toInt()
            textView.setPadding(padding, padding, padding, padding)
            textView.setMovementMethod(LinkMovementMethod.getInstance())
            textView.setClickable(true)
            textView.setText(
                HtmlCompat.fromHtml(
                    getString(R.string.vision_not_supported_message),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.vision_not_supported_title)
                .setView(textView)
                .setCancelable(false)
                .show()
            VisionLogger.Companion.e(
                "BoardNotSupported",
                "System Info: [" + SystemInfoUtils.obtainSystemInfo() + "]"
            )
        }
        initViews()
        title = getString(R.string.app_name) + " " + this.javaClass.simpleName
        if (!allPermissionsGranted()) {
            requestPermissions(requiredPermissions, PERMISSIONS_REQUEST_CODE)
        } else {
            onPermissionsGranted()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                // PERMISSION_FOREGROUND_SERVICE was added for targetSdkVersion >= 28, it is normal and always granted, but should be added to the Manifest file
                // on devices with Android < P(9) checkSelfPermission(PERMISSION_FOREGROUND_SERVICE) can return PERMISSION_DENIED, but in fact it is GRANTED, so skip it
                // https://developer.android.com/guide/components/services#Foreground
                if (Build.VERSION.SDK_INT > VERSION_CODES.R) {
                    if (permission == PERMISSION_FOREGROUND_SERVICE || permission == WRITE_EXTERNAL_STORAGE
                        || permission == READ_EXTERNAL_STORAGE
                    ) {
                        continue
                    }
                } else {
                    if (permission == READ_MEDIA_IMAGES || permission == READ_MEDIA_VIDEO
                        || permission == READ_MEDIA_AUDIO
                    ) {
                        continue
                    }
                }
                return false
            }
        }
        return true
    }

    private val requiredPermissions: Array<String>
        get() {
            val permissions: Array<String>
            permissions = try {
                val info: PackageInfo = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_PERMISSIONS
                )
                val requestedPermissions: Array<String> = info.requestedPermissions
                if (requestedPermissions.size > 0) {
                    requestedPermissions
                } else {
                    arrayOf<String>()
                }
            } catch (e: PackageManager.NameNotFoundException) {
                arrayOf<String>()
            }
            return permissions
        }

    private fun dpToPx(dp: Float): Float {
        return dp * getApplicationContext<Context>().getResources()
            .getDisplayMetrics().density
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (allPermissionsGranted() && requestCode == PERMISSIONS_REQUEST_CODE) {
            onPermissionsGranted()
        }
    }

    companion object {
        private const val PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
        private const val PERMISSIONS_REQUEST_CODE = 123
    }
}
