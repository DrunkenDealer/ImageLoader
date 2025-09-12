package io.rounds.imageloader.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageLoader private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ImageLoader? = null
        private const val CACHE_VALIDITY_HOURS = 4
        private const val CACHE_VALIDITY_MS = CACHE_VALIDITY_HOURS * 60 * 60 * 1000L

        fun getInstance(context: Context): ImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoader(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val memoryCache = ConcurrentHashMap<String, Bitmap>()
    private val cacheTimestamps = ConcurrentHashMap<String, Long>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheDir = File(context.cacheDir, "image_cache")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun loadImage(url: String, imageView: ImageView, placeholder: Drawable? = null) {
        imageView.tag = url

        placeholder?.let {
            imageView.setImageDrawable(it)
        }

        val cacheKey = generateCacheKey(url)

        val cachedBitmap = getFromMemoryCache(cacheKey)
        if (cachedBitmap != null && isCacheValid(cacheKey)) {
            if (imageView.tag == url) {
                imageView.setImageBitmap(cachedBitmap)
            }
            return
        }

        executor.execute {
            try {
                val bitmap = loadBitmapFromUrl(url, cacheKey)
                mainHandler.post {
                    if (imageView.tag == url) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun invalidateCache() {
        memoryCache.clear()
        cacheTimestamps.clear()

        executor.execute {
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadBitmapFromUrl(url: String, cacheKey: String): Bitmap? {
        val diskBitmap = getFromDiskCache(cacheKey)
        val isValid = isCacheValid(cacheKey)

        if (diskBitmap != null && isValid) {
            putToMemoryCache(cacheKey, diskBitmap)
            return diskBitmap
        }

        return downloadBitmap(url)?.also { bitmap ->
            putToMemoryCache(cacheKey, bitmap)
            putToDiskCache(cacheKey, bitmap)
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream

                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, options)

                connection.disconnect()
                connection = URL(url).openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()

                options.inSampleSize = calculateInSampleSize(options, 800, 800)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                val newInputStream = connection.inputStream
                return BitmapFactory.decodeStream(newInputStream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun getFromMemoryCache(key: String): Bitmap? {
        return memoryCache[key]
    }

    private fun putToMemoryCache(key: String, bitmap: Bitmap) {
        memoryCache[key] = bitmap
        cacheTimestamps[key] = System.currentTimeMillis()
    }

    private fun getFromDiskCache(key: String): Bitmap? {
        val file = File(cacheDir, key)
        return if (file.exists()) {
            try {
                val options = BitmapFactory.Options()
                options.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(file.absolutePath, options)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    private fun putToDiskCache(key: String, bitmap: Bitmap) {
        val file = File(cacheDir, key)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            cacheTimestamps[key] = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isCacheValid(key: String): Boolean {
        val timestamp = cacheTimestamps[key]
        val currentTime = System.currentTimeMillis()

        if (timestamp == null) {
            val file = File(cacheDir, key)
            if (file.exists()) {
                val fileTimestamp = file.lastModified()
                cacheTimestamps[key] = fileTimestamp
                return (currentTime - fileTimestamp) < CACHE_VALIDITY_MS
            }
            return false
        }

        return (currentTime - timestamp) < CACHE_VALIDITY_MS
    }

    private fun generateCacheKey(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(url.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }
}