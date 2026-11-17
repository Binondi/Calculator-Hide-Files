package devs.org.calculator.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import devs.org.calculator.databinding.ItemOnboardingPageBinding

data class OnboardingPage(
    @DrawableRes val iconRes: Int,
    @StringRes val tagRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val highlight1Res: Int,
    @StringRes val highlight2Res: Int,
    @StringRes val highlight3Res: Int
)

class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(val binding: ItemOnboardingPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage) {
            binding.ivHeroIcon.setImageResource(page.iconRes)
            binding.tvTag.setText(page.tagRes)
            binding.tvTitle.setText(page.titleRes)
            binding.tvDescription.setText(page.descRes)
            binding.tvHighlight1.setText(page.highlight1Res)
            binding.tvHighlight2.setText(page.highlight2Res)
            binding.tvHighlight3.setText(page.highlight3Res)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OnboardingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size
}

