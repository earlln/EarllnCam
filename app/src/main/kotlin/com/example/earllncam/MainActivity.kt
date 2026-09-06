package com.example.earllncam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Launcher entry point. Runs the whole capture sequence with zero taps:
 * ask for CAMERA once if needed, alternate front/back shots per AppSettings,
 * wipe the previous EarllnCam album on success, then finish() back to the
 * home screen. Two small buttons sit along the bottom edge (out of the way of
 * the status bar / gesture area) so the capture log and the options are
 * reachable even mid-sequence; tapping either, or leaving this screen any
 * other way, cancels the capture and unbinds the camera instead of leaving a
 * stalled session behind. The version badge is shown at the bottom.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Set while a system dialog is on top of us, so onPause does not read that
    // as the user walking away mid-capture and kill the sequence.
    private var awaitingSystemDialog = false
    private var deleteConsent: CancellableContinuation<Unit>? = null

    private val requestDeleteConsent = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        awaitingSystemDialog = false
        deleteConsent?.takeIf { it.isActive }?.resume(Unit)
        deleteConsent = null
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginSequence()
        } else {
            showStatusThenFinish(getString(R.string.status_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        findViewById<TextView>(R.id.versionText).text = AppVersion.label(this)
        findViewById<FloatingActionButton>(R.id.galleryButton).setOnClickListener {
            stopAndOpen(GalleryActivity::class.java)
        }
        findViewById<FloatingActionButton>(R.id.settingsButton).setOnClickListener {
            stopAndOpen(SettingsActivity::class.java)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            beginSequence()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun beginSequence() {
        captureJob = lifecycleScope.launch {
            runCatching { runSequence() }
                .onFailure { showStatusThenFinish(getString(R.string.status_camera_error)) }
        }
    }

    private fun stopAndOpen(target: Class<*>) {
        if (isFinishing) return
        captureJob?.cancel()
        runCatching { cameraProvider?.unbindAll() }
        startActivity(Intent(this, target))
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (awaitingSystemDialog) return
        // The user switched away (tapped one of the bottom buttons, or left some
        // other way) before the sequence finished on its own. Camera access dies with
        // foreground anyway, so stop cleanly here instead of leaving a stalled
        // session running.
        if (captureJob?.isActive == true) {
            captureJob?.cancel()
            runCatching { cameraProvider?.unbindAll() }
            if (!isFinishing) finish()
        }
    }

    private suspend fun runSequence() {
        val settings = AppSettings(this)
        val count = settings.shotCount
        val mode = settings.mode
        val keepOnlyLatest = settings.keepOnlyLatest

        val previousUris = if (keepOnlyLatest) MediaVault.existingUris(this) else emptyList()

        val cameraProvider = getCameraProvider()
        this.cameraProvider = cameraProvider

        for (i in 0 until count) {
            val useFront = i % 2 == 0
            val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

            statusText.text = when (mode) {
                CaptureMode.PHOTO -> getString(
                    if (useFront) R.string.status_shooting_front else R.string.status_shooting_back,
                    i + 1, count
                )
                CaptureMode.VIDEO -> getString(
                    if (useFront) R.string.status_recording_front else R.string.status_recording_back,
                    i + 1, count
                )
            }

            when (mode) {
                CaptureMode.PHOTO -> captureOnePhoto(cameraProvider, selector, i)
                CaptureMode.VIDEO -> captureOneVideo(cameraProvider, selector, i, settings.videoClipSeconds)
            }

            delay(300)
        }

        cameraProvider.unbindAll()

        if (keepOnlyLatest && previousUris.isNotEmpty()) {
            statusText.text = getString(R.string.status_cleaning_up)
            val result = MediaVault.deleteUris(this, previousUris)
            // Captures from an earlier install are not ours to delete quietly;
            // the system asks the user instead, and without this they would pile
            // up forever while the app claimed to keep only the latest.
            result.consent?.let { consent ->
                statusText.text = getString(R.string.status_cleanup_confirm)
                awaitDeleteConsent(consent)
            }
        }

        showStatusThenFinish(getString(R.string.status_done))
    }

    private suspend fun awaitDeleteConsent(consent: IntentSender): Unit =
        suspendCancellableCoroutine { cont ->
            deleteConsent = cont
            awaitingSystemDialog = true
            runCatching {
                requestDeleteConsent.launch(IntentSenderRequest.Builder(consent).build())
            }.onFailure {
                awaitingSystemDialog = false
                deleteConsent = null
                if (cont.isActive) cont.resume(Unit)
            }
        }

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                if (cont.isActive) cont.resume(future.get())
            }, ContextCompat.getMainExecutor(this))
        }

    private suspend fun captureOnePhoto(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        index: Int
    ): Uri? = suspendCancellableCoroutine { cont ->
        val imageCapture = ImageCapture.Builder().build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, imageCapture)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val name = "EarllnCam_${System.currentTimeMillis()}_$index.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${MediaVault.ALBUM}")
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(output.savedUri)
                }

                override fun onError(exc: ImageCaptureException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }

    private suspend fun captureOneVideo(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        index: Int,
        seconds: Int
    ): Uri? = suspendCancellableCoroutine { cont ->
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, videoCapture)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val name = "EarllnCam_${System.currentTimeMillis()}_$index.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/${MediaVault.ALBUM}")
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var resumed = false
        val recording = recorder.prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize && !resumed) {
                    resumed = true
                    val uri = if (!event.hasError()) event.outputResults.outputUri else null
                    if (cont.isActive) cont.resume(uri)
                }
            }

        mainHandler.postDelayed({ recording.stop() }, seconds * 1000L)
        cont.invokeOnCancellation { recording.close() }
    }

    private fun showStatusThenFinish(message: String) {
        statusText.text = message
        lifecycleScope.launch {
            delay(1200)
            finish()
        }
    }
}
