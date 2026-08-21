/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.ui.fragment

import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.ListFragment
import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.R
import org.catrobat.catroid.common.Constants.PROGESSIVE_INPUT_DELAY
import org.catrobat.catroid.content.bricks.Brick
import org.catrobat.catroid.content.bricks.BrickBaseType
import org.catrobat.catroid.ui.BottomBar.hideBottomBar
import org.catrobat.catroid.ui.EdgeToEdge
import org.catrobat.catroid.ui.SpriteActivity
import org.catrobat.catroid.ui.adapter.PrototypeBrickAdapter
import org.catrobat.catroid.ui.hideKeyboard
import org.catrobat.catroid.ui.settingsfragments.AccessibilityProfile
import org.catrobat.catroid.ui.settingsfragments.SettingsFragment
import org.catrobat.catroid.utils.ToastUtil
import org.catrobat.catroid.utils.setVisibleOrGone
import java.util.IdentityHashMap
import java.util.Locale

class BrickSearchFragment : ListFragment() {

    private var previousActionBarTitle: CharSequence? = null

    private var searchView: SearchView? = null
    private var recentlyUsedTitle: TextView? = null
    private var queryTextListener: SearchView.OnQueryTextListener? = null
    private var suggestionListener: SearchView.OnSuggestionListener? = null
    private var availableBricks: MutableList<Brick> = mutableListOf()
    private var recentlyUsedBricks: MutableList<Brick> = mutableListOf()
    private var searchResults = mutableListOf<Brick>()
    private var addBrickListener: AddBrickFragment.OnAddBrickListener? = null
    private var category: String? = null
    private var adapter: PrototypeBrickAdapter? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchableText = IdentityHashMap<Brick, String>()
    private var pendingSearch: Runnable? = null
    private var searchGeneration = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_brick_search, container, false)
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        previousActionBarTitle = actionBar?.title
        recentlyUsedTitle = view.findViewById(R.id.recent_used_header)
        hideBottomBar(activity)
        setHasOptionsMenu(true)
        getRecentlyUsedBricks()
        category?.let { prepareBrickList(it) }
        return view
    }

    override fun onStart() {
        super.onStart()
        if (listIndexToFocus != -1) {
            listView.setSelection(listIndexToFocus)
            listIndexToFocus = -1
        }
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long -> adapter?.getItem(position)?.let { addBrickToScript(
            it,
            activity as SpriteActivity,
            addBrickListener,
            parentFragmentManager,
            BRICK_SEARCH_FRAGMENT_TAG
        ) }
        }
    }

    override fun onResume() {
        super.onResume()
        EdgeToEdge.applyBottomPadding(
            requireActivity().findViewById(R.id.fragment_brick_search)
        )
    }

    override fun onDestroy() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        searchGeneration++
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        val isRestoringPreviouslyDestroyedActivity = actionBar == null
        if (!isRestoringPreviouslyDestroyedActivity) {
            actionBar?.title = previousActionBarTitle
        }
        searchView.hideKeyboard()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_search, menu)
        val searchItem = menu.findItem(R.id.search_bar).actionView
        (searchItem as SearchView).apply {
            isIconified = false
            queryHint = context.getString(R.string.search_hint)
        }
        searchResults.clear()
        searchResults.addAll(recentlyUsedBricks)
        adapter = PrototypeBrickAdapter(searchResults)
        listAdapter = adapter
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(
                view: AbsListView,
                scrollState: Int
            ) {
                    searchView.hideKeyboard()
            }

            @SuppressWarnings("EmptyFunctionBlock")
            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {}
        })

        searchView = searchItem
        if (searchView != null) {
            queryTextListener = object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(query: String): Boolean {
                    recentlyUsedTitle?.setVisibleOrGone(query.isBlank())
                    scheduleSearch(query, PROGESSIVE_INPUT_DELAY)
                    return true
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    scheduleSearch(query, 0L, clearFocusWhenDone = true)
                    return true
                }
            }
            suggestionListener = object : SearchView.OnSuggestionListener {
                override fun onSuggestionSelect(position: Int): Boolean {
                    return false
                }

                override fun onSuggestionClick(position: Int): Boolean {
                    val cursor: Cursor? = searchView?.suggestionsAdapter?.cursor
                    cursor?.moveToPosition(position)
                    val suggestion: String? = cursor?.getString(2)
                    searchView?.setQuery(suggestion, true)
                    return true
                }
            }
        }
        searchView?.setOnQueryTextListener(queryTextListener)
        searchView?.setOnSuggestionListener(suggestionListener)
        searchView?.requestFocus()
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun setShowProgressBar(visible: Boolean) {
            if (visible) {
                view?.findViewById<ProgressBar>(R.id.progress_bar)?.visibility = View.VISIBLE
            } else {
                view?.findViewById<ProgressBar>(R.id.progress_bar)?.visibility = View.INVISIBLE
            }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        for (index in 0 until menu.size()) {
            menu.getItem(index).setVisible(false)
        }
        menu.findItem(R.id.search_bar).isVisible = true
    }

    private fun onlyBeginnerBricks(): Boolean = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(AccessibilityProfile.BEGINNER_BRICKS, false)

    private fun scheduleSearch(
        query: String,
        delayMillis: Long,
        clearFocusWhenDone: Boolean = false
    ) {
        pendingSearch?.let(searchHandler::removeCallbacks)
        val generation = ++searchGeneration
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)

        if (normalizedQuery.isEmpty()) {
            searchResults.clear()
            searchResults.addAll(recentlyUsedBricks)
            adapter?.replaceList(searchResults)
            setShowProgressBar(false)
            return
        }

        setShowProgressBar(true)
        val starter = Runnable {
            if (generation == searchGeneration) {
                searchInBatches(normalizedQuery, generation, clearFocusWhenDone)
            }
        }
        pendingSearch = starter
        searchHandler.postDelayed(starter, delayMillis)
    }

    /**
     * Inflating every one of 150+ prototype blocks in one UI callback caused a
     * visible freeze. Build the localized text index once, in small batches,
     * and reuse it for every following query. No regex is compiled per brick.
     */
    private fun searchInBatches(query: String, generation: Int, clearFocusWhenDone: Boolean) {
        val activeContext = context ?: return
        val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val matches = ArrayList<Brick>()
        val matchedClasses = HashSet<Class<*>>()
        var index = 0
        lateinit var batch: Runnable

        batch = Runnable {
            if (generation != searchGeneration || !isAdded) {
                return@Runnable
            }
            val end = minOf(index + SEARCH_INDEX_BATCH_SIZE, availableBricks.size)
            while (index < end) {
                val brick = availableBricks[index++]
                val text = searchableText[brick] ?: run {
                    val indexedText = findBrickString(brick.getPrototypeView(activeContext))
                    (brick as? BrickBaseType)?.releaseDetachedView()
                    searchableText[brick] = indexedText
                    indexedText
                }
                if (tokens.all(text::contains) && matchedClasses.add(brick.javaClass)) {
                    matches.add(brick)
                }
            }

            if (index < availableBricks.size) {
                pendingSearch = batch
                // Returning to the Looper between batches lets drawing and
                // touch input run even while the first search index is built.
                searchHandler.postDelayed(batch, SEARCH_INDEX_BATCH_DELAY_MS)
            } else {
                searchResults.clear()
                searchResults.addAll(matches)
                adapter?.replaceList(searchResults)
                setShowProgressBar(false)
                pendingSearch = null
                if (matches.isEmpty()) {
                    ToastUtil.showError(context, getString(R.string.no_results_found))
                } else if (clearFocusWhenDone) {
                    searchView?.clearFocus()
                }
            }
        }
        pendingSearch = batch
        searchHandler.post(batch)
    }

    private fun findBrickString(view: View): String {
        val result = StringBuilder()
        appendBrickString(view, result)
        return result.toString().lowercase(Locale.ROOT)
    }

    private fun appendBrickString(view: View, result: StringBuilder) {
        when (view) {
            is TextView -> result.append(view.text).append(' ')
            is ViewGroup -> for (i in 0 until view.childCount) {
                appendBrickString(view.getChildAt(i), result)
            }
        }
    }

    fun getRecentlyUsedBricks() {
        val categoryBricksFactory: CategoryBricksFactory = when {
            onlyBeginnerBricks() -> CategoryBeginnerBricksFactory()
            else -> CategoryBricksFactory()
        }
        val backgroundSprite = ProjectManager.getInstance().currentlyEditedScene.backgroundSprite
        val sprite = ProjectManager.getInstance().currentSprite
        recentlyUsedBricks.clear()
        recentlyUsedBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_recently_used), backgroundSprite.equals(sprite), requireContext()))
    }

    @SuppressWarnings("ComplexMethod")
    fun prepareBrickList(category: String = "") {
        val categoryBricksFactory: CategoryBricksFactory = when {
            onlyBeginnerBricks() -> CategoryBeginnerBricksFactory()
            else -> CategoryBricksFactory()
        }
        val backgroundSprite = ProjectManager.getInstance().currentlyEditedScene.backgroundSprite
        val sprite = ProjectManager.getInstance().currentSprite
        availableBricks.clear()
        searchableText.clear()
        if (category != context?.getString(R.string.category_search_bricks)) {
            availableBricks.addAll(categoryBricksFactory.getBricks(category, backgroundSprite.equals(sprite), requireContext()))
        } else {
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_recently_used), backgroundSprite.equals(sprite), requireContext()))
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_event), backgroundSprite.equals(sprite), requireContext()))
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_control), backgroundSprite.equals(sprite), requireContext()))
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_motion), backgroundSprite.equals(sprite), requireContext()))
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_sound), backgroundSprite.equals(sprite), requireContext()))
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_looks), backgroundSprite.equals(sprite), requireContext()))

            if (!onlyBeginnerBricks()) {
                availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_pen), backgroundSprite.equals(sprite), requireContext()))
            }
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_data), backgroundSprite.equals(sprite), requireContext()))
            availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_device), backgroundSprite.equals(sprite), requireContext()))
            if (!onlyBeginnerBricks()) {
                availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_c), backgroundSprite.equals(sprite), requireContext()))
                availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_user_bricks), backgroundSprite.equals(sprite), requireContext()))
            }
            if (SettingsFragment.isTestSharedPreferenceEnabled(activity)) {
                availableBricks.addAll(categoryBricksFactory.getBricks(requireContext().getString(R.string.category_assertions), backgroundSprite.equals(sprite), requireContext()))
            }
        }
    }

    companion object {
        @JvmField
        val BRICK_SEARCH_FRAGMENT_TAG = BrickSearchFragment::class.java.simpleName
        private const val SEARCH_INDEX_BATCH_SIZE = 12
        private const val SEARCH_INDEX_BATCH_DELAY_MS = 1L
        private var listIndexToFocus = -1
        @JvmStatic
        fun newInstance(addBrickListener: AddBrickFragment.OnAddBrickListener?, selectedCategory: String?):
            BrickSearchFragment {
            val fragment = BrickSearchFragment()
            fragment.category = selectedCategory
            fragment.addBrickListener = addBrickListener
            return fragment
        }
    }
}
