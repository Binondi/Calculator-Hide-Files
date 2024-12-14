package devs.org.calculator.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import devs.org.calculator.databinding.ItemFileBinding
import java.io.File

class ImagePreviewAdapter(
    private val context: Context,
    private val images: List<File>
) : RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = images[position]
        Glide.with(context)
            .load(imageUrl)
            .into(holder.binding.imageView)
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)
}