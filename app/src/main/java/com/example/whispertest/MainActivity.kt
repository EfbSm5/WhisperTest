package com.example.whispertest

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.whispertest.databinding.ActivityMainBinding
import com.example.whispertest.whisperEngine.WhisperUtil


class MainActivity : AppCompatActivity() {
    private val MULTILINGUAL_VOCAB_FILE: String = "filters_vocab_multilingual.bin"
    private val DEFAULT_MODEL_TO_USE: String = "whisper-tiny.tflite"
    private val DEFAULT_WAV: String = "english_test2.wav"
    private lateinit var viewBinding: ActivityMainBinding
    private val audioUtil = VoiceRecorder()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewBinding.translate.setOnClickListener {
            Toast.makeText(this, "click", Toast.LENGTH_SHORT).show()
            try {
                WhisperUtil.translate(
                    modelPath = DEFAULT_MODEL_TO_USE,
                    input = DEFAULT_WAV,
                    vocabPath = MULTILINGUAL_VOCAB_FILE,
                    callback = {
                        this.runOnUiThread {
                            viewBinding.translateText.text = it
                        }
                    })
            } catch (e: Exception) {
                throw e
            }
        }
        viewBinding.play.setOnClickListener {
            Toast.makeText(this, "play", Toast.LENGTH_SHORT).show()
            try {
                audioUtil.play()
            } catch (e: Exception) {
                throw e
            }
        }
        viewBinding.record.setOnClickListener {
            Toast.makeText(this, "record", Toast.LENGTH_SHORT).show()
            AppUtil.checkPermission(this)
            try {
                audioUtil.record()
            } catch (e: Exception) {
                throw e
            }
        }
    }

    override fun onStop() {
        super.onStop()
        audioUtil.stop()
    }

}


