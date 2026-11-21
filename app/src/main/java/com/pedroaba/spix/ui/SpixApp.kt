package com.pedroaba.spix.ui

import androidx.compose.foundation.layout.*
import com.pedroaba.spix.playback.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pedroaba.spix.download.DownloadScheduler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.data.SettingsRepository
import com.pedroaba.spix.data.VideoMetadataSource
import com.pedroaba.spix.domain.VideoPreviewState
import com.pedroaba.spix.ui.components.*
import com.pedroaba.spix.ui.screens.*

private enum class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Início", SpixIcons.Home, SpixIcons.HomeFilled),
    Downloads("Downloads", SpixIcons.Download, SpixIcons.DownloadFilled),
    Library("Biblioteca", SpixIcons.Library, SpixIcons.LibraryFilled),
    Playlists("Playlists", SpixIcons.VideoLibrary, SpixIcons.VideoLibraryFilled)
}

@Composable
fun SpixApp(repository: SpixRepository, settingsRepository: SettingsRepository, metadata: VideoMetadataSource, downloads: DownloadScheduler, playlists: com.pedroaba.spix.data.PlaylistMetadataSource, playback: PlaybackViewModel, playerOpenRequest: Int = 0, pictureInPicture: Boolean = false) {
    val storage by repository.storageState.collectAsStateWithLifecycle()
    val recoveryScope = rememberCoroutineScope()
    if (storage !is com.pedroaba.spix.data.StorageState.Ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val state = storage) {
                is com.pedroaba.spix.data.StorageState.Failed -> Column(Modifier.padding(24.dp)) {
                    Text(state.message)
                    TextButton(onClick = { recoveryScope.launch { runCatching { repository.retryInitialization() } } }) { Text("Tentar novamente") }
                }
                else -> CircularProgressIndicator()
            }
        }
        return
    }
    val context = LocalContext.current
    val application = context.applicationContext as com.pedroaba.spix.SpixApplication
    val localPlaylists = application.localPlaylists
    val vm: SpixViewModel = viewModel(factory = viewModelFactory {
        initializer { SpixViewModel(repository, createSavedStateHandle(), metadata, downloads, playlists, localPlaylists::importPlaylist, application.videoStorage::deleteSavedVideo) }
    })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(settingsRepository) }
    })
    val preferences by settingsVm.settings.collectAsStateWithLifecycle()
    val motion = preferences.navigationAnimations && !pictureInPicture
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: Destination.Home.name
    val deletionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.deletionConsentResult(it.resultCode == android.app.Activity.RESULT_OK)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (Build.VERSION.SDK_INT >= 29 || granted[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true) vm.enqueue()
        else vm.storagePermissionDenied()
    }
    fun startDownload() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isEmpty()) vm.enqueue() else permissionLauncher.launch(permissions.toTypedArray())
    }
    val access = remember(playback, nav) { PlayerAccess(playback) { nav.navigate("player") { launchSingleTop = true } } }
    suspend fun openPlayerWhenReady() {
        val destination = nav.currentDestination ?: nav.currentBackStackEntryFlow.first().destination
        if (destination.route != "player") access.open()
    }
    LaunchedEffect(playerOpenRequest) { if (playerOpenRequest > 0) openPlayerWhenReady() }
    LaunchedEffect(pictureInPicture) { if (pictureInPicture) openPlayerWhenReady() }
    val snackbar = remember { SnackbarHostState() }
    var downloadQueueRequest by rememberSaveable { mutableIntStateOf(0) }

    fun navigate(destination: Destination) {
        // Detail routes must not become the saved stack restored by a main tab.
        while (nav.currentDestination?.route !in Destination.entries.map { it.name }) {
            if (!nav.popBackStack()) break
        }
        if (nav.currentDestination?.route == destination.name) return
        nav.navigate(destination.name) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SpixEvent.LinkQueued -> { downloadQueueRequest++; navigate(Destination.Downloads); snackbar.showSnackbar(event.message) }
                is SpixEvent.Message -> snackbar.showSnackbar(event.text)
                is SpixEvent.Removed -> playback.removeDeleted(event.videoId)
                is SpixEvent.DeleteConsent -> {
                    try { deletionLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(event.sender).build()) }
                    catch (_: Exception) { vm.deletionConsentResult(false) }
                }
            }
        }
    }
    LaunchedEffect(settingsVm) { settingsVm.events.collect { snackbar.showSnackbar(it) } }
    fun open(route: String) { nav.navigate(route) { launchSingleTop = true } }
    var saveTrack by remember { mutableStateOf<com.pedroaba.spix.domain.Track?>(null) }
    var deleteTrack by remember { mutableStateOf<com.pedroaba.spix.domain.Track?>(null) }
    val deleteAction: (com.pedroaba.spix.domain.Track) -> Unit = { deleteTrack = it }
    val saveAction: (com.pedroaba.spix.domain.Track) -> Unit = { saveTrack = it }
    CompositionLocalProvider(LocalPlayerAccess provides access, LocalSaveToPlaylist provides saveAction, LocalDeleteTrack provides deleteAction) {
    val isMainDestination = route in Destination.entries.map { it.name }
    val travel = with(LocalDensity.current) { 20.dp.roundToPx() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (pictureInPicture) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { if (!pictureInPicture) SnackbarHost(snackbar) },
        topBar = {
            if (isMainDestination && !pictureInPicture) Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                    BrandHeader({ open("search") }, { open("settings") })
                }
            }
        },
        bottomBar = {
            // Change the viewport once per destination, never animate Scaffold's padding every frame.
            if (isMainDestination && !pictureInPicture) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Destination.entries.forEach { destination ->
                        val selected = route == destination.name
                        NavigationBarItem(selected = selected, onClick = { navigate(destination) },
                            icon = {
                                Box(Modifier.size(width = 32.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                                    Icon(if (selected) destination.selectedIcon else destination.icon, null, Modifier.size(24.dp))
                                }
                            }, label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
            }
        },
    ) { padding ->
        Box(if (pictureInPicture) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController = nav, startDestination = Destination.Home.name,
                modifier = if (pictureInPicture) Modifier.fillMaxSize() else Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = if (route == "player") 0.dp else 20.dp),
                enterTransition = {
                    if (!motion) EnterTransition.None
                    else if (initialState.destination.route in Destination.entries.map { it.name } && targetState.destination.route in Destination.entries.map { it.name })
                        fadeIn(tween(150, delayMillis = 70))
                    else fadeIn(tween(150, delayMillis = 70)) + slideInHorizontally(tween(200, delayMillis = 70, easing = FastOutSlowInEasing)) { travel }
                },
                exitTransition = {
                    if (!motion) ExitTransition.None else fadeOut(tween(70))
                },
                popEnterTransition = {
                    if (!motion) EnterTransition.None else fadeIn(tween(150, delayMillis = 70))
                },
                popExitTransition = {
                    if (!motion) ExitTransition.None else fadeOut(tween(70))
                },
            ) {
                composable(Destination.Home.name) {
                    val link by vm.link.collectAsStateWithLifecycle()
                    val error by vm.error.collectAsStateWithLifecycle()
                    val busy by vm.busy.collectAsStateWithLifecycle()
                    val preview by vm.preview.collectAsStateWithLifecycle()
                    HomeScreen(link, error, busy, preview, repository, preferences.homeCards, settingsVm::setHomeCards, vm::updateLink,
                        onSubmit = { if (vm.validateLink()) { if (preview is VideoPreviewState.Idle || preview is VideoPreviewState.Failed) vm.searchVideo(); open("add") } },
                        onDownloads = { downloadQueueRequest++; navigate(Destination.Downloads) }, onCancel = vm::cancel)
                }
                composable(Destination.Downloads.name) {
                    val retryBusy by vm.retryBusy.collectAsStateWithLifecycle()
                    DownloadsScreen(repository, vm::remove, vm::retry, vm::cancel, vm::cancelAllDownloads, retryBusy, vm::retryDownloads, downloadQueueRequest) { navigate(Destination.Library) }
                }
                composable(Destination.Library.name) {
                    val removalBusy by vm.removalBusy.collectAsStateWithLifecycle()
                    LibraryScreen(repository, localPlaylists, removalBusy, vm::removeMany) { message ->
                        recoveryScope.launch { snackbar.showSnackbar(message) }
                    }
                }
                composable(Destination.Playlists.name) {
                    PlaylistsScreen(localPlaylists, { open("playlist-create") }, { open("playlist/$it") }, { open("playlist-edit/$it") })
                }
                composable("playlist/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    PlaylistDetail(id, localPlaylists, repository, { open("playlist-edit/$id") }, { nav.popBackStack() },
                        playlistSync = application.playlistSync)
                }
                composable("playlist-create?video={video}", arguments = listOf(
                    androidx.navigation.navArgument("video") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }
                )) { entry ->
                    PlaylistEditorScreen(null, entry.arguments?.getString("video"), repository, localPlaylists,
                        { nav.popBackStack() }, { nav.popBackStack(); recoveryScope.launch { snackbar.showSnackbar("Playlist criada.") } })
                }
                composable("playlist-edit/{id}") { entry ->
                    PlaylistEditorScreen(entry.arguments?.getString("id"), null, repository, localPlaylists,
                        { nav.popBackStack() }, { nav.popBackStack() })
                }
                composable("search") {
                    val query by vm.query.collectAsStateWithLifecycle()
                    SearchScreen(query, repository, vm::updateQuery, { nav.popBackStack() }, { navigate(Destination.Home) })
                }
                composable("add") {
                    val link by vm.link.collectAsStateWithLifecycle()
                    val error by vm.error.collectAsStateWithLifecycle()
                    val busy by vm.busy.collectAsStateWithLifecycle()
                    val preview by vm.preview.collectAsStateWithLifecycle()
                    val location by settingsVm.location.collectAsStateWithLifecycle()
                    val playlistName by vm.playlistName.collectAsStateWithLifecycle()
                    AddContentScreen(link, error, busy, preview, motion, location.name, playlistName, vm::updatePlaylistName, vm::updateLink, vm::searchVideo,
                        { nav.popBackStack() }, ::startDownload)
                }
                composable("player") { PlayerScreen(playback, pictureInPicture) { nav.popBackStack() } }
                composable("settings") {
                    val location by settingsVm.location.collectAsStateWithLifecycle()
                    val settingsBusy by settingsVm.busy.collectAsStateWithLifecycle()
                    SettingsScreen(preferences, location, settingsBusy, { nav.popBackStack() },
                        settingsVm::setTheme, settingsVm::setAnimations, settingsVm::selectFolder,
                        settingsVm::useDefaultFolder, settingsVm::refreshLocation, settingsVm::pickerUnavailable,
                        storageRepository = repository, onManageDownloads = { navigate(Destination.Library) })
                }
            }
            if (route != "player" && !pictureInPicture) {
                Box(
                    Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth()
                        .padding(end = 12.dp, bottom = 12.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    MiniPlayer(access)
                }
            }
        }
    }
    if (!pictureInPicture) deleteTrack?.let { track ->
        AlertDialog(onDismissRequest = { deleteTrack = null }, title = { Text("Excluir do celular?") },
            text = { Text("“${track.title}” será removido da biblioteca, das playlists e do armazenamento do celular.") },
            confirmButton = { TextButton(onClick = { deleteTrack = null; vm.remove(track.id) }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTrack = null }) { Text("Cancelar") } })
    }
    if (!pictureInPicture) saveTrack?.let { track ->
        SaveToPlaylistSheet(track, localPlaylists, { saveTrack = null }, {
            saveTrack = null
            open("playlist-create?video=${android.net.Uri.encode(track.id)}")
        })
    }
    }
}
