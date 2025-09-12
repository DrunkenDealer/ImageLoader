package io.rounds.imageloader.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.rounds.imageloader.R
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.rounds.imageloader.example.adapter.ImageAdapter
import io.rounds.imageloader.example.model.ImageData
import io.rounds.imageloader.example.network.NetworkService
import io.rounds.imageloader.library.ImageLoader

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var invalidateCacheButton: Button
    private lateinit var refreshButton: Button

    private lateinit var imageAdapter: ImageAdapter
    private val networkService = NetworkService()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cachedImages: List<ImageData> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addSystemPaddings()
        initViews()
        setupRecyclerView()
        setupClickListeners()

        loadCachedImagesFirst()
    }

    private fun addSystemPaddings() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        invalidateCacheButton = findViewById(R.id.btnInvalidateCache)
        refreshButton = findViewById(R.id.btnRefresh)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(this, emptyList())
        recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = imageAdapter
        }
    }

    private fun setupClickListeners() {
        invalidateCacheButton.setOnClickListener {
            invalidateCache()
        }

        refreshButton.setOnClickListener {
            loadImages()
        }
    }

    private fun loadCachedImagesFirst() {
        val savedImages = loadImagesFromPreferences()
        if (savedImages.isNotEmpty()) {
            cachedImages = savedImages
            imageAdapter.updateImages(savedImages)
            showToast("Loaded ${savedImages.size} cached images")
        }

        loadImages()
    }

    private fun loadImages() {
        showLoading(true)

        networkService.fetchImages(object : NetworkService.NetworkCallback {
            override fun onSuccess(images: List<ImageData>) {
                mainHandler.post {
                    showLoading(false)
                    cachedImages = images
                    imageAdapter.updateImages(images)
                    saveImagesToPreferences(images)
                    showToast("Loaded ${images.size} fresh images")
                }
            }

            override fun onError(error: String) {
                mainHandler.post {
                    showLoading(false)
                    if (cachedImages.isNotEmpty()) {
                        showToast("Using cached images (no internet)")
                    } else {
                        showToast("Error: $error")
                    }
                }
            }
        })
    }

    private fun invalidateCache() {
        ImageLoader.getInstance(this).invalidateCache()
        clearSavedImages()
        cachedImages = emptyList()
        imageAdapter.updateImages(emptyList())
        showToast("Cache invalidated")
    }

    private fun saveImagesToPreferences(images: List<ImageData>) {
        val prefs = getSharedPreferences("image_cache", MODE_PRIVATE)
        val editor = prefs.edit()

        val imageUrls = images.map { "${it.id}|${it.imageUrl}" }.joinToString(";;")
        editor.putString("cached_images", imageUrls)
        editor.apply()
    }

    private fun loadImagesFromPreferences(): List<ImageData> {
        val prefs = getSharedPreferences("image_cache", MODE_PRIVATE)
        val imageUrls = prefs.getString("cached_images", "") ?: ""

        if (imageUrls.isEmpty()) return emptyList()

        return imageUrls.split(";;").mapNotNull { imageString ->
            val parts = imageString.split("|")
            if (parts.size == 2) {
                ImageData(parts[0], parts[1])
            } else null
        }
    }

    private fun clearSavedImages() {
        val prefs = getSharedPreferences("image_cache", MODE_PRIVATE)
        prefs.edit().remove("cached_images").apply()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        invalidateCacheButton.isEnabled = !show
        refreshButton.isEnabled = !show
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}