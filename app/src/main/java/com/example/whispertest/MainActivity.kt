package com.example.whispertest

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
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
    val recordBtn = Button(AppUtil.getContext())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUi()
    }

    override fun onStop() {
        super.onStop()
        audioUtil.stop()
    }

    fun initUi() {
        recordBtn.text = getString(R.string.record)
        recordBtn.id = View.generateViewId()
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
        recordBtn.setOnClickListener {
            Toast.makeText(this, "record", Toast.LENGTH_SHORT).show()
            AppUtil.checkPermission(this)
            try {
                audioUtil.record()
            } catch (e: Exception) {
                throw e
            }
        }
        val items = listOf(DEFAULT_WAV, "inputVoice")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewBinding.spinner.adapter = adapter
        viewBinding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (items[position] == "inputVoice") {
                    changeView(true)
                } else {
                    changeView(false)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

        }

    }

    fun changeView(showRecord: Boolean) {
        val layout = viewBinding.main
        val layoutSet = ConstraintSet()
        layoutSet.clone(layout)
        val playBtn = viewBinding.play
        val tranBtn = viewBinding.translate
        if (showRecord) {
            layout.addView(recordBtn)
            layoutSet.clear(playBtn.id, ConstraintSet.END)
            layoutSet.clear(tranBtn.id, ConstraintSet.START)
            layoutSet.connect(playBtn.id, ConstraintSet.END, recordBtn.id, ConstraintSet.START)
            layoutSet.connect(recordBtn.id, ConstraintSet.START, playBtn.id, ConstraintSet.END)
            layoutSet.connect(recordBtn.id, ConstraintSet.END, tranBtn.id, ConstraintSet.START)
            layoutSet.connect(tranBtn.id, ConstraintSet.START, recordBtn.id, ConstraintSet.END)
        } else {
            layoutSet.connect(playBtn.id, ConstraintSet.END, tranBtn.id, ConstraintSet.START)
            layoutSet.connect(tranBtn.id, ConstraintSet.START, recordBtn.id, ConstraintSet.END)
            layoutSet.clear(recordBtn.id, ConstraintSet.START)
            layoutSet.clear(recordBtn.id, ConstraintSet.END)
            layout.removeView(recordBtn)

        }
        layoutSet.applyTo(layout)
    }

}


