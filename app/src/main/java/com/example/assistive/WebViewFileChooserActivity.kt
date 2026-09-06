package com.example.assistive

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

class WebViewFileChooserActivity : ComponentActivity() {

    companion object {
        var currentCallback: ValueCallback<Array<Uri>>? = null
        var currentParams: FileChooserParams? = null

        fun cancelPendingCallback() {
            try {
                currentCallback?.onReceiveValue(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentCallback = null
            currentParams = null
        }
    }

    private var cameraImageUri: Uri? = null
    private var cameraImageFile: File? = null
    private var isResultHandled = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePickerResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            savedInstanceState.getString("camera_uri")?.let {
                cameraImageUri = Uri.parse(it)
            }
            savedInstanceState.getString("camera_file_path")?.let {
                cameraImageFile = File(it)
            }
        }

        if (currentCallback == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            launchChooser()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraImageUri?.let { outState.putString("camera_uri", it.toString()) }
        cameraImageFile?.let { outState.putString("camera_file_path", it.absolutePath) }
    }

    private fun launchChooser() {
        try {
            val params = currentParams
            val acceptTypes = params?.acceptTypes?.filter { it.isNotBlank() } ?: emptyList()
            val isCaptureEnabled = params?.isCaptureEnabled == true

            // Determine if images are accepted (empty accepts everything, or includes image/*)
            val acceptsImages = acceptTypes.isEmpty() || acceptTypes.any {
                it.contains("image", ignoreCase = true) || it == "*/*"
            }

            val cameraIntents = mutableListOf<Intent>()

            if (acceptsImages) {
                try {
                    val photoFile = File.createTempFile("lens_upload_", ".jpg", cacheDir)
                    cameraImageFile = photoFile
                    val photoUri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        photoFile
                    )
                    cameraImageUri = photoUri

                    val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    cameraIntents.add(captureIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // If website explicitly requested direct camera capture and camera is ready
            if (isCaptureEnabled && cameraIntents.isNotEmpty()) {
                filePickerLauncher.launch(cameraIntents.first())
                return
            }

            // Create base file selection intent from WebView params
            val contentIntent = try {
                params?.createIntent() ?: createFallbackContentIntent(acceptsImages)
            } catch (e: Exception) {
                createFallbackContentIntent(acceptsImages)
            }

            val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, contentIntent)
                putExtra(Intent.EXTRA_TITLE, params?.title ?: "Upload Image or File")
                if (cameraIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toTypedArray())
                }
            }

            filePickerLauncher.launch(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: simple GET_CONTENT
            try {
                val fallback = createFallbackContentIntent(true)
                filePickerLauncher.launch(Intent.createChooser(fallback, "Select Image"))
            } catch (ex: Exception) {
                ex.printStackTrace()
                cancelPendingCallback()
                finish()
            }
        }
    }

    private fun createFallbackContentIntent(acceptsImages: Boolean): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (acceptsImages) "image/*" else "*/*"
        }
    }

    private fun handlePickerResult(resultCode: Int, data: Intent?) {
        isResultHandled = true
        val callback = currentCallback
        if (callback == null) {
            finish()
            return
        }

        var results: Array<Uri>? = null

        if (resultCode == Activity.RESULT_OK) {
            val file = cameraImageFile
            // Check if captured by camera (data is null or data.data is null, but camera file has size)
            if (file != null && file.exists() && file.length() > 0 && cameraImageUri != null) {
                if (data == null || (data.data == null && (data.clipData == null || data.clipData?.itemCount == 0))) {
                    results = arrayOf(cameraImageUri!!)
                }
            }

            if (results == null) {
                // If chosen from gallery/file picker
                results = FileChooserParams.parseResult(resultCode, data)
                if (results == null && data?.data != null) {
                    results = arrayOf(data.data!!)
                }
            }
        }

        try {
            callback.onReceiveValue(results)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentCallback = null
        currentParams = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isResultHandled && isFinishing) {
            cancelPendingCallback()
        }
    }
}
