package com.lagradost.cloudstream3.ui.home

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.cardview.widget.CardView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.coroutines.Job


class HomeScrollViewHolderState(view: ViewBinding) : ViewHolderState<Boolean>(view) {
    /*private fun recursive(view : View) : Boolean {
        if (view.isFocused) {
            println("VIEW: $view | id=${view.id}")
        }
        return (view as? ViewGroup)?.children?.any { recursive(it) } ?: false
    }*/

    // very shitty that we cant store the state when the view clears,
    // but this is because the focus clears before the view is removed
    // so we have to manually store it
    var wasFocused: Boolean = false
    override fun save(): Boolean = wasFocused
    override fun restore(state: Boolean) {
        if (state) {
            wasFocused = false
            // only refocus if tv
            if (isLayout(TV)) {
                itemView.requestFocus()
            }
        }
    }
}

class HomeChildItemAdapter(
    private val fragment: Fragment,
    id: Int,
    private val nextFocusUp: Int? = null,
    private val nextFocusDown: Int? = null,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : BaseAdapter<SearchResponse, Boolean>(fragment, id) {

    var isHorizontal: Boolean = false
    var hasNext: Boolean = false

    private val _isRowFocused = MutableLiveData(false)
    private val isRowFocused: LiveData<Boolean> get() = _isRowFocused

    companion object {
        private var previousId: Int? = null
        private val recyclerViewFocusMap = mutableMapOf<Int, Boolean>()
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Boolean> {
        val expanded = parent.context.isBottomLayout()
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (expanded) HomeResultGridExpandedBinding.inflate(
            inflater,
            parent,
            false
        ) else HomeResultGridBinding.inflate(inflater, parent, false)
        return HomeScrollViewHolderState(binding)
    }

    override fun onBindContent(
        holder: ViewHolderState<Boolean>,
        item: SearchResponse,
        position: Int
    ) {
        val binding = holder.view

        val minSize = 114
        val maxSize = 180
        val (baseWidth, baseHeight) = if (isHorizontal) {
            maxSize to minSize
        } else {
            minSize to maxSize
        }
        val normalWidth = baseWidth.toPx
        val normalHeight = baseHeight.toPx
        val focusedWidth = (baseWidth * 1.4).toInt().toPx
        val focusedHeight = (baseHeight * 1.4).toInt().toPx


        when (binding) {
            is HomeResultGridBinding -> {
                binding.backgroundCard.apply {
                    layoutParams =
                        layoutParams.apply {
                            width = normalWidth
                            height = normalHeight
                        }
                }
            }

            is HomeResultGridExpandedBinding -> {

                isRowFocused.observe(fragment.viewLifecycleOwner) { gotFocus ->
                    if (gotFocus) {
                        if (previousId == id) {
                            binding.backgroundCard.updateLayoutParams {
                                width = focusedWidth
                                height = focusedHeight
                            }
                            return@observe
                        }
                        animateCardSize(
                            binding.backgroundCard,
                            normalWidth,
                            normalHeight,
                            focusedWidth,
                            focusedHeight
                        )
                    } else {
                        if (previousId == id) {
                            binding.backgroundCard.updateLayoutParams {
                                width = normalWidth
                                height = normalHeight
                            }
                            return@observe
                        }
                        animateCardSize(
                            binding.backgroundCard,
                            focusedWidth,
                            focusedHeight,
                            normalWidth,
                            normalHeight
                        )
                    }
                }
                if (position == 0) { // to fix tv
                    binding.backgroundCard.nextFocusLeftId = R.id.nav_rail_view
                }
            }
        }

        SearchResultBuilder.bind(
            clickCallback = { click ->
                // ok, so here we hijack the callback to fix the focus
                when (click.action) {
                    SEARCH_ACTION_LOAD -> (holder as? HomeScrollViewHolderState)?.wasFocused = true
                }
                clickCallback(click)
            },
            item,
            position,
            holder.itemView,
            nextFocusUp,
            nextFocusDown
        )

        holder.itemView.tag = position
        if (binding is HomeResultGridExpandedBinding) {
            holder.itemView.setOnFocusChangeListener { _, gotFocus ->
                if (gotFocus) {
                    recyclerViewFocusMap[id] = true
                } else {
                    if (id != previousId) {
                        previousId = id
                        recyclerViewFocusMap[id] = false
                    }
                }
                _isRowFocused.value = recyclerViewFocusMap[id]
            }
        }
    }

    private fun animateCardSize(
        cardView: CardView,
        startWidth: Int,
        startHeight: Int,
        endWidth: Int,
        endHeight: Int
    ) {
        val widthAnimator = ValueAnimator.ofInt(startWidth, endWidth)
        val heightAnimator = ValueAnimator.ofInt(startHeight, endHeight)

        widthAnimator.addUpdateListener { animation ->
            val newWidth = animation.animatedValue as Int
            val layoutParams = cardView.layoutParams
            layoutParams.width = newWidth
            cardView.layoutParams = layoutParams
        }

        heightAnimator.addUpdateListener { animation ->
            val newHeight = animation.animatedValue as Int
            val layoutParams = cardView.layoutParams
            layoutParams.height = newHeight
            cardView.layoutParams = layoutParams
        }

        widthAnimator.duration = 300
        heightAnimator.duration = 300
        widthAnimator.interpolator = DecelerateInterpolator()
        heightAnimator.interpolator = DecelerateInterpolator()

        widthAnimator.start()
        heightAnimator.start()
    }
}