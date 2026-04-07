package com.android.webview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelInferenceHelper(private val context: Context) {

    companion object {
        private const val TAG = "ModelInference"
        private const val MODEL_FILE = "bili_binary_model_v2.tflite"

        // Model input dimensions (NHWC): 1 x 603 x 295 x 3
        private const val INPUT_HEIGHT = 603
        private const val INPUT_WIDTH = 295
        private const val INPUT_CHANNELS = 3
        private const val PIXEL_SIZE = 4 // float32

        // Custom normalization from model_protocol.json
        private val MEAN = floatArrayOf(0.5679f, 0.5574f, 0.5495f)
        private val STD = floatArrayOf(0.399f, 0.3966f, 0.4008f)

        // Output classes (binary: notrelated / shortvideo)
        private const val NUM_CLASSES = 2
    }

    enum class ClassificationResult {
        NOT_RELATED,    // 0
        SHORT_VIDEO     // 1
    }

    private var interpreter: Interpreter? = null

    fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    /**
     * Classify a screenshot bitmap.
     * @return classification result, or NOT_RELATED on error
     */
    fun classify(bitmap: Bitmap): ClassificationResult {
        val interp = interpreter
        if (interp == null) {
            Log.w(TAG, "Interpreter not initialized")
            return ClassificationResult.NOT_RELATED
        }

        return try {
            val inputBuffer = preprocessBitmap(bitmap)
            val outputArray = Array(1) { FloatArray(NUM_CLASSES) }

            // Use runForMultipleInputsOutputs to avoid Kotlin's run() name shadow
            // Model output shape is [1, 2]
            val outputMap = HashMap<Int, Any>()
            outputMap[0] = outputArray
            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            val scores = outputArray[0]
            val total = scores[0] + scores[1]
            val notRelatedPct = if (total > 0) scores[0] / total * 100 else 0f
            val shortVideoPct = if (total > 0) scores[1] / total * 100 else 0f
            Log.i(TAG, "Confidence: notrelated=%.2f%% (%.4f), shortvideo=%.2f%% (%.4f)".format(
                notRelatedPct, scores[0], shortVideoPct, scores[1]))

            val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
            when (maxIndex) {
                0 -> ClassificationResult.NOT_RELATED
                1 -> ClassificationResult.SHORT_VIDEO
                else -> ClassificationResult.NOT_RELATED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            ClassificationResult.NOT_RELATED
        }
    }

    /**
     * Preprocess: Stretch → Normalize
     * 1. Directly stretch (resize) the image to 295x603 ignoring aspect ratio
     * 2. Normalize pixels with custom mean/std
     */
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        // Directly stretch the image to model input size (ignoring aspect ratio)
        val stretchedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)

        // Convert to normalized float buffer (NHWC)
        val bufferSize = 1 * INPUT_HEIGHT * INPUT_WIDTH * INPUT_CHANNELS * PIXEL_SIZE
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        stretchedBitmap.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
        if (stretchedBitmap != bitmap) {
            stretchedBitmap.recycle()
        }

        for (y in 0 until INPUT_HEIGHT) {
            for (x in 0 until INPUT_WIDTH) {
                val pixel = pixels[y * INPUT_WIDTH + x]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                inputBuffer.putFloat((r - MEAN[0]) / STD[0])
                inputBuffer.putFloat((g - MEAN[1]) / STD[1])
                inputBuffer.putFloat((b - MEAN[2]) / STD[2])
            }
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
