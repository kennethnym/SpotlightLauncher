package kenneth.app.spotlightlauncher.searching.views

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import kenneth.app.spotlightlauncher.AppState
import kenneth.app.spotlightlauncher.R
import kenneth.app.spotlightlauncher.api.SearchModule
import kenneth.app.spotlightlauncher.api.SearchResult
import kenneth.app.spotlightlauncher.extension.ExtensionManager
import kenneth.app.spotlightlauncher.prefs.SearchPreferenceManager
import kenneth.app.spotlightlauncher.searching.Searcher
import kenneth.app.spotlightlauncher.utils.BindingRegister
import kenneth.app.spotlightlauncher.utils.activity
import javax.inject.Inject

@AndroidEntryPoint
class SearchResultView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    @Inject
    lateinit var extensionManager: ExtensionManager

    @Inject
    lateinit var searcher: Searcher

    @Inject
    lateinit var searchPreferenceManager: SearchPreferenceManager

    @Inject
    lateinit var appState: AppState

    private val searchResultContainers =
        extensionManager.installedSearchModules
            .map { null }
            .toMutableList<SearchResultContainer?>()

    private val containersInLayout = mutableSetOf<Int>()

    init {
        fitsSystemWindows = false
        orientation = VERTICAL
        gravity = Gravity.START

        val paddingHorizontal = resources.getDimensionPixelSize(R.dimen.widget_margin_horizontal)
        val paddingTop = resources.getDimensionPixelSize(R.dimen.widget_space_between)

        setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0)

        with(searcher) {
            addSearchResultListener { result ->
                activity?.runOnUiThread {
                    showSearchResult(result)
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePadding(
            bottom = activity?.window?.decorView?.rootWindowInsets?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    it.getInsets(WindowInsets.Type.navigationBars()).bottom
                else
                    it.systemWindowInsetBottom
            } ?: 0
        )
    }

    fun clearSearchResults() {
        searchResultContainers.forEach { it?.isVisible = false }
    }

    private fun showSearchResult(result: SearchResult) {
        BindingRegister.activityMainBinding.widgetsPanel.showSearchResults()
        extensionManager.lookupSearchModule(result.searchModuleName)?.let { searchModule ->
            val order = searchPreferenceManager.orderOf(searchModule.metadata.name)

            searchResultContainers[order]
                ?.let {
                    searchModule.adapter.onBindSearchResult(it.viewHolder!!, result)
                    it.isVisible = true
                }
                ?: run {
                    val container = createSearchResultContainer(searchModule, at = order)
                    searchModule.adapter.onBindSearchResult(container.viewHolder!!, result)
                    onNewSearchResultContainerCreated()
                    container.isVisible = true
                }
        }
    }

    private fun onNewSearchResultContainerCreated() {
        searchResultContainers
            .filterNotNull()
            .forEach {
                if (!containersInLayout.contains(it.id)) {
                    addView(it)
                    containersInLayout.add(it.id)
                }
            }
    }

    private fun createSearchResultContainer(
        searchModule: SearchModule,
        at: Int
    ): SearchResultContainer {
        val container = SearchResultContainer(context)
        val vh = searchModule.adapter.onCreateViewHolder(container)
        searchResultContainers[at] = container.apply {
            id = generateViewId()
            viewHolder = vh
        }
        return container
    }
}