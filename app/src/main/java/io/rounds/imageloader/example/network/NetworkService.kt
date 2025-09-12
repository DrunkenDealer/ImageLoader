package io.rounds.imageloader.example.network

import io.rounds.imageloader.example.model.ImageData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray

class NetworkService {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    interface NetworkCallback {
        fun onSuccess(images: List<ImageData>)
        fun onError(error: String)
    }

    fun fetchImages(callback: NetworkCallback) {
        executor.execute {
            try {
                val url = URL("https://zipoapps-storage-test.nyc3.digitaloceanspaces.com/image_list.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val images = parseJsonResponse(response)
                    callback.onSuccess(images)
                } else {
                    callback.onError("HTTP Error: ${connection.responseCode}")
                }

                connection.disconnect()
            } catch (e: Exception) {
                callback.onError("Network error: ${e.message}")
            }
        }
    }

    private fun parseJsonResponse(jsonString: String): List<ImageData> {
        val images = mutableListOf<ImageData>()
        try {
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val imageObject = jsonArray.getJSONObject(i)
                val id = imageObject.getString("id")
                val imageUrl = imageObject.getString("imageUrl")
                images.add(ImageData(id, imageUrl))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return images
    }
}