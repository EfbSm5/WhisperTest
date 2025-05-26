package com.example.whispertest

import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.lang.Float
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Arrays
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.FloatArray
import kotlin.Int
import kotlin.OptIn
import kotlin.String
import kotlin.Throws
import kotlin.Unit
import kotlin.apply
import kotlin.isInitialized
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.run

object WhisperUtil {
    private const val TAG = "WhisperUtil"
    lateinit var interpreter: Interpreter

    private fun loadModel(modelPath: String) {
        val fileDescriptor = AppUtil.getContext().assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        val options = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors())
        }
        interpreter = Interpreter(retFile, options)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun translate(
        modelPath: String, input: String, vocabPath: String, callback: (String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            if (!::interpreter.isInitialized) {
                loadModel(modelPath)
                Log.d(TAG, "translate: init model")
            }
            loadFiltersAndVocab(false, vocabPath)
            Log.d(TAG, "translate: load vocab")
            val inputarray = convertToPcmFloat(input)
            Log.d(TAG, "translate: convertInput")
            val temp = run(inputarray)
            callback(temp)
        }

    }


    private fun convertToPcmFloat(audioPath: String): FloatArray {
        val assetManager = AppUtil.getContext().assets
        val fileInputStream = assetManager.open(audioPath)
        val header = ByteArray(44)
        fileInputStream.read(header)

        // Check if it's a valid WAV file (contains "RIFF" and "WAVE" markers)
        val headerStr = String(header, 0, 4)
        if (headerStr != "RIFF") {
            Log.d(TAG, "convertToPcmFloat:\"Not a valid WAV file\" ")
            return FloatArray(0)
        }

        // Get the audio format details from the header
        val sampleRate: Int = byteArrayToNumber(header, 24, 4)
        val bitsPerSample: Int = byteArrayToNumber(header, 34, 2)
        if (bitsPerSample != 16 && bitsPerSample != 32) {
            Log.d(TAG, "convertToPcmFloat: Unsupported bits per sample: $bitsPerSample")
            return FloatArray(0)
        }

        // Get the size of the data section (all PCM data)
        val dataLength = fileInputStream.available() // byteArrayToInt(header, 40, 4);

        // Calculate the number of samples
        val bytesPerSample = bitsPerSample / 8
        val numSamples = dataLength / bytesPerSample

        // Read the audio data
        val audioData = ByteArray(dataLength)
        fileInputStream.read(audioData)
        val byteBuffer = ByteBuffer.wrap(audioData)
        byteBuffer.order(ByteOrder.nativeOrder())


        // Convert audio data to PCM_FLOAT format
        val samples = FloatArray(numSamples)
        if (bitsPerSample == 16) {
            for (i in 0..<numSamples) {
                samples[i] = (byteBuffer.getShort() / 32768.0).toFloat()
            }
        } else if (bitsPerSample == 32) {
            for (i in 0..<numSamples) {
                samples[i] = byteBuffer.getFloat()
            }
        }

        val fixedInputSize: Int = 16000 * 30
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength = min(samples.size.toDouble(), fixedInputSize.toDouble()).toInt()
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)

        val cores = Runtime.getRuntime().availableProcessors()
        return getMelSpectrogram(inputSamples, inputSamples.size, cores)
    }

    private fun byteArrayToNumber(bytes: ByteArray, offset: Int, length: Int): Int {
        var value = 0 // Start with an initial value of 0

        // Loop through the specified portion of the byte array
        for (i in 0..<length) {
            // Extract a byte, ensure it's positive, and shift it to its position in the integer
            value = value or ((bytes[offset + i].toInt() and 0xFF) shl (8 * i))
        }

        return value // Return the resulting integer value
    }

    private fun run(inputData: FloatArray): String {
        val inputTensor: Tensor = interpreter.getInputTensor(0)
        val inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType())
        val outputTensor: Tensor = interpreter.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32)
        val inputSize =
            inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES
        val inputBuf = ByteBuffer.allocateDirect(inputSize)
        inputBuf.order(ByteOrder.nativeOrder())
        for (input in inputData) {
            inputBuf.putFloat(input)
        }
        inputBuffer.loadBuffer(inputBuf)
        interpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer())
        val outputLen = outputBuffer.intArray.size
        val result = StringBuilder()
        for (i in 0..<outputLen) {
            val token = outputBuffer.getBuffer().getInt()
            if (token == WhisperVocab.tokenEOT) break

            // Get word for token and Skip additional token
            if (token < WhisperVocab.tokenEOT) {
                val word: String? = WhisperVocab.tokenToWord[token]
                result.append(word)
            } else {
                if (token == WhisperVocab.tokenTRANSCRIBE) Log.d(TAG, "It is Transcription...")
                if (token == WhisperVocab.tokenTRANSLATE) Log.d(TAG, "It is Translation...")
            }
        }

        return result.toString()
    }

    @Throws(IOException::class)
    private fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String?): Boolean {
        val assetManager = AppUtil.getContext().assets
        val inputStream = assetManager.open("filters_vocab_multilingual.bin")
        val bytes = inputStream.readBytes()
        inputStream.close()
        val vocabBuf = ByteBuffer.wrap(bytes)
        vocabBuf.order(ByteOrder.nativeOrder())
        val magic = vocabBuf.getInt()
        if (magic == 0x5553454e) {
            Log.d(TAG, "Magic number: " + magic)
        } else {
            Log.d(TAG, "Invalid vocab file (bad magic: " + magic + "), " + vocabPath)
            return false
        }
        WhisperFilter.nMel = vocabBuf.getInt()
        WhisperFilter.nFft = vocabBuf.getInt()
        val filterData = ByteArray(WhisperFilter.nMel * WhisperFilter.nFft * Float.BYTES)
        vocabBuf.get(filterData, 0, filterData.size)
        val filterBuf = ByteBuffer.wrap(filterData)
        filterBuf.order(ByteOrder.nativeOrder())

        WhisperFilter.data = FloatArray(WhisperFilter.nMel * WhisperFilter.nFft)
        run {
            var i = 0
            while (filterBuf.hasRemaining()) {
                WhisperFilter.data[i] = filterBuf.getFloat()
                i++
            }
        }

        // Load vocabulary
        val nVocab = vocabBuf.getInt()
        Log.d(TAG, "nVocab: " + nVocab)
        for (i in 0..<nVocab) {
            val len = vocabBuf.getInt()
            val wordBytes = ByteArray(len)
            vocabBuf.get(wordBytes, 0, wordBytes.size)
            val word = String(wordBytes)
            WhisperVocab.tokenToWord.put(i, word)
        }

        // Add additional vocab ids
        val nVocabAdditional: Int
        if (!multilingual) {
            nVocabAdditional = WhisperVocab.nVocabEnglish
        } else {
            nVocabAdditional = WhisperVocab.nVocabMultilingual
            WhisperVocab.apply {
                tokenEOT++
                tokenSOT++
                tokenPREV++
                tokenSOLM++
                tokenNOT++
                tokenBEG++
            }
        }

        for (i in nVocab..<nVocabAdditional) {
            val word = if (i > WhisperVocab.tokenBEG) {
                "[_TT_" + (i - WhisperVocab.tokenBEG) + "]"
            } else if (i == WhisperVocab.tokenEOT) {
                "[_EOT_]"
            } else if (i == WhisperVocab.tokenSOT) {
                "[_SOT_]"
            } else if (i == WhisperVocab.tokenPREV) {
                "[_PREV_]"
            } else if (i == WhisperVocab.tokenNOT) {
                "[_NOT_]"
            } else if (i == WhisperVocab.tokenBEG) {
                "[_BEG_]"
            } else {
                "[_extra_token_" + i + "]"
            }

            WhisperVocab.tokenToWord.put(i, word)
        }

        return true
    }

    fun getMelSpectrogram(samples: FloatArray, nSamples: Int, nThreads: Int): FloatArray {
        val fftSize = 400
        val fftStep = 160
        WhisperMel.nMel = 80
        WhisperMel.nLen = nSamples / fftStep
        WhisperMel.data = FloatArray(WhisperMel.nMel * WhisperMel.nLen)

        val hann = FloatArray(fftSize)
        for (i in 0..<fftSize) {
            hann[i] = (0.5 * (1.0 - cos(2.0 * Math.PI * i / fftSize))).toFloat()
        }

        val nFft = 1 + fftSize / 2

        /**//////////// UNCOMMENT below block to use multithreaded mel calculation ///////////////////////// */
        // Calculate mel values using multiple threads
        val workers: MutableList<Thread> = ArrayList<Thread>()
        for (iw in 0..<nThreads) {
            val ith = iw // Capture iw in a final variable for use in the lambda
            val thread = Thread(Runnable {
                // Inside the thread, ith will have the same value as iw (first value is 0)
                Log.d(TAG, "Thread " + ith + " started.")

                val fftIn = FloatArray(fftSize)
                Arrays.fill(fftIn, 0.0f)
                val fftOut = FloatArray(fftSize * 2)

                var i = ith
                while (i < WhisperMel.nLen) {/**//////////// END of Block /////////////////////////////////////////////////////////////////////// */ /**//////////// COMMENT below block to use multithreaded mel calculation /////////////////////////// */
//        float[] fftIn = new float[fftSize];
//        Arrays.fill(fftIn, 0.0f);
//        float[] fftOut = new float[fftSize * 2];
//
//        for (int i = 0; i < mel.nLen; i++) {
                    /**//////////// END of Block /////////////////////////////////////////////////////////////////////// */
                    val offset = i * fftStep

                    // apply Hanning window
                    for (j in 0..<fftSize) {
                        if (offset + j < nSamples) {
                            fftIn[j] = hann[j] * samples[offset + j]
                        } else {
                            fftIn[j] = 0.0f
                        }
                    }

                    // FFT -> mag^2
                    fft(fftIn, fftOut)
                    for (j in 0..<fftSize) {
                        fftOut[j] =
                            fftOut[2 * j] * fftOut[2 * j] + fftOut[2 * j + 1] * fftOut[2 * j + 1]
                    }

                    for (j in 1..<fftSize / 2) {
                        fftOut[j] += fftOut[fftSize - j]
                    }

                    // mel spectrogram
                    for (j in 0..<WhisperMel.nMel) {
                        var sum = 0.0
                        for (k in 0..<nFft) {
                            sum += (fftOut[k] * WhisperFilter.data[j * nFft + k]).toDouble()
                        }

                        if (sum < 1e-10) {
                            sum = 1e-10
                        }

                        sum = log10(sum)
                        WhisperMel.data[j * WhisperMel.nLen + i] = sum.toFloat()
                    }
                    i += nThreads
                }
            })
            workers.add(thread)
            thread.start()
        }

        // Wait for all threads to finish
        for (worker in workers) {
            try {
                worker.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        /**//////////// END of Block /////////////////////////////////////////////////////////////////////// */

        // clamping and normalization
        var mmax = -1e20
        for (i in 0..<WhisperMel.nMel * WhisperMel.nLen) {
            if (WhisperMel.data[i] > mmax) {
                mmax = WhisperMel.data[i].toDouble()
            }
        }

        mmax -= 8.0
        for (i in 0..<WhisperMel.nMel * WhisperMel.nLen) {
            if (WhisperMel.data[i] < mmax) {
                WhisperMel.data[i] = mmax.toFloat()
            }
            WhisperMel.data[i] = ((WhisperMel.data[i] + 4.0) / 4.0).toFloat()
        }

        return WhisperMel.data
    }
    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }

        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(inSize / 2)
        val odd = FloatArray(inSize / 2)

        var indxEven = 0
        var indxOdd = 0
        for (i in 0..<inSize) {
            if (i % 2 == 0) {
                even[indxEven] = input[i]
                indxEven++
            } else {
                odd[indxOdd] = input[i]
                indxOdd++
            }
        }

        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)

        fft(even, evenFft)
        fft(odd, oddFft)
        for (k in 0..<inSize / 2) {
            val theta = (2 * Math.PI * k / inSize).toFloat()
            val re = cos(theta.toDouble()).toFloat()
            val im = -sin(theta.toDouble()).toFloat()
            val reOdd = oddFft[2 * k + 0]
            val imOdd = oddFft[2 * k + 1]
            output[2 * k + 0] = evenFft[2 * k + 0] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            output[2 * (k + inSize / 2) + 0] = evenFft[2 * k + 0] - re * reOdd + im * imOdd
            output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }
    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0..<inSize) {
            var re = 0.0f
            var im = 0.0f
            for (n in 0..<inSize) {
                val angle = (2 * Math.PI * k * n / inSize).toFloat()
                re += (input[n] * cos(angle.toDouble())).toFloat()
                im -= (input[n] * sin(angle.toDouble())).toFloat()
            }
            output[k * 2 + 0] = re
            output[k * 2 + 1] = im
        }
    }
}

private object WhisperFilter {
    var nMel: Int = 0
    var nFft: Int = 0
    lateinit var data: FloatArray
}

private object WhisperMel {
    var nLen: Int = 0
    var nMel: Int = 0
    lateinit var data: FloatArray
}