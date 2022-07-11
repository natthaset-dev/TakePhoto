package com.ocps.takephoto

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1
        private const val READ_EXTERNAL_STORAGE_PERMISSION_CODE = 2
    }

    private var currentPhotoPath: String? = null

    private var resultCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val takenPhoto: Bitmap = BitmapFactory.decodeFile(currentPhotoPath)
            val rotatePhoto = rotateBitmap(takenPhoto, 90f)
            imvPhoto.setImageBitmap(rotatePhoto)
            btnUpload.isEnabled = true
        }
    }

    private var resultGalleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val filePath = result.data?.data!!
            imvPhoto.setImageURI(filePath)
            btnUpload.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnUpload.isEnabled = false

        btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            }
        }

        btnGallery.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                selectPhoto()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE_PERMISSION_CODE)
            }
        }

        btnUpload.setOnClickListener {
            // show dialog
            val dialog = Dialog(it.context, android.R.style.Theme_Translucent_NoTitleBar)
            val progressBarView = this.layoutInflater.inflate(R.layout.full_screen_progress_bar, null)
            dialog.setContentView(progressBarView)
            dialog.setCancelable(false)
            dialog.show()

            try {
                // convert bitmap from imageview
                val bitmap = (imvPhoto.drawable as BitmapDrawable).bitmap
                val stream = ByteArrayOutputStream()
                bitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val byteArray = stream.toByteArray()

                // Web APIs url
                val url = "http://202203-as-pui.ocps.co.th/workshop/api/images"
                val client = OkHttpClient()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("", "fn.jpg",
                        byteArray.toRequestBody("image/*.jpg".toMediaTypeOrNull(), 0, byteArray.size)
                    ).build()
                val request: Request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object: Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (response.isSuccessful) {
                                runOnUiThread {
                                    dialog.dismiss()
                                    imvPhoto.setImageResource(0)
                                    btnUpload.isEnabled = false
                                    showToast("Upload photo successfully")
                                }
                            } else {
                                runOnUiThread {
                                    dialog.dismiss()
                                    showToast("Error with status code ${response.code}")
                                }
                            }
                        }
                    }
                    @SuppressLint("SetTextI18n")
                    override fun onFailure(call: Call, e: IOException) {
                        call.cancel()
                        runOnUiThread {
                            dialog.dismiss()
                            showToast("Error with message ${e.message}")
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        imvPhoto.setOnClickListener {
            when (imvPhoto.rotation) {
                0f -> {
                    imvPhoto.rotation = 90f
                }
                90f -> {
                    imvPhoto.rotation = 180f
                }
                180f -> {
                    imvPhoto.rotation = 270f
                }
                else -> {
                    imvPhoto.rotation = 0f
                }
            }
        }
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    showToast("Unable to open camera")
                }
            }
            READ_EXTERNAL_STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    selectPhoto()
                } else {
                    showToast("Unable to open photo gallery")
                }
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun takePhoto() {
        val fileName = "photo"
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        try {
            val imageFile = File.createTempFile(fileName, ".jpg", storageDirectory)
            currentPhotoPath = imageFile.absolutePath
            val uri = FileProvider.getUriForFile(this, "com.ocps.takephoto.fileprovider", imageFile)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            resultCameraLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun selectPhoto() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        resultGalleryLauncher.launch(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}