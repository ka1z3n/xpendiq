package com.kaizenll.xpendiq.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.kaizenll.xpendiq.R

/**
 * First-run flow: a welcome page, a short feature walkthrough, then the SMS-permission page last
 * (its Allow button drives the permission request in [OnboardingActivity]).
 */
class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    data class Slide(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
    )

    override fun getItemCount(): Int = TUTORIAL.size + 2

    override fun createFragment(position: Int): Fragment = when {
        position == 0 -> OnboardingIntroFragment()
        position <= TUTORIAL.size -> OnboardingTutorialFragment.newInstance(TUTORIAL[position - 1])
        else -> OnboardingPermissionFragment()
    }

    companion object {
        val TUTORIAL = listOf(
            Slide(R.drawable.ic_grid, R.string.onb_t1_title, R.string.onb_t1_body),
            Slide(R.drawable.ic_nav_insights, R.string.onb_t2_title, R.string.onb_t2_body),
            Slide(R.drawable.ic_shield, R.string.onb_t3_title, R.string.onb_t3_body),
        )
    }
}
