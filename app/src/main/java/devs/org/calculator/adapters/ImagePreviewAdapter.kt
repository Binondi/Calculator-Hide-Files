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
    private var currentPlayingPosition = -1
    private var currentViewHolder: ImageViewHolder? = null

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

        stopAndResetCurrentAudio()

        holder.bind(imageUrl, fileType, position)
    }

    override fun getItemCount(): Int = images.size

    private fun stopAndResetCurrentAudio() {
        currentViewHolder?.stopAndResetAudio()
        currentPlayingPosition = -1
        currentViewHolder = null
    }

    inner class ImageViewHolder(private val binding: ViewpagerItemsBinding) : RecyclerView.ViewHolder(binding.root) {

        private var seekHandler = Handler(Looper.getMainLooper())
        private var seekRunnable: Runnable? = null
        private var mediaPlayer: MediaPlayer? = null
        private var isMediaPlayerPrepared = false
        private var isPlaying = false
        private var currentPosition = 0

        fun bind(file: File, fileType: FileManager.FileType, position: Int) {
            currentPosition = position

            releaseMediaPlayer()
            resetAudioUI()

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
                    setupPlaybackControls()
                }
                else -> {
                    binding.imageView.visibility = View.VISIBLE
                    binding.audioBg.visibility = View.GONE
                    binding.videoView.visibility = View.GONE
                }
            }
        }

        private fun resetAudioUI() {
            binding.playPause.setImageResource(R.drawable.play)
            binding.audioSeekBar.value = 0f
            binding.audioSeekBar.valueTo = 100f // Default value
            seekRunnable?.let { seekHandler.removeCallbacks(it) }
        }

        private fun setupAudioPlayer(file: File) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { mp ->
                        binding.audioSeekBar.valueTo = mp.duration.toFloat()
                        binding.audioSeekBar.value = 0f
                        setupSeekBar()
                        isMediaPlayerPrepared = true
                    }
                    setOnCompletionListener {
                        stopAndResetAudio()
                    }
                    setOnErrorListener { _, _, _ ->
                        releaseMediaPlayer()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                releaseMediaPlayer()
            }
        }

        private fun setupSeekBar() {
            binding.audioSeekBar.addOnChangeListener { _, value, fromUser ->
                if (fromUser && mediaPlayer != null && isMediaPlayerPrepared) {
                    mediaPlayer?.seekTo(value.toInt())
                }
            }

            seekRunnable = Runnable {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying && isMediaPlayerPrepared) {
                        try {
                            binding.audioSeekBar.value = mp.currentPosition.toFloat()
                            seekHandler.postDelayed(seekRunnable!!, 100)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
                    if (isMediaPlayerPrepared) {
                        try {
                            val newPosition = mp.currentPosition - 10000
                            mp.seekTo(maxOf(0, newPosition))
                            binding.audioSeekBar.value = mp.currentPosition.toFloat()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            binding.next.setOnClickListener {
                mediaPlayer?.let { mp ->
                    if (isMediaPlayerPrepared) {
                        try {
                            val newPosition = mp.currentPosition + 10000
                            mp.seekTo(minOf(mp.duration, newPosition))
                            binding.audioSeekBar.value = mp.currentPosition.toFloat()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        private fun playAudio() {
            mediaPlayer?.let { mp ->
                if (isMediaPlayerPrepared) {
                    try {
                        if (currentPlayingPosition != currentPosition) {
                            stopAndResetCurrentAudio()
                        }

                        mp.start()
                        isPlaying = true
                        binding.playPause.setImageResource(R.drawable.pause)
                        seekRunnable?.let { seekHandler.post(it) }

                        currentPlayingPosition = currentPosition
                        currentViewHolder = this@ImageViewHolder
                    } catch (e: Exception) {
                        e.printStackTrace()
                        releaseMediaPlayer()
                    }
                }
            }
        }

        private fun pauseAudio() {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                        binding.playPause.setImageResource(R.drawable.play)
                        seekRunnable?.let { seekHandler.removeCallbacks(it) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    releaseMediaPlayer()
                }
            }
        }

        fun stopAndResetAudio() {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.stop()
                        mp.prepare()
                    } else if (isMediaPlayerPrepared) {
                        mp.seekTo(0)
                    }
                }
                isPlaying = false
                resetAudioUI()

                if (currentPlayingPosition == currentPosition) {
                    currentPlayingPosition = -1
                    currentViewHolder = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                releaseMediaPlayer()
            }
        }

        fun releaseMediaPlayer() {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mediaPlayer = null
                isPlaying = false
                isMediaPlayerPrepared = false
                seekRunnable?.let { seekHandler.removeCallbacks(it) }

                if (currentPlayingPosition == currentPosition) {
                    currentPlayingPosition = -1
                    currentViewHolder = null
                }
            }
        }

        private fun playVideoAtPosition(position: Int) {
            if (position < images.size) {
                val nextFile = images[position]
                val fileType = FileManager(context, lifecycleOwner).getFileType(images[position])
                if (fileType == FileManager.FileType.VIDEO) {
                    val videoUri = Uri.fromFile(nextFile)
                    binding.videoView.setVideoURI(videoUri)
                    binding.videoView.start()
                }
            }
        }
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.releaseMediaPlayer()
    }

    fun onItemScrolledAway(position: Int) {
        if (currentPlayingPosition == position) {
            stopAndResetCurrentAudio()
        }
    }

    fun releaseAllResources() {
        stopAndResetCurrentAudio()
    }
}