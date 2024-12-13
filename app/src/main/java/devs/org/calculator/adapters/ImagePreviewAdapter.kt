import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

class ImagePreviewAdapter(
    private val context: Context,
    private val images: List<File>
) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val photoView = PhotoView(context)
        photoView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        Glide.with(context)
            .load(images[position])
            .into(photoView)

        container.addView(photoView)
        return photoView
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun getCount(): Int = images.size

    override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj
} 