package com.todoapp.aibackgroundremover

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater // 🟢 Fixed Import
import android.view.View
import android.view.ViewGroup      // 🟢 Fixed Import
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.todoapp.aibackgroundremover.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// History Data Model
data class HistoryItem(val filePath: String, val timestamp: Long)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pickImage: ActivityResultLauncher<String>
    private var currentImageUri: Uri? = null
    private var resultBitmap: Bitmap? = null

    // 🟢 Engine & Power Management
    private var rmbgRemover: RMBGRemover? = null
    private var shutdownJob: Job? = null // Timer to kill engine

    // History Variables
    private val historyList = mutableListOf<HistoryItem>()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize wrapper (Engine stays OFF until needed)
        try {
            rmbgRemover = RMBGRemover(this)
        } catch (e: Exception) {
            // Critical Safety: If model is missing, go back to Welcome
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setupHistoryRecyclerView()
        loadHistory()

        // 1. Menu Click
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // 2. Batch Mode Click
        binding.root.findViewById<View>(R.id.btnOpenBatch).setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            val intent = Intent(this, BatchActivity::class.java)
            startActivity(intent)
        }

        // 3. Image Picker (Smart Wake Up)
        pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                currentImageUri = it
                binding.placeholderLayout.visibility = View.GONE
                binding.imageView.setImageURI(it)
                binding.btnDownload.visibility = View.GONE
                binding.horizontalProgress.visibility = View.INVISIBLE
                binding.horizontalProgress.progress = 0

                // Start processing immediately
                startBackgroundRemoval(it)
            }
            // If user cancelled, go back to sleep
            if (uri == null) scheduleEngineShutdown()
        }

        // 4. Action Button
        binding.btnAction.setOnClickListener {
            // 🟢 WAKE UP ENGINE IMMEDIATELY (While user clicks)
            wakeUpEngine()

            if (binding.btnAction.text == "Try Again" && currentImageUri != null) {
                startBackgroundRemoval(currentImageUri!!)
            } else {
                pickImage.launch("image/*")
            }
        }

        // 5. Download Button
        binding.btnDownload.setOnClickListener {
            resultBitmap?.let { bitmap -> saveImageToGallery(bitmap) }
        }
    }

    // ==========================================================
    // 🧠 SMART POWER & RAM MANAGEMENT
    // ==========================================================

    private fun wakeUpEngine() {
        shutdownJob?.cancel() // Stop the "sleep" timer
        shutdownJob = null

        // Start loading silently in background
        lifecycleScope.launch(Dispatchers.IO) {
            rmbgRemover?.initialize()
        }
    }

    private fun scheduleEngineShutdown() {
        shutdownJob?.cancel()
        // Wait 30 seconds, then kill engine to save battery
        shutdownJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(30000)
            rmbgRemover?.release()
        }
    }

    // ==========================================================
    // ⚡ HIGH PERFORMANCE PROCESSING
    // ==========================================================
    private fun startBackgroundRemoval(uri: Uri) {
        shutdownJob?.cancel() // Keep engine awake while working

        binding.btnAction.text = "Processing on GPU..."
        binding.btnAction.isEnabled = false
        binding.btnDownload.visibility = View.GONE
        binding.horizontalProgress.visibility = View.VISIBLE
        binding.horizontalProgress.progress = 0
        binding.loadingAnimation.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 🟢 OPTIMIZATION 1: Smart Loading (Prevents Crashes on Large Photos)
                // Step A: Read Dimensions Only
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                var input = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(input, null, options)
                input?.close()

                // Step B: Calculate Scale (Don't load 50MP image into RAM)
                val targetSize = 1024
                var scale = 1
                while (options.outWidth / scale / 2 >= targetSize &&
                    options.outHeight / scale / 2 >= targetSize) {
                    scale *= 2
                }

                // Step C: Load Resized Image
                val loadOptions = BitmapFactory.Options()
                loadOptions.inSampleSize = scale
                input = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(input, null, loadOptions)
                input?.close()

                if (originalBitmap == null) {
                    runOnUiThread { showError("Failed to read image") }
                    return@launch
                }

                runOnUiThread { setProgressAnimate(20) }

                // ⚡ RUN AI ENGINE (NPU/GPU)
                // If engine isn't ready yet, removeBackground will auto-init it.
                val processedBitmap = rmbgRemover?.removeBackground(originalBitmap)

                runOnUiThread { setProgressAnimate(100) }

                if (processedBitmap != null) {
                    runOnUiThread { showResult(processedBitmap) }
                } else {
                    runOnUiThread { showError("GPU Processing Failed") }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { showError("Error: ${e.message}") }
            } finally {
                // 🟢 JOB DONE: Start countdown to sleep
                scheduleEngineShutdown()
            }
        }
    }

    // ==========================================================
    // 💾 NON-BLOCKING SAVE (Optimization 2)
    // ==========================================================
    private fun saveImageToGallery(bitmap: Bitmap) {
        binding.btnDownload.isEnabled = false
        binding.btnDownload.text = "Saving..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filename = "AI_BG_Removed_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Saved to Gallery! ✨", Toast.LENGTH_SHORT).show()
                    binding.btnDownload.text = "Save to Gallery"
                    binding.btnDownload.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save", Toast.LENGTH_SHORT).show()
                    binding.btnDownload.text = "Save to Gallery"
                    binding.btnDownload.isEnabled = true
                }
            }
        }
    }

    // ==========================================================
    // UI & HELPERS
    // ==========================================================
    private fun setProgressAnimate(value: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            binding.horizontalProgress.setProgress(value, true)
        } else {
            binding.horizontalProgress.progress = value
        }
    }

    private fun showResult(bitmap: Bitmap) {
        binding.horizontalProgress.visibility = View.INVISIBLE
        resultBitmap = bitmap
        binding.loadingAnimation.visibility = View.GONE
        binding.imageView.setImageBitmap(bitmap)
        binding.btnAction.text = "Select New Image"
        binding.btnAction.isEnabled = true
        binding.btnDownload.visibility = View.VISIBLE
        currentImageUri = null
        addToHistory(bitmap)
    }

    private fun showError(message: String) {
        binding.horizontalProgress.visibility = View.INVISIBLE
        binding.loadingAnimation.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.btnAction.text = "Try Again"
        binding.btnAction.isEnabled = true
    }

    // ==========================================================
    // HISTORY SYSTEM
    // ==========================================================
    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(historyList) { item, position ->
            try {
                val file = File(item.filePath)
                if (file.exists()) file.delete()
                historyList.removeAt(position)
                historyAdapter.notifyItemRemoved(position)
                saveHistoryList()
                updateHistoryCount()
            } catch (e: Exception) { e.printStackTrace() }
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun addToHistory(bitmap: Bitmap) {
        try {
            val filename = "hist_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, filename)
            FileOutputStream(file).use { stream ->
                // Thumbnail can be lower quality (80%) to save space
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            }
            val newItem = HistoryItem(file.absolutePath, System.currentTimeMillis())
            historyList.add(0, newItem)
            historyAdapter.notifyItemInserted(0)
            binding.rvHistory.scrollToPosition(0)
            saveHistoryList()
            updateHistoryCount()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveHistoryList() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(historyList)
        prefs.edit().putString("history_data", json).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("history_data", null)
        if (json != null) {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            val savedList: List<HistoryItem> = Gson().fromJson(json, type)
            historyList.clear()
            historyList.addAll(savedList)
            historyAdapter.notifyDataSetChanged()
        }
        updateHistoryCount()
    }

    private fun updateHistoryCount() {
        binding.tvHistoryCount.text = "${historyList.size} images processed"
    }

    // ==========================================================
    // ADAPTER CLASS
    // ==========================================================
    inner class HistoryAdapter(
        private val items: List<HistoryItem>,
        private val onDelete: (HistoryItem, Int) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.ivHistoryThumb)
            val date: TextView = view.findViewById(R.id.tvDate)
            val delete: View = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            holder.date.text = sdf.format(Date(item.timestamp))

            Glide.with(holder.itemView.context).load(item.filePath).into(holder.img)
            holder.delete.setOnClickListener { onDelete(item, position) }
        }

        override fun getItemCount() = items.size
    }
}