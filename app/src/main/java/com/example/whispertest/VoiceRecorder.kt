package com.example.whispertest

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

class VoiceRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private var file = ""
    fun record() {
        if (isRecording) {
            startRecord()
        } else {
            stopRecord()
        }
    }

    fun play() {
        if (isPlaying) {
            startPlay()
        } else {
            stopPlay()
        }
    }

    fun stop() {
        stopRecord()
        stopPlay()
    }

    private fun startRecord() {
        if (mediaRecorder == null) {
            mediaRecorder = MediaRecorder().apply {
                16000
                setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                setOutputFile(file)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                try {
                    prepare()
                    start()
                } catch (e: Exception) {
                    throw e
                }
            }
        } else {
            mediaRecorder.apply {
                try {
                    this!!.prepare()
                    start()
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }

    private fun stopRecord() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
    }

    private fun startPlay() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file)
                try {
                    prepare()
                    start()
                } catch (e: Exception) {
                    throw e
                }
            }
        } else {
            mediaPlayer!!.apply {
                try {
                    prepare()
                    start()
                } catch (e: Exception) {
                    throw e
                }
            }

        }
    }

    private fun stopPlay() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }
}