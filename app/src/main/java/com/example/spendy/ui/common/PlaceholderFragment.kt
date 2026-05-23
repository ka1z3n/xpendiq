package com.example.spendy.ui.common

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.spendy.R

/**
 * Temporary fragment used to scaffold the bottom-nav graph. Subclasses just provide a title res
 * id; real screens replace these one at a time.
 */
abstract class PlaceholderFragment(@param:StringRes private val titleRes: Int) : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.title).setText(titleRes)
    }
}
