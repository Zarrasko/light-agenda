package com.thelightphone.kaginews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class NewsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, NewsViewModel>(sealedActivity) {

    override val viewModelClass: Class<NewsViewModel>
        get() = NewsViewModel::class.java

    override fun createViewModel(): NewsViewModel = NewsViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is NewsScreenMode.Loading -> LoadingContent(message = mode.message)

                    is NewsScreenMode.Categories -> CategoriesContent(
                        categories = mode.categories,
                        totalCategoryCount = mode.totalCategoryCount,
                        onSelect = viewModel::openCategory,
                        onOpenSettings = viewModel::openCategorySettings,
                    )

                    is NewsScreenMode.CategorySettings -> CategorySettingsContent(
                        categories = mode.categories,
                        hiddenFiles = mode.hiddenFiles,
                        onToggle = viewModel::toggleCategoryVisibility,
                        onBack = viewModel::closeCategorySettings,
                    )

                    is NewsScreenMode.Stories -> StoriesContent(
                        categoryName = mode.category.name,
                        clusters = mode.clusters,
                        onSelect = viewModel::openStory,
                        onBack = viewModel::closeCategory,
                        onRefresh = viewModel::refreshCurrentCategory,
                    )

                    is NewsScreenMode.Story -> StoryContent(
                        categoryName = mode.category.name,
                        cluster = mode.cluster,
                        onBack = viewModel::closeStory,
                    )
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Brief"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = message,
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun CategoriesContent(
    categories: List<NewsCategory>,
    totalCategoryCount: Int,
    onSelect: (NewsCategory) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Brief"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (categories.isEmpty()) {
            val message = if (totalCategoryCount > 0) {
                "No categories selected. Tap the gear icon below to choose which ones you want to see."
            } else {
                "Couldn't load categories. Check your connection and reopen Brief."
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = message,
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )
            }
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                categories.forEach { category ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onSelect(category) })
                            .padding(bottom = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = category.name,
                            variant = LightTextVariant.Heading,
                        )
                    }
                }
            }
        }

        AttributionFooter()

        LightBottomBar(
            items = listOf(
                null,
                LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = onOpenSettings,
                    contentDescription = "Settings",
                ),
            ),
        )
    }
}

@Composable
private fun CategorySettingsContent(
    categories: List<NewsCategory>,
    hiddenFiles: Set<String>,
    onToggle: (NewsCategory) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Categories"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            categories.forEach { category ->
                val isVisible = category.file !in hiddenFiles
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onToggle(category) })
                        .padding(vertical = 0.75f.gridUnitsAsDp()),
                ) {
                    LightIcon(
                        icon = if (isVisible) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                        contentDescription = if (isVisible) "Shown" else "Hidden",
                        modifier = Modifier.padding(end = 0.75f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = category.name,
                        variant = LightTextVariant.Copy,
                        lighten = !isVisible,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoriesContent(
    categoryName: String,
    clusters: List<NewsCluster>,
    onSelect: (NewsCluster) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(categoryName),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (clusters.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "No stories in this category right now.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )
            }
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                clusters.forEach { cluster ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onSelect(cluster) })
                            .padding(bottom = 1.25f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = clusterHeadline(cluster),
                            variant = LightTextVariant.Copy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = cluster.shortSummary.stripCitationMarkers(),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.REFRESH,
                    onClick = onRefresh,
                    contentDescription = "Refresh",
                ),
            ),
        )
    }
}

@Composable
private fun StoryContent(
    categoryName: String,
    cluster: NewsCluster,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(categoryName),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = clusterHeadline(cluster),
                variant = LightTextVariant.Heading,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            cluster.shortSummary.stripCitationMarkers().splitIntoParagraphs().forEach { paragraph ->
                LightText(
                    text = paragraph,
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                )
            }
            cluster.didYouKnow?.takeIf { it.isNotBlank() }?.let { didYouKnow ->
                Column(modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp())) {
                    LightText(
                        text = "DID YOU KNOW?",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = didYouKnow.stripCitationMarkers(),
                        variant = LightTextVariant.Copy,
                    )
                }
            }
            LightText(
                text = cluster.sourceSummary(),
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
    }
}

@Composable
private fun AttributionFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = "Stories via Kagi News (kite.kagi.com)",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
        )
    }
}

private fun clusterHeadline(cluster: NewsCluster): String = cluster.title
