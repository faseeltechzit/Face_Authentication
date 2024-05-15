package com.example.faceauthentication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.faceauthentication.camerax.CameraManager
import com.example.faceauthentication.databinding.ActivityMainBinding
import com.example.faceauthentication.face_recognonize.FaceStatus
import com.us47codex.liveness_detection.FaceAnalyzer
import com.us47codex.liveness_detection.LivenessDetector
import com.us47codex.liveness_detection.tasks.DetectionTask
import com.us47codex.liveness_detection.tasks.EyesBlinkDetectionTask
import com.us47codex.liveness_detection.tasks.FacingDetectionTask
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        var applicationContext1: Context? = null
        var name: String = ""

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val FACEDATA = "facedata"
        const val VERIFIED = "verify"
        const val EMPLOYEEIMG = "employeeImg"
        const val CAPTUREIMG = "captureimg"
    }

    private var bitmap: Bitmap? = null
    private var result: Float? = null
    private var selfieImg: String? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: LifecycleCameraController
    private var imageFiles = arrayListOf<String>()

    // Initialize the Liveness Detector with specific tasks
    private val livenessDetector = LivenessDetector(
        FacingDetectionTask(),
        EyesBlinkDetectionTask()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationContext1 = this.applicationContext

        // Inflate and set the content view using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request Camera permission and start the camera if granted
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.launch(Manifest.permission.CAMERA)
    }

    // Start the camera and set up the camera controller
    private fun startCamera() {
        cameraController = LifecycleCameraController(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this),
            FaceAnalyzer(buildLivenessDetector())
        )
        cameraController.bindToLifecycle(this)
        binding.cameraPreview.controller = cameraController

        // Switch between front and back camera
        binding.cameraSwitch.setOnClickListener {
            cameraController.cameraSelector =
                if (cameraController.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // Build the Liveness Detector with a listener to handle task events
    private fun buildLivenessDetector(): LivenessDetector {
        val listener = object : LivenessDetector.Listener {
            @SuppressLint("SetTextI18n")
            override fun onTaskStarted(task: DetectionTask) {
                binding.guide.text = task.taskDescription()
            }

            override fun onTaskCompleted(task: DetectionTask, isLastTask: Boolean) {
                takePhoto(
                    File(cacheDir, "${name}_${task.taskName()}_${System.currentTimeMillis()}.jpg")
                ) { file ->
                    imageFiles.add(file.absolutePath)
                    if (isLastTask) {
                        finishForResult()
                    }
                }
            }

            override fun onTaskFailed(task: DetectionTask, code: Int) {
                val message = when (code) {
                    LivenessDetector.ERROR_MULTI_FACES -> "Please make sure there is only one face on the screen."
                    LivenessDetector.ERROR_NO_FACE -> "Please make sure there is a face on the screen."
                    LivenessDetector.ERROR_OUT_OF_DETECTION_RECT -> "Please make sure there is a face in the Rectangle."
                    else -> "${task.taskName()} Failed."
                }
                binding.guide2.text = message
            }
        }

        return livenessDetector.also { it.setListener(listener) }
    }

    // Handle completion of liveness detection tasks
    private fun finishForResult() {
        val result = ArrayList(imageFiles.takeLast(livenessDetector.getTaskSize()))
        setResult(RESULT_OK, Intent().putStringArrayListExtra(ResultContract.RESULT_KEY, result))
        binding.guide.text = getString(R.string.face_liveliness_check_passed)
        binding.guide2.text = getString(R.string.face_recognition_check_started)

        createCameraManager()
        checkForPermission()
        setupUIActions()
    }

    // Capture photo and handle the result
    private fun takePhoto(file: File, onSaved: (File) -> Unit) {
        cameraController.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(e: ImageCaptureException) {
                    e.printStackTrace()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSaved(file)
                }
            }
        )
    }

    // Define contract for result handling between activities
    class ResultContract : ActivityResultContract<Any?, List<String>?>() {

        companion object {
            const val RESULT_KEY = "images"
        }

        override fun createIntent(context: Context, input: Any?): Intent {
            return Intent(context, MainActivity::class.java)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): List<String>? {
            return if (resultCode == RESULT_OK && intent != null) {
                intent.getStringArrayListExtra(RESULT_KEY)
            } else null
        }
    }

    // Check for necessary permissions and start camera if granted
    private fun checkForPermission() {
        if (allPermissionsGranted()) {
            cameraManager.startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    // Set up UI actions and event handlers
    private fun setupUIActions() {
        binding.cameraSwitch.setOnClickListener {
            cameraManager.changeCameraSelector()
        }
    }

    // Handle the result of permission requests
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraManager.startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Create and configure the CameraManager
    private fun createCameraManager() {
        cameraManager = CameraManager(
            this,
            binding.cameraPreview,
            this,
            binding.graphicOverlayFinder,
            ::processPicture,
            ::onFaceDetect
        )
    }

    // Handle face detection results
    private fun onFaceDetect(bitmap: Bitmap, result: Float) {
        this.bitmap = bitmap
        this.result = result
        if (result < 1.0f) {
            Toast.makeText(this, "Verified User", Toast.LENGTH_SHORT).show()
        }
        Log.e("TAG", "onFaceDetect: bitmap : $bitmap result : $result")
    }

    // Process the captured picture (currently empty)
    private fun processPicture(faceStatus: FaceStatus) {}

    // Check if all required permissions are granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}


/*
package com.example.faceauthentication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.example.faceauthentication.camerax.CameraManager
import com.example.faceauthentication.databinding.ActivityMainBinding
import com.example.faceauthentication.face_recognonize.FaceStatus
import com.us47codex.liveness_detection.FaceAnalyzer
import com.us47codex.liveness_detection.LivenessDetector
import com.us47codex.liveness_detection.tasks.DetectionTask
import com.us47codex.liveness_detection.tasks.EyesBlinkDetectionTask
import com.us47codex.liveness_detection.tasks.FacingDetectionTask
import java.io.File

*/
/**
 * MainActivity class that handles face authentication using camera, liveness detection,
 * and face recognition. It captures images, analyzes them for liveness, and performs
 * face recognition based on the results.
 *//*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: LifecycleCameraController
    private lateinit var cameraManager: CameraManager

    private var bitmap: Bitmap? = null
    private var result: Float? = null
    private val imageFiles = arrayListOf<String>()

    // Initialize LivelinessDetector with necessary detection tasks
    private val livenessDetector = LivenessDetector(FacingDetectionTask(), EyesBlinkDetectionTask())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request camera permission
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) setupCamera() else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.launch(Manifest.permission.CAMERA)

        binding.cameraSwitch.setOnClickListener{
            cameraManager.changeCameraSelector()
        }
    }

    */
/**
 * Sets up the camera controller and binds it to the lifecycle of this activity.
 * Also configures the camera switch button to toggle between front and back cameras.
 *//*

    private fun setupCamera() {
        cameraController = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(this@MainActivity),
                FaceAnalyzer(buildLivelinessDetector())
            )
            bindToLifecycle(this@MainActivity)
        }
        binding.cameraPreview.controller = cameraController

        binding.cameraSwitch.setOnClickListener {
            cameraController.cameraSelector =
                if (cameraController.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    */
/**
 * Builds and configures the LivelinessDetector with a listener for task events.
 * This includes handling task started, completed, and failed events.
 *//*

    private fun buildLivelinessDetector(): LivenessDetector {
        val listener = object : LivenessDetector.Listener {
            override fun onTaskStarted(task: DetectionTask) {
                binding.guide.text = task.taskDescription()
            }

            override fun onTaskCompleted(task: DetectionTask, isLastTask: Boolean) {
                takePhoto(File(cacheDir, "${System.currentTimeMillis()}_${task.taskName()}.jpg")) {
                    synchronized(imageFiles) {
                        imageFiles.add(it.absolutePath)
                    }
                    if (isLastTask) finishForResult()
                }
            }

            override fun onTaskFailed(task: DetectionTask, code: Int) {
                val message = when (code) {
                    LivenessDetector.ERROR_MULTI_FACES -> "Please make sure there is only one face on the screen."
                    LivenessDetector.ERROR_NO_FACE -> "Please make sure there is a face on the screen."
                    LivenessDetector.ERROR_OUT_OF_DETECTION_RECT -> "Please make sure there is a face in the Rectangle."
                    else -> "${task.taskName()} Failed."
                }
                binding.guide2.text = message
            }
        }
        return livenessDetector.apply { setListener(listener) }
    }

    */
/**
 * Completes the liveness detection process, collects the captured images,
 * and prepares the result to be sent back to the calling activity.
 *//*

    private fun finishForResult() {
        val resultImages =
            synchronized(imageFiles) { ArrayList(imageFiles.takeLast(livenessDetector.getTaskSize())) }
        setResult(
            RESULT_OK,
            Intent().putStringArrayListExtra(ResultContract.RESULT_KEY, resultImages)
        )
        binding.guide.text = getString(R.string.face_liveliness_check_passed)
        binding.guide2.text = getString(R.string.face_recognition_check_started)
        setupCameraManager()
    }

    */
/**
 * Captures a photo and saves it to the specified file.
 *
 * @param file The file to save the captured image to.
 * @param onSaved Callback invoked when the image is successfully saved.
 *//*

    private fun takePhoto(file: File, onSaved: (File) -> Unit) {
        cameraController.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(e: ImageCaptureException) {
                    e.printStackTrace()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSaved(file)
                }
            }
        )
    }

    */
/**
 * Custom ActivityResultContract to handle results from MainActivity.
 *//*

    class ResultContract : ActivityResultContract<Any?, List<String>?>() {
        companion object {
            const val RESULT_KEY = "images"
        }

        override fun createIntent(context: Context, input: Any?) =
            Intent(context, MainActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            if (resultCode == RESULT_OK) intent?.getStringArrayListExtra(RESULT_KEY) else null
    }

    */
/**
 * Sets up the CameraManager which handles camera operations, preview, and graphic overlays.
 *//*

    private fun setupCameraManager() {
        cameraManager = CameraManager(
            this,
            binding.cameraPreview,
            this,
            binding.graphicOverlayFinder,
            ::processPicture,
            ::onFaceDetect
        )
    }

    */
/**
 * Handles face detection results, updates the bitmap and result variables,
 * and displays a toast if the user is verified.
 *
 * @param bitmap The detected face bitmap.
 * @param result The confidence score of the detected face.
 *//*

    private fun onFaceDetect(bitmap: Bitmap, result: Float) {
        this.bitmap = bitmap
        this.result = result
        if (result < 1.0f) Toast.makeText(this, "Verified User", Toast.LENGTH_SHORT).show()
        Log.e("TAG", "onFaceDetect: bitmap : $bitmap result : $result")
    }

    */
/**
 * Processes the picture for face status. To be implemented as needed.
 *
 * @param faceStatus The face status object.
 *//*

    private fun processPicture(faceStatus: FaceStatus) {}

    */
/**
 * Checks if all required permissions are granted.
 *
 * @return True if all permissions are granted, false otherwise.
 *//*

    private fun allPermissionsGranted() = arrayOf(Manifest.permission.CAMERA).all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    */
/**
 * Handles the result of permission requests.
 *
 * @param requestCode The request code passed in requestPermissions.
 * @param permissions The requested permissions.
 * @param grantResults The grant results for the corresponding permissions.
 *//*

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            setupCamera()
        } else {
            Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
*/
