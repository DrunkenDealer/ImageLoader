package io.rounds.imageloader.example.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.rounds.imageloader.R
import io.rounds.imageloader.example.model.ImageData
import io.rounds.imageloader.library.ImageLoader

class ImageAdapter(
    private val context: Context,
    private var images: List<ImageData>
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private val imageLoader = ImageLoader.getInstance(context)

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val textId: TextView = itemView.findViewById(R.id.textId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageData = images[position]

        holder.textId.text = "ID: ${imageData.id}"

        val placeholder = ContextCompat.getDrawable(context, R.drawable.ic_placeholder)
        imageLoader.loadImage(imageData.imageUrl, holder.imageView, placeholder)
    }

    override fun getItemCount(): Int = images.size

    fun updateImages(newImages: List<ImageData>) {
        images = newImages
        notifyDataSetChanged()
    }
}