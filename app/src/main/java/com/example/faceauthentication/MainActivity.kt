package com.example.faceauthentication

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
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
/**
 * MainActivity handles the camera and face authentication logic.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        var applicationContext1: Context? = null
        var name: String = ""
    }

    private var bitmap: Bitmap? = null
    private var result: Float? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: LifecycleCameraController

    // Initialize the Liveness Detector with specific tasks
    private val livenessDetector = LivenessDetector(
        FacingDetectionTask(),
        EyesBlinkDetectionTask()
    )

    // ActivityResultLauncher for requesting permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Called when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationContext1 = this.applicationContext

        // Inflate and set the content view using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request Camera permission
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    /**
     * Start the camera and set up the camera controller.
     */
    private fun startCamera() {
        cameraController = LifecycleCameraController(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this),
            FaceAnalyzer(buildLivenessDetector())
        )
        cameraController.bindToLifecycle(this)
        binding.cameraPreview.controller = cameraController
    }

    /**
     * Build the Liveness Detector with a listener to handle task events.
     * @return An instance of [LivenessDetector]
     */
    private fun buildLivenessDetector(): LivenessDetector {
        val listener = object : LivenessDetector.Listener {
            override fun onTaskStarted(task: DetectionTask) {
                binding.guide.text = task.taskDescription()
            }

            override fun onTaskCompleted(task: DetectionTask, isLastTask: Boolean) {
                    if (isLastTask) {
                        finishForResult()
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

    /**
     * Handle completion of liveness detection tasks.
     */
    private fun finishForResult() {
        binding.guide.text = getString(R.string.face_liveliness_check_passed)
        binding.guide2.text = getString(R.string.face_recognition_check_started)

        createCameraManager()
        cameraManager.startCamera()
    }

    /**
     * Create and configure the CameraManager.
     */
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

    /**
     * Handle face detection results.
     * @param bitmap The detected face as a bitmap.
     * @param result The similarity result of the detected face.
     */
    private fun onFaceDetect(bitmap: Bitmap, result: Float) {
        this.bitmap = bitmap
        this.result = result
        if (result < 1.0f) {
            binding.guide.text = getString(R.string.face_recognition_successful)
            binding.guide2.text = getString(R.string.verified_user)
        }
        Log.e("TAG", "onFaceDetect: bitmap : $bitmap result : $result")
    }

    /**
     * Process the captured picture (currently empty).
     * @param faceStatus The status of the detected face.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun processPicture(faceStatus: FaceStatus) {
        // Currently empty. Implement as needed.
    }
}
