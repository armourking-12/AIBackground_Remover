package com.todoapp.aibackgroundremover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.FloatBuffer
import java.util.Collections

class RMBGRemover(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Check if engine is currently running
    val isLoaded: Boolean
        get() = ortSession != null

    // 🟢 START THE ENGINE
    fun initialize() {
        if (ortSession != null) return // Already loaded

        try {
            if (ortEnv == null) ortEnv = OrtEnvironment.getEnvironment()

            val options = OrtSession.SessionOptions()
            try { options.addNnapi() } catch (e: Exception) { }

            val modelFile = File(context.filesDir, "rmbg_model.onnx")
            if (modelFile.exists()) {
                val modelBytes = FileInputStream(modelFile).readBytes()
                ortSession = ortEnv?.createSession(modelBytes, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🔴 SHUTDOWN THE ENGINE (Save Battery)
    fun release() {
        try {
            ortSession?.close()
            ortSession = null
            // We keep ortEnv because it's lightweight
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeBackground(originalBitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        // Auto-start if not ready
        if (ortSession == null) initialize()
        if (ortSession == null) return@withContext null

        try {
            // ... (Your existing 1024x1024 Logic) ...
            val modelSize = 1024
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, modelSize, modelSize, true)

            val imgData = FloatBuffer.allocate(1 * 3 * modelSize * modelSize)
            imgData.rewind()
            val pixels = IntArray(modelSize * modelSize)
            scaledBitmap.getPixels(pixels, 0, modelSize, 0, 0, modelSize, modelSize)

            for (i in 0 until modelSize * modelSize) {
                val pixel = pixels[i]
                val r = ((pixel shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f
                val g = ((pixel shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f
                val b = ((pixel and 0xFF) / 255.0f - 0.5f) / 0.5f
                imgData.put(i, r); imgData.put(modelSize * modelSize + i, g); imgData.put(2 * modelSize * modelSize + i, b)
            }
            imgData.rewind()

            val inputName = ortSession?.inputNames?.iterator()?.next() ?: "input"
            val shape = longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong())
            val tensor = OnnxTensor.createTensor(ortEnv, imgData, shape)

            val result = ortSession?.run(Collections.singletonMap(inputName, tensor))
            val outputTensor = result?.get(0) as OnnxTensor
            val floatBuffer = outputTensor.floatBuffer

            val maskPixels = IntArray(modelSize * modelSize)
            for (i in 0 until modelSize * modelSize) {
                val alpha = (floatBuffer.get(i) * 255).toInt().coerceIn(0, 255)
                maskPixels[i] = Color.argb(alpha, 0, 0, 0)
            }

            val tempMask = Bitmap.createBitmap(modelSize, modelSize, Bitmap.Config.ARGB_8888)
            tempMask.setPixels(maskPixels, 0, modelSize, 0, 0, modelSize, modelSize)
            val finalMask = Bitmap.createScaledBitmap(tempMask, originalBitmap.width, originalBitmap.height, true)

            val finalBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            val paint = Paint()
            canvas.drawBitmap(originalBitmap, 0f, 0f, null)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(finalMask, 0f, 0f, paint)

            return@withContext finalBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}