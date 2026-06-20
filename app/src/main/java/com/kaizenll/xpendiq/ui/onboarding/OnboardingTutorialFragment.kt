package com.kaizenll.xpendiq.ui.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kaizenll.xpendiq.R

/** A single illustrated walkthrough slide: an icon, a title, and a body. Content comes via args. */
class OnboardingTutorialFragment : Fragment(R.layout.fragment_onboarding_slide) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        view.findViewById<ImageView>(R.id.slide_icon).setImageResource(args.getInt(ARG_ICON))
        view.findViewById<TextView>(R.id.slide_title).setText(args.getInt(ARG_TITLE))
        view.findViewById<TextView>(R.id.slide_body).setText(args.getInt(ARG_BODY))
    }

    companion object {
        private const val ARG_ICON = "icon"
        private const val ARG_TITLE = "title"
        private const val ARG_BODY = "body"

        fun newInstance(slide: OnboardingPagerAdapter.Slide): OnboardingTutorialFragment =
            OnboardingTutorialFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ICON, slide.iconRes)
                    putInt(ARG_TITLE, slide.titleRes)
                    putInt(ARG_BODY, slide.bodyRes)
                }
            }
    }
}
