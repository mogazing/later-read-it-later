package com.example.pocketreborn.navigation

sealed class Screen(val route: String) {
    object MainTabs : Screen("main_tabs")
    object Library : Screen("library")
    object Reader : Screen("reader/{articleId}?offset={offset}") {
        fun createRoute(articleId: String, startOffset: Int = -1) = "reader/$articleId?offset=$startOffset"
    }
    object Search : Screen("search")
    object Folders : Screen("folders")
    object Discovery : Screen("discovery")
    object Highlights : Screen("highlights")
    object Settings : Screen("settings")
}
