package devs.org.calculator.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import devs.org.calculator.R
import devs.org.calculator.adapters.OnboardingAdapter
import devs.org.calculator.adapters.OnboardingPage
import devs.org.calculator.databinding.ActivityOnboardingBinding

class OnboardingActivity : BaseCalculatorActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var isFromSettings = false
    private lateinit var pages: List<OnboardingPage>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        isFromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)

        setupToolbar()
        setupPages()
        setupViewPager()
        setupButtons()
        setupBackPress()
    }

    private fun setupToolbar() {
        if (isFromSettings) {
            binding.toolbar.visibility = View.VISIBLE
            binding.toolbar.title = getString(R.string.instructions_title)
            binding.toolbar.setNavigationOnClickListener { finish() }
            binding.btnSkip.visibility = View.GONE
        } else {
            binding.toolbar.navigationIcon = null
            binding.btnSkip.visibility = View.VISIBLE
            binding.btnSkip.setOnClickListener {
                completeOnboarding()
            }
        }
    }

    private fun setupPages() {
        pages = listOf(
            OnboardingPage(
                iconRes = R.drawable.ic_launcher_foreground,
                tagRes = R.string.onboarding_page1_tag,
                titleRes = R.string.onboarding_page1_title,
                descRes = R.string.onboarding_page1_desc,
                highlight1Res = R.string.onboarding_page1_h1,
                highlight2Res = R.string.onboarding_page1_h2,
                highlight3Res = R.string.onboarding_page1_h3
            ),
            OnboardingPage(
                iconRes = R.drawable.ic_key,
                tagRes = R.string.onboarding_page2_tag,
                titleRes = R.string.onboarding_page2_title,
                descRes = R.string.onboarding_page2_desc,
                highlight1Res = R.string.onboarding_page2_h1,
                highlight2Res = R.string.onboarding_page2_h2,
                highlight3Res = R.string.onboarding_page2_h3
            ),
            OnboardingPage(
                iconRes = R.drawable.ic_privacy_tip,
                tagRes = R.string.onboarding_page3_tag,
                titleRes = R.string.onboarding_page3_title,
                descRes = R.string.onboarding_page3_desc,
                highlight1Res = R.string.onboarding_page3_h1,
                highlight2Res = R.string.onboarding_page3_h2,
                highlight3Res = R.string.onboarding_page3_h3
            ),
            OnboardingPage(
                iconRes = R.drawable.ic_folder,
                tagRes = R.string.onboarding_page4_tag,
                titleRes = R.string.onboarding_page4_title,
                descRes = R.string.onboarding_page4_desc,
                highlight1Res = R.string.onboarding_page4_h1,
                highlight2Res = R.string.onboarding_page4_h2,
                highlight3Res = R.string.onboarding_page4_h3
            ),
            OnboardingPage(
                iconRes = R.drawable.ic_heart,
                tagRes = R.string.onboarding_page5_tag,
                titleRes = R.string.onboarding_page5_title,
                descRes = R.string.onboarding_page5_desc,
                highlight1Res = R.string.onboarding_page5_h1,
                highlight2Res = R.string.onboarding_page5_h2,
                highlight3Res = R.string.onboarding_page5_h3
            )
        )
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(pages)
        binding.viewPager.adapter = adapter

        setupIndicators(pages.size)
        updateIndicators(0)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                updateBottomButtons(position)
            }
        })
    }

    private fun setupIndicators(count: Int) {
        binding.layoutDots.removeAllViews()
        val margin = (resources.displayMetrics.density * 4).toInt()

        for (i in 0 until count) {
            val dot = ImageView(this).apply {
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.indicator_dot_inactive))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(margin, 0, margin, 0)
                }
                layoutParams = params
            }
            binding.layoutDots.addView(dot)
        }
    }

    private fun updateIndicators(selectedPosition: Int) {
        val childCount = binding.layoutDots.childCount
        for (i in 0 until childCount) {
            val imageView = binding.layoutDots.getChildAt(i) as? ImageView ?: continue
            val drawableRes = if (i == selectedPosition) {
                R.drawable.indicator_dot_active
            } else {
                R.drawable.indicator_dot_inactive
            }
            imageView.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this, drawableRes))
        }
    }

    private fun updateBottomButtons(position: Int) {
        if (position == 0) {
            binding.btnPrevious.visibility = View.INVISIBLE
        } else {
            binding.btnPrevious.visibility = View.VISIBLE
        }

        if (position == pages.size - 1) {
            binding.btnNext.text = if (isFromSettings) getString(R.string.got_it) else getString(R.string.get_started)
        } else {
            binding.btnNext.text = getString(R.string.next)
        }
    }

    private fun setupButtons() {
        binding.btnPrevious.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.setCurrentItem(current - 1, true)
            }
        }

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.size - 1) {
                binding.viewPager.setCurrentItem(current + 1, true)
            } else {
                completeOnboarding()
            }
        }
    }

    private fun completeOnboarding() {
        prefs.setSeenOnboarding(true)
        if (isFromSettings) {
            finish()
        } else {
            if (!prefs.hasPassword()) {
                startActivity(Intent(this, SetupPasswordActivity::class.java))
            }
            finish()
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.viewPager.currentItem
                if (current > 0) {
                    binding.viewPager.setCurrentItem(current - 1, true)
                } else {
                    if (isFromSettings) {
                        finish()
                    } else {
                        finishAffinity()
                    }
                }
            }
        })
    }

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
