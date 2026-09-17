package com.vibe.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onResultClick: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categoryResIds = listOf(
        com.vibe.core.ui.R.string.search_tab_all,
        com.vibe.core.ui.R.string.search_tab_songs,
        com.vibe.core.ui.R.string.search_tab_artists,
        com.vibe.core.ui.R.string.search_tab_albums,
        com.vibe.core.ui.R.string.search_tab_playlists,
        com.vibe.core.ui.R.string.search_tab_podcasts
    )

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        SearchBar(
            query = query,
            onQueryChange = {
                query = it
                onQueryChange(it)
            },
            onSearch = {},
            active = false,
            onActiveChange = {},
            placeholder = { Text(androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.nav_search)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = androidx.compose.ui.res.stringResource(com.vibe.core.ui.R.string.search_clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {}

        Spacer(modifier = Modifier.height(12.dp))

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            edgePadding = 0.dp
        ) {
            categoryResIds.forEachIndexed { index, resId ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = { Text(androidx.compose.ui.res.stringResource(resId)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Displays Top Result & independent category rows
        }
    }
}
