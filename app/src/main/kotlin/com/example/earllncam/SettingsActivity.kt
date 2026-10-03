package com.example.earllncam

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var shotCountPicker: NumberPicker
    private lateinit var modeGroup: RadioGroup
    private lateinit var videoSecondsLabel: View
    private lateinit var videoSecondsPicker: NumberPicker
    private lateinit var recordAudioSwitch: SwitchMaterial
    private lateinit var keepOnlyLatestSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        settings = AppSettings(this)

        shotCountPicker = findViewById(R.id.shotCountPicker)
        modeGroup = findViewById(R.id.modeGroup)
        videoSecondsLabel = findViewById(R.id.videoSecondsLabel)
        videoSecondsPicker = findViewById(R.id.videoSecondsPicker)
        recordAudioSwitch = findViewById(R.id.recordAudioSwitch)
        keepOnlyLatestSwitch = findViewById(R.id.keepOnlyLatestSwitch)

        shotCountPicker.minValue = AppSettings.MIN_SHOT_COUNT
        shotCountPicker.maxValue = AppSettings.MAX_SHOT_COUNT
        shotCountPicker.value = settings.shotCount

        videoSecondsPicker.minValue = AppSettings.MIN_VIDEO_SECONDS
        videoSecondsPicker.maxValue = AppSettings.MAX_VIDEO_SECONDS
        videoSecondsPicker.value = settings.videoClipSeconds

        val radioPhoto = findViewById<View>(R.id.radioPhoto)
        val radioVideo = findViewById<View>(R.id.radioVideo)
        when (settings.mode) {
            CaptureMode.PHOTO -> radioPhoto.id.let { modeGroup.check(it) }
            CaptureMode.VIDEO -> radioVideo.id.let { modeGroup.check(it) }
        }
        updateVideoFieldsVisibility(settings.mode)

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radioVideo) CaptureMode.VIDEO else CaptureMode.PHOTO
            updateVideoFieldsVisibility(mode)
        }

        recordAudioSwitch.isChecked = settings.recordAudio
        keepOnlyLatestSwitch.isChecked = settings.keepOnlyLatest

        findViewById<TextView>(R.id.versionText).text = AppVersion.label(this)

        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
    }

    private fun updateVideoFieldsVisibility(mode: CaptureMode) {
        val visibility = if (mode == CaptureMode.VIDEO) View.VISIBLE else View.GONE
        videoSecondsLabel.visibility = visibility
        videoSecondsPicker.visibility = visibility
        recordAudioSwitch.visibility = visibility
    }

    private fun save() {
        shotCountPicker.clearFocus()
        videoSecondsPicker.clearFocus()

        settings.shotCount = shotCountPicker.value
        settings.mode = if (modeGroup.checkedRadioButtonId == R.id.radioVideo) {
            CaptureMode.VIDEO
        } else {
            CaptureMode.PHOTO
        }
        settings.videoClipSeconds = videoSecondsPicker.value
        settings.recordAudio = recordAudioSwitch.isChecked
        settings.keepOnlyLatest = keepOnlyLatestSwitch.isChecked

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
