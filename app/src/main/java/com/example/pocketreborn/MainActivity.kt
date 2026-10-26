package com.example.pocketreborn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Build
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pocketreborn.data.local.AppDatabase
import com.example.pocketreborn.data.network.NetworkSecurityUtils
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.FolderRepository
import com.example.pocketreborn.data.repository.HighlightRepository
import com.example.pocketreborn.data.repository.UserPreferencesRepository
import com.example.pocketreborn.data.discovery.DiscoveryRepository
import com.example.pocketreborn.worker.ArticleDownloadScheduler
import com.example.pocketreborn.navigation.Screen
import com.example.pocketreborn.service.TtsPlaybackState
import com.example.pocketreborn.service.TtsService
import com.example.pocketreborn.ui.components.FloatingPillNavBar
import com.example.pocketreborn.ui.components.TtsBottomBarPlayer
import com.example.pocketreborn.ui.folders.FoldersScreen
import com.example.pocketreborn.ui.folders.FoldersViewModel
import com.example.pocketreborn.ui.highlights.HighlightsScreen
import com.example.pocketreborn.ui.highlights.HighlightsViewModel
import com.example.pocketreborn.ui.library.LibraryScreen
import com.example.pocketreborn.ui.library.LibraryViewModel
import com.example.pocketreborn.ui.reader.ReaderScreen
import com.example.pocketreborn.ui.reader.ReaderViewModel
import com.example.pocketreborn.ui.search.SearchScreen
import com.example.pocketreborn.ui.search.SearchViewModel
import com.example.pocketreborn.ui.settings.SettingsScreen
import com.example.pocketreborn.ui.settings.SettingsViewModel
import com.example.pocketreborn.ui.discovery.DiscoveryScreen
import com.example.pocketreborn.ui.discovery.DiscoveryViewModel
import com.example.pocketreborn.ui.onboarding.applyAppLanguage
import com.example.pocketreborn.ui.onboarding.OnboardingScreen
import com.example.pocketreborn.ui.theme.PocketRebornTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var folderRepository: FolderRepository
    private lateinit var highlightRepository: HighlightRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var readerViewModel: ReaderViewModel
    private lateinit var searchViewModel: SearchViewModel
    private lateinit var foldersViewModel: FoldersViewModel
    private lateinit var highlightsViewModel: HighlightsViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var discoveryViewModel: DiscoveryViewModel

    private val ttsService = mutableStateOf<TtsService?>(null)
    private var isTtsBound = false
    private var pendingTtsRequest: PendingTtsRequest? = null

    private val pendingSharedUrls = mutableStateOf<List<String>>(emptyList())
    private var lastHandledIntentKey: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.pocketreborn.ui.onboarding.AppLanguageContext(newBase))
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("LaterTTS", "onServiceConnected: TtsService bound successfully")
            val binder = service as TtsService.LocalBinder
            ttsService.value = binder.getService()
            isTtsBound = true
            pendingTtsRequest?.let { request ->
                Log.d("LaterTTS", "Executing pendingTtsRequest for articleId=${request.articleId}")
                startTtsPlayback(
                    request.articleId,
                    request.title,
                    request.publisher,
                    request.text
                )
                pendingTtsRequest = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("LaterTTS", "onServiceDisconnected: TtsService unbound")
            ttsService.value = null
            isTtsBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        articleRepository = ArticleRepository(db.articleDao(), context = applicationContext)
        folderRepository = FolderRepository(db.folderDao())
        highlightRepository = HighlightRepository(db.highlightDao())
        userPreferencesRepository = UserPreferencesRepository(applicationContext)

        libraryViewModel = LibraryViewModel(articleRepository) { articleId ->
            ArticleDownloadScheduler.enqueue(applicationContext, articleId)
        }
        readerViewModel = ReaderViewModel(articleRepository, highlightRepository, userPreferencesRepository)
        searchViewModel = SearchViewModel(articleRepository)
        foldersViewModel = FoldersViewModel(folderRepository, articleRepository)
        highlightsViewModel = HighlightsViewModel(highlightRepository)
        settingsViewModel = SettingsViewModel(userPreferencesRepository)
        discoveryViewModel = DiscoveryViewModel(
            DiscoveryRepository(applicationContext),
            articleRepository
        ) { articleId ->
            ArticleDownloadScheduler.enqueue(applicationContext, articleId)
        }

        handleIncomingIntent(intent)
        lifecycleScope.launch {
            val pendingIds = withContext(Dispatchers.IO) {
                articleRepository.getPendingDownloadIds()
            }
            pendingIds.forEach { articleId ->
                ArticleDownloadScheduler.enqueue(applicationContext, articleId)
            }
        }

        // Bind while the app is visible; promote the service to foreground only during playback.
        val serviceIntent = Intent(this, TtsService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            val prefs by userPreferencesRepository.userPreferencesFlow.collectAsState(initial = com.example.pocketreborn.data.repository.UserPreferences())

            val darkTheme = when (prefs.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            PocketRebornTheme(darkTheme = darkTheme) {
                MainAppContent(
                    libraryViewModel = libraryViewModel,
                    readerViewModel = readerViewModel,
                    searchViewModel = searchViewModel,
                    foldersViewModel = foldersViewModel,
                    highlightsViewModel = highlightsViewModel,
                    settingsViewModel = settingsViewModel,
                    discoveryViewModel = discoveryViewModel,
                    userPreferencesRepository = userPreferencesRepository,
                    onChangeLanguage = { languageTag ->
                        getSharedPreferences("user_settings", Context.MODE_PRIVATE)
                            .edit()
                            .putString("app_language", languageTag)
                            .apply()
                        settingsViewModel.updateAppLanguage(languageTag)
                        applyAppLanguage(languageTag)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
                    },
                    ttsService = ttsService.value,
                    pendingUrls = pendingSharedUrls.value,
                    onUrlsHandled = { pendingSharedUrls.value = emptyList() },
                    onStartTts = { id, title, publisher, text ->
                        startTtsPlayback(id, title, publisher, text)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lastHandledIntentKey = null
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val intentKey = buildString {
            append(intent.action).append('|').append(intent.type).append('|').append(intent.dataString).append('|')
            append(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)).append('|')
            append(intent.getStringExtra(Intent.EXTRA_HTML_TEXT))
        }
        if (intentKey == lastHandledIntentKey) return
        lastHandledIntentKey = intentKey

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val directUrl = intent.dataString
                    @Suppress("DEPRECATION")
                    val streamUrl = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
                    val sharedText = listOfNotNull(
                        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                        intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
                    ).joinToString("\n")
                    val candidates = when {
                        !directUrl.isNullOrBlank() && NetworkSecurityUtils.isValidWebUrl(directUrl) ->
                            listOf(NetworkSecurityUtils.normalizeUrl(directUrl))
                        !streamUrl.isNullOrBlank() && NetworkSecurityUtils.isValidWebUrl(streamUrl) ->
                            listOf(NetworkSecurityUtils.normalizeUrl(streamUrl))
                        else -> NetworkSecurityUtils.extractUrlsFromSharedText(sharedText)
                    }
                    if (candidates.isEmpty()) {
                        libraryViewModel.reportSaveError("The shared content did not contain a supported HTTP or HTTPS link.")
                    } else {
                        pendingSharedUrls.value = candidates
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUrl = intent.dataString
                if (!dataUrl.isNullOrBlank() && NetworkSecurityUtils.isValidWebUrl(dataUrl)) {
                    pendingSharedUrls.value = listOf(NetworkSecurityUtils.normalizeUrl(dataUrl))
                } else {
                    libraryViewModel.reportSaveError("This link is not a supported HTTP or HTTPS URL.")
                }
            }
        }
    }

    override fun onDestroy() {
        if (isTtsBound) {
            unbindService(serviceConnection)
            isTtsBound = false
        }
        super.onDestroy()
    }

    private fun startTtsPlayback(articleId: String, title: String, publisher: String, text: String) {
        Log.d("LaterTTS", "startTtsPlayback invoked: id=$articleId, textLength=${text.length}, serviceBound=${ttsService.value != null}")
        val service = ttsService.value
        if (service == null) {
            Log.d("LaterTTS", "Service not bound yet; saving pendingTtsRequest")
            pendingTtsRequest = PendingTtsRequest(articleId, title, publisher, text)
        } else {
            Log.d("LaterTTS", "Starting foreground service & calling playArticle(...)")
            ContextCompat.startForegroundService(this, Intent(this, TtsService::class.java))
            service.playArticle(articleId, title, publisher, text)
        }
    }

    private data class PendingTtsRequest(
        val articleId: String,
        val title: String,
        val publisher: String,
        val text: String
    )
}

@Composable
fun MainAppContent(
    libraryViewModel: LibraryViewModel,
    readerViewModel: ReaderViewModel,
    searchViewModel: SearchViewModel,
    foldersViewModel: FoldersViewModel,
    highlightsViewModel: HighlightsViewModel,
    settingsViewModel: SettingsViewModel,
    discoveryViewModel: DiscoveryViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onChangeLanguage: (String) -> Unit,
    ttsService: TtsService?,
    pendingUrls: List<String>,
    onUrlsHandled: () -> Unit,
    onStartTts: (String, String, String, String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    var showFirstLaunchNotificationRationale by remember { mutableStateOf(false) }
    val userPrefs by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.example.pocketreborn.data.repository.UserPreferences()
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        settingsViewModel.markNotificationPermissionPrompted()
        if (!userPrefs.onboardingCompleted) settingsViewModel.completeOnboarding()
    }
    val finishOnboarding = {
        settingsViewModel.markNotificationPermissionPrompted()
        settingsViewModel.completeOnboarding()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (prefs.onboardingCompleted && !permissionGranted && !prefs.notificationPermissionPrompted) {
                showFirstLaunchNotificationRationale = true
            }
        }
    }

    if (showFirstLaunchNotificationRationale) {
        AlertDialog(
            onDismissRequest = {
                showFirstLaunchNotificationRationale = false
                settingsViewModel.markNotificationPermissionPrompted()
            },
            title = { Text("Allow Later notifications?") },
            text = {
                Text("Notifications provide controls for background text-to-speech and updates while articles are saved.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFirstLaunchNotificationRationale = false
                    settingsViewModel.markNotificationPermissionPrompted()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFirstLaunchNotificationRationale = false
                    settingsViewModel.markNotificationPermissionPrompted()
                }) { Text("Not now") }
            }
        )
    }

    LaunchedEffect(pendingUrls) {
        if (pendingUrls.size == 1) {
            libraryViewModel.saveUrl(pendingUrls.single())
            onUrlsHandled()
        }
    }

    if (pendingUrls.size > 1) {
        AlertDialog(
            onDismissRequest = onUrlsHandled,
            title = { Text("Choose a link to save") },
            text = {
                Column {
                    pendingUrls.forEach { url ->
                        TextButton(
                            onClick = {
                                libraryViewModel.saveUrl(url)
                                onUrlsHandled()
                            }
                        ) {
                            Text(url)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onUrlsHandled) { Text("Cancel") }
            }
        )
    }

    val ttsState = ttsService?.playbackState?.collectAsState()?.value ?: TtsPlaybackState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val isMainTabsRoute = currentRoute == null || currentRoute == Screen.MainTabs.route || currentRoute in listOf(
        Screen.Library.route,
        Screen.Discovery.route,
        Screen.Highlights.route,
        Screen.Settings.route
    )

    if (!userPrefs.onboardingCompleted) {
        OnboardingScreen(
            onSelectLanguage = onChangeLanguage,
            onEnableNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    finishOnboarding()
                }
            },
            onFinish = finishOnboarding
        )
    } else Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.MainTabs.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.MainTabs.route) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> LibraryScreen(
                            viewModel = libraryViewModel,
                            onOpenArticle = { articleId ->
                                navController.navigate(Screen.Reader.createRoute(articleId))
                            },
                            onOpenSearch = { navController.navigate(Screen.Search.route) }
                        )
                        1 -> DiscoveryScreen(
                            viewModel = discoveryViewModel,
                            onOpenArticle = { articleId ->
                                navController.navigate(Screen.Reader.createRoute(articleId))
                            }
                        )
                        2 -> HighlightsScreen(
                            viewModel = highlightsViewModel,
                            onOpenArticle = { highlight ->
                                readerViewModel.requestHighlightNavigation(highlight)
                                navController.navigate(Screen.Reader.createRoute(highlight.articleId))
                            }
                        )
                        3 -> SettingsScreen(
                            viewModel = settingsViewModel,
                            onChangeLanguage = onChangeLanguage
                        )
                    }
                }
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("articleId") { type = NavType.StringType },
                    navArgument("offset") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
                val startOffset = backStackEntry.arguments?.getInt("offset") ?: -1
                ReaderScreen(
                    articleId = articleId,
                    viewModel = readerViewModel,
                    onBack = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate(Screen.MainTabs.route) {
                                popUpTo(Screen.MainTabs.route) { inclusive = true }
                            }
                        }
                    },
                    startOffset = startOffset,
                    onStartTts = onStartTts,
                    onSaveUrl = { url -> libraryViewModel.saveUrl(url) }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    viewModel = searchViewModel,
                    onOpenArticle = { articleId ->
                        navController.navigate(Screen.Reader.createRoute(articleId))
                    }
                )
            }

            composable(Screen.Folders.route) {
                FoldersScreen(
                    viewModel = foldersViewModel,
                    onOpenArticle = { articleId ->
                        navController.navigate(Screen.Reader.createRoute(articleId))
                    }
                )
            }
        }

        // Overlay TTS Player and Floating Pill Navigation Bar at the bottom
        // Dynamically shifts above 3-button or gesture navigation bar insets
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            if (ttsState.articleId != null) {
                TtsBottomBarPlayer(
                    state = ttsState,
                    onPlayPause = {
                        if (ttsState.isPlaying) ttsService?.pausePlayback() else ttsService?.resumePlayback()
                    },
                    onSkipForward = { ttsService?.skipForward() },
                    onSkipBackward = { ttsService?.skipBackward() },
                    onSetSpeed = { ttsService?.setSpeed(it) },
                    onClose = { ttsService?.stopPlayback() },
                    onOpenReader = { articleId ->
                        navController.navigate(Screen.Reader.createRoute(articleId))
                    }
                )
            }

            if (isMainTabsRoute) {
                FloatingPillNavBar(
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { pageIndex ->
                        scope.launch {
                            pagerState.animateScrollToPage(pageIndex)
                        }
                    }
                )
            }
        }
    }
}
