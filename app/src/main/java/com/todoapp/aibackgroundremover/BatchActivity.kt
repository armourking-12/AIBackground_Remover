package com.todoapp.aibackgroundremover

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.io.OutputStream

data class BatchItem(
    val uri: Uri,
    var status: String = "Waiting", // Waiting, Processing, Done, Error
    var progress: Int = 0,
    var resultBitmap: Bitmap? = null
)

class BatchActivity : AppCompatActivity() {

    private lateinit var rvBatch: RecyclerView
    private lateinit var btnSelect: View
    private lateinit var btnSaveAll: View
    private lateinit var tvTotal: TextView

    private val batchList = mutableListOf<BatchItem>()
    private lateinit var adapter: BatchAdapter
    private lateinit var rmbgRemover: RMBGRemover

    // Multi-Image Picker
    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            // Limit to 10
            val selectedUris = uris.take(10)

            batchList.clear()
            selectedUris.forEach { uri ->
                batchList.add(BatchItem(uri))
            }
            adapter.notifyDataSetChanged()
            updateTotalProgress(0, batchList.size)

            // Start Processing Queue
            startBatchProcessing()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch)

        // Init GPU Engine
        rmbgRemover = RMBGRemover(this)

        rvBatch = findViewById(R.id.rvBatch)
        btnSelect = findViewById(R.id.btnSelectBatch)
        btnSaveAll = findViewById(R.id.btnSaveAll)
        tvTotal = findViewById(R.id.tvTotalProgress)

        adapter = BatchAdapter(batchList)
        rvBatch.layoutManager = LinearLayoutManager(this)
        rvBatch.adapter = adapter

        btnSelect.setOnClickListener {
            pickMultipleImages.launch("image/*")
        }

        btnSaveAll.setOnClickListener {
            saveAllImages()
        }
    }

    private fun startBatchProcessing() {
        btnSelect.visibility = View.GONE
        btnSaveAll.visibility = View.GONE

        lifecycleScope.launch {
            var processedCount = 0

            for ((index, item) in batchList.withIndex()) {
                // Update UI: Processing
                item.status = "Processing..."
                item.progress = 30
                adapter.notifyItemChanged(index)

                // Load Bitmap
                val inputStream = contentResolver.openInputStream(item.uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    // ⚡ GPU MAGIC
                    val result = rmbgRemover.removeBackground(originalBitmap)

                    if (result != null) {
                        item.status = "Done"
                        item.progress = 100
                        item.resultBitmap = result
                    } else {
                        item.status = "Failed"
                        item.progress = 0
                    }
                } else {
                    item.status = "Error Loading"
                }

                adapter.notifyItemChanged(index)
                processedCount++
                updateTotalProgress(processedCount, batchList.size)
            }

            // All Done
            btnSelect.visibility = View.VISIBLE
            btnSelect.isEnabled = true
            btnSaveAll.visibility = View.VISIBLE // Show Save Button
            Toast.makeText(this@BatchActivity, "Batch Processing Complete!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTotalProgress(current: Int, total: Int) {
        tvTotal.text = "$current/$total Processed"
    }

    private fun saveAllImages() {
        var savedCount = 0
        batchList.forEach { item ->
            item.resultBitmap?.let { bitmap ->
                saveImageToGallery(bitmap)
                savedCount++
            }
        }
        Toast.makeText(this, "Saved $savedCount images to Gallery!", Toast.LENGTH_SHORT).show()
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val filename = "Batch_AI_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    // --- INNER ADAPTER CLASS ---
    inner class BatchAdapter(private val items: List<BatchItem>) : RecyclerView.Adapter<BatchAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.ivThumbnail)
            val status: TextView = view.findViewById(R.id.tvStatus)
            val progress: ProgressBar = view.findViewById(R.id.progressBar)
            val doneIcon: ImageView = view.findViewById(R.id.ivDone)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_batch, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            // If result exists, show result. Else show original.
            if (item.resultBitmap != null) {
                holder.img.setImageBitmap(item.resultBitmap)
            } else {
                holder.img.setImageURI(item.uri)
            }

            holder.status.text = item.status
            holder.progress.progress = item.progress

            if (item.status == "Done") {
                holder.doneIcon.visibility = View.VISIBLE
                holder.progress.visibility = View.GONE
                holder.status.setTextColor(android.graphics.Color.GREEN)
            } else {
                holder.doneIcon.visibility = View.GONE
                holder.progress.visibility = View.VISIBLE
                holder.status.setTextColor(android.graphics.Color.WHITE)
            }
        }

        override fun getItemCount() = items.size
    }
}