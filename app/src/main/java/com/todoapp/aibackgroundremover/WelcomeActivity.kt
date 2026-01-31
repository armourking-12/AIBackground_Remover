package com.todoapp.aibackgroundremover

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.todoapp.aibackgroundremover.databinding.ActivityWelcomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    // Config
    private val MODEL_FILENAME = "rmbg_model.onnx"
    private val MODEL_URL = "https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model_quantized.onnx?download=true"

    // 🟢 FUNNY MESSAGES
    private val funnyLines = listOf(
        "Convincing the pixels to cooperate...",
        "Installing pure intelligence...",
        "Teaching the AI what a cat looks like...",
        "Still faster than your ex replying...",
        "Polishing the magic wand...",
        "Removing bad vibes from the code...",
        "Powering up the neural network...",
        "Almost there! Hang tight..."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Check if model exists
        val modelFile = File(filesDir, MODEL_FILENAME)
        if (modelFile.exists()) {
            goToMain() // Skip screen if already downloaded
        }

        binding.btnStart.setOnClickListener {
            startDownload(modelFile)
        }
    }

    private fun startDownload(destinationFile: File) {
        binding.btnStart.isEnabled = false
        binding.btnStart.text = "Downloading..."
        binding.progressBar.visibility = View.VISIBLE

        // Keep screen awake
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        startFunnyTextLoop()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(MODEL_URL).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) throw Exception("Network Error")

                val body = response.body ?: throw Exception("Empty Data")
                val totalSize = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = ((totalRead * 100) / totalSize).toInt()
                    runOnUiThread { binding.progressBar.progress = progress }
                }

                outputStream.flush(); outputStream.close(); inputStream.close()

                runOnUiThread {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    binding.tvFunnyStatus.text = "Power Unlocked! 🚀"
                    binding.btnStart.text = "Enter AI Studio"
                    binding.btnStart.isEnabled = true
                    binding.btnStart.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
                    binding.btnStart.setOnClickListener { goToMain() }
                }

            } catch (e: Exception) {
                if (destinationFile.exists()) destinationFile.delete()
                runOnUiThread {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    binding.tvFunnyStatus.text = "Download failed. Check internet."
                    binding.btnStart.text = "Retry Unlock"
                    binding.btnStart.isEnabled = true
                    binding.btnStart.setOnClickListener { startDownload(destinationFile) }
                }
            }
        }
    }

    private fun startFunnyTextLoop() {
        val handler = Handler(Looper.getMainLooper())
        var index = 0
        val runnable = object : Runnable {
            override fun run() {
                if (binding.progressBar.progress < 100) {
                    binding.tvFunnyStatus.text = funnyLines[index % funnyLines.size]
                    index++
                    handler.postDelayed(this, 2500)
                }
            }
        }
        handler.post(runnable)
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}