package com.android.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
            Log.d(TAG, "Scores: notrelated=${scores[0]}, shortvideo=${scores[1]}")

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
。     * Preprocess: PadWhite → Resize → Normalize
     * 1. Calculate scale to fit image into 295x603 while maintaining aspect ratio
     * 2. Place scaled image centered on a white 295x603 canvas
     * 3. Normalize pixels with ImageNet mean/std
     */
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        // Create white canvas at model input size
        val paddedBitmap = Bitmap.createBitmap(INPUT_WIDTH, INPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(Color.WHITE)

        // Calculate scaling to fit within INPUT_WIDTH x INPUT_HEIGHT
        val scaleW = INPUT_WIDTH.toFloat() / bitmap.width
        val scaleH = INPUT_HEIGHT.toFloat() / bitmap.height
        val scale = minOf(scaleW, scaleH)

        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()

        // Center the image on the canvas
        val left = (INPUT_WIDTH - scaledWidth) / 2
        val top = (INPUT_HEIGHT - scaledHeight) / 2

        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = Rect(left, top, left + scaledWidth, top + scaledHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        // Convert to normalized float buffer (NHWC)
        val bufferSize = 1 * INPUT_HEIGHT * INPUT_WIDTH * INPUT_CHANNELS * PIXEL_SIZE
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        paddedBitmap.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
        paddedBitmap.recycle()

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
