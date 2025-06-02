package devs.org.calculator.adapters

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.SeekBar
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import devs.org.calculator.databinding.ViewpagerItemsBinding
import devs.org.calculator.utils.FileManager
import java.io.File
import devs.org.calculator.R

class ImagePreviewAdapter(
    private val context: Context,
    private var lifecycleOwner: LifecycleOwner
) : RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder>() {

    private val differ = AsyncListDiffer(this, FileDiffCallback())
    var currentMediaPlayer: MediaPlayer? = null
    var isMediaPlayerPrepared = false
    var currentViewHolder: ImageViewHolder? = null
    private var currentPlayingPosition = -1
    private var isPlaying = false

    var images: List<File>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ViewpagerItemsBinding.inflate(LayoutInflater.from(context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = images[position]
        val fileType = FileManager(context, lifecycleOwner).getFileType(images[position])
        holder.bind(imageUrl,fileType)
        currentViewHolder = holder

        currentMediaPlayer?.let {
            if (it.isPlaying) it.pause()
            it.seekTo(0)
        }
        currentMediaPlayer = null
        isMediaPlayerPrepared = false

        if (currentMediaPlayer?.isPlaying == true) {
            currentMediaPlayer?.stop()
            currentMediaPlayer?.release()
        }
        currentMediaPlayer = null
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(private val binding: ViewpagerItemsBinding) : RecyclerView.ViewHolder(binding.root) {

        private var mediaPlayer: MediaPlayer? = null
        private var seekHandler = Handler(Looper.getMainLooper())
        private var seekRunnable: Runnable? = null

        fun bind(file: File, fileType: FileManager.FileType) {
            when (fileType) {
                FileManager.FileType.VIDEO -> {
                    binding.imageView.visibility = View.GONE
                    binding.audioBg.visibility = View.GONE
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
                    binding.audioBg.visibility = View.GONE
                    Glide.with(context)
                        .load(file)
                        .into(binding.imageView)
                }
                FileManager.FileType.AUDIO -> {
                    binding.imageView.visibility = View.GONE
                    binding.audioBg.visibility = View.VISIBLE
                    binding.videoView.visibility = View.GONE
                    binding.audioTitle.text = file.name

                    setupAudioPlayer(file)
                    setupSeekBar()
                    setupPlaybackControls()
                }
                else -> {
                    binding.imageView.visibility = View.VISIBLE
                    binding.audioBg.visibility = View.GONE
                    binding.videoView.visibility = View.GONE
                }
            }
        }

        private fun setupAudioPlayer(file: File) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    binding.audioSeekBar.valueTo = mp.duration.toFloat()

                    isMediaPlayerPrepared = true
                }
                setOnCompletionListener {
//                    isPlaying = false
                    binding.playPause.setImageResource(R.drawable.play)
                    binding.audioSeekBar.value = 0f

                    seekHandler.removeCallbacks(seekRunnable!!)
                }
                prepareAsync()
            }
        }

        private fun setupSeekBar() {
            binding.audioSeekBar.addOnChangeListener { slider, value, fromUser ->
                if (fromUser && mediaPlayer != null && isMediaPlayerPrepared) {
                    mediaPlayer?.seekTo(value.toInt())
                }
            }

            seekRunnable = Runnable {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        binding.audioSeekBar.value = mp.currentPosition.toFloat()
                        seekHandler.postDelayed(seekRunnable!!, 100)
                    }
                }
            }
        }


        private fun setupPlaybackControls() {
            binding.playPause.setOnClickListener {
                if (isPlaying) {
                    pauseAudio()
                } else {
                    playAudio()
                }
            }

            binding.preview.setOnClickListener {
                mediaPlayer?.let { mp ->
                    val newPosition = mp.currentPosition - 10000
                    mp.seekTo(maxOf(0, newPosition))
                    binding.audioSeekBar.value = mp.currentPosition.toFloat()
                }
            }

            binding.next.setOnClickListener {
                mediaPlayer?.let { mp ->
                    val newPosition = mp.currentPosition + 10000
                    mp.seekTo(minOf(mp.duration, newPosition))
                    binding.audioSeekBar.value = mp.currentPosition.toFloat()
                }
            }
        }

        private fun playAudio() {
            mediaPlayer?.let { mp ->
                if (currentPlayingPosition != -1 && currentPlayingPosition != adapterPosition) {
                    currentViewHolder?.pauseAudio()
                }
                mp.start()
                isPlaying = true
                binding.playPause.setImageResource(R.drawable.pause)
                seekHandler.post(seekRunnable!!)
                currentPlayingPosition = adapterPosition
                currentViewHolder = this
            }
        }

        private fun pauseAudio() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                    binding.playPause.setImageResource(R.drawable.play)
                    seekHandler.removeCallbacks(seekRunnable!!)
                }
            }
        }

        fun releaseMediaPlayer() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
                mediaPlayer = null
                isPlaying = false
                seekHandler.removeCallbacks(seekRunnable!!)
            }
        }

        private fun playVideoAtPosition(position: Int) {
            val nextFile = images[position]
            val fileType = FileManager(context, lifecycleOwner).getFileType(images[position])
            if (fileType == FileManager.FileType.VIDEO) {
                val videoUri = Uri.fromFile(nextFile)
                binding.videoView.setVideoURI(videoUri)
                binding.videoView.start()
            }
        }
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.releaseMediaPlayer()
    }
}

