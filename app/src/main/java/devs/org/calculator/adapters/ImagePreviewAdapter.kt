package devs.org.calculator.adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import devs.org.calculator.adapters.FileAdapter.FileDiffCallback
import devs.org.calculator.databinding.ViewpagerItemsBinding
import devs.org.calculator.utils.FileManager
import java.io.File

class ImagePreviewAdapter(
    private val context: Context,
    private var fileType: FileManager.FileType
) : RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder>() {

    // Use AsyncListDiffer for managing the list
    private val differ = AsyncListDiffer(this, FileDiffCallback())

    // Expose data management through differ
    var images: List<File>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ViewpagerItemsBinding.inflate(LayoutInflater.from(context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = images[position]
        holder.bind(imageUrl)
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(val binding: ViewpagerItemsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            when (fileType) {
                FileManager.FileType.VIDEO -> {
                    binding.imageView.visibility = View.GONE
                    binding.videoView.visibility = View.VISIBLE

                    val videoUri = Uri.fromFile(file)
                    binding.videoView.setVideoURI(videoUri)
                    binding.videoView.start()

                    val mediaController = MediaController(context)
                    mediaController.setAnchorView(binding.videoView)
                    binding.videoView.setMediaController(mediaController)

                    mediaController.setPrevNextListeners(
                        {
                            val nextPosition = (adapterPosition + 1) % images.size
                            playVideoAtPosition(nextPosition)
                        },
                        {
                            val prevPosition = if (adapterPosition - 1 < 0) images.size - 1 else adapterPosition - 1
                            playVideoAtPosition(prevPosition)
                        }
                    )

                    binding.videoView.setOnCompletionListener {
                        val nextPosition = (adapterPosition + 1) % images.size
                        playVideoAtPosition(nextPosition)
                    }
                }
                FileManager.FileType.IMAGE -> {
                    binding.imageView.visibility = View.VISIBLE
                    binding.videoView.visibility = View.GONE
                    Glide.with(context)
                        .load(file)
                        .into(binding.imageView)
                }
                FileManager.FileType.AUDIO -> {
                    // Handle audio if necessary
                }
                else -> {
                    // Handle other types if necessary
                }
            }
        }

        private fun playVideoAtPosition(position: Int) {
            val nextFile = images[position]
            if (fileType == FileManager.FileType.VIDEO) {
                val videoUri = Uri.fromFile(nextFile)
                binding.videoView.setVideoURI(videoUri)
                binding.videoView.start()
            }
        }
    }
}

