package com.pedroaba.spix.ui

import androidx.compose.foundation.layout.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pedroaba.spix.download.DownloadScheduler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pedroaba.spix.data.SpixRepository
import com.pedroaba.spix.data.SettingsRepository
import com.pedroaba.spix.data.VideoMetadataSource
import com.pedroaba.spix.domain.VideoPreviewState
import com.pedroaba.spix.ui.components.*
import com.pedroaba.spix.ui.screens.*

private enum class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Início", SpixIcons.Home, SpixIcons.HomeFilled),
    Downloads("Downloads", SpixIcons.Download, SpixIcons.DownloadFilled),
    Library("Biblioteca", SpixIcons.Library, SpixIcons.LibraryFilled)
}

@Composable
fun SpixApp(repository: SpixRepository, settingsRepository: SettingsRepository, metadata: VideoMetadataSource, downloads: DownloadScheduler) {
    val vm: SpixViewModel = viewModel(factory = viewModelFactory {
        initializer { SpixViewModel(repository, createSavedStateHandle(), metadata, downloads) }
    })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(settingsRepository) }
    })
    val preferences by settingsVm.settings.collectAsStateWithLifecycle()
    val location by settingsVm.location.collectAsStateWithLifecycle()
    val settingsBusy by settingsVm.busy.collectAsStateWithLifecycle()
    val motion = preferences.navigationAnimations
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: Destination.Home.name
    val link by vm.link.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val requests by vm.requests.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
    val readyVideo = (preview as? VideoPreviewState.Ready)?.video
    val snackbar = remember { SnackbarHostState() }
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(link) { showConfirmation = false }

    fun navigate(destination: Destination) {
        nav.navigate(destination.name) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                SpixEvent.LinkQueued -> { navigate(Destination.Downloads); snackbar.showSnackbar("Download adicionado à fila.") }
                is SpixEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    LaunchedEffect(settingsVm) { settingsVm.events.collect { snackbar.showSnackbar(it) } }
    fun open(route: String) { nav.navigate(route) { launchSingleTop = true } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = route != "search" && route != "settings",
                enter = if (motion) fadeIn(tween(180)) + expandVertically(tween(220)) else EnterTransition.None,
                exit = if (motion) fadeOut(tween(120)) + shrinkVertically(tween(180)) else ExitTransition.None,
            ) {
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
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController = nav, startDestination = Destination.Home.name,
                modifier = Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp),
                enterTransition = {
                    if (!motion) EnterTransition.None else fadeIn(tween(180)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) {
                        if (routeOrder(targetState.destination.route) >= routeOrder(initialState.destination.route)) it / 8 else -it / 8
                    }
                },
                exitTransition = {
                    if (!motion) ExitTransition.None else fadeOut(tween(140)) + slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                        if (routeOrder(targetState.destination.route) >= routeOrder(initialState.destination.route)) -it / 8 else it / 8
                    }
                },
                popEnterTransition = {
                    if (!motion) EnterTransition.None else fadeIn(tween(180)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 8 }
                },
                popExitTransition = {
                    if (!motion) ExitTransition.None else fadeOut(tween(140)) + slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 }
                },
            ) {
                composable(Destination.Home.name) {
                    TabScreen({ open("search") }, { open("settings") }) {
                        HomeScreen(link, error, busy, preview, motion, tracks.size, vm::updateLink, vm::searchVideo) { showConfirmation = true }
                    }
                }
                composable(Destination.Downloads.name) {
                    TabScreen({ open("search") }, { open("settings") }) { DownloadsScreen(requests, vm::remove, vm::retry, vm::cancel) }
                }
                composable(Destination.Library.name) {
                    TabScreen({ open("search") }, { open("settings") }) { LibraryScreen(tracks) }
                }
                composable("search") { SearchScreen(query, tracks, vm::updateQuery) { nav.popBackStack() } }
                composable("settings") {
                    SettingsScreen(preferences, location, settingsBusy, { nav.popBackStack() },
                        settingsVm::setTheme, settingsVm::setAnimations, settingsVm::selectFolder,
                        settingsVm::useDefaultFolder, settingsVm::refreshLocation, settingsVm::pickerUnavailable)
                }
            }
        }
    }
    if (showConfirmation && readyVideo != null) AlertDialog(onDismissRequest = { showConfirmation = false },
        title = { Text("Baixar este vídeo?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(readyVideo.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(readyVideo.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("O vídeo será salvo em ${location.name}. Qualidade de até 720p, com áudio.")
        } },
        confirmButton = { TextButton(onClick = { showConfirmation = false; startDownload() }, enabled = !busy) { Text("Baixar vídeo") } },
        dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("Cancelar") } })
}

private fun routeOrder(route: String?): Int = when (route) {
    Destination.Home.name -> 0
    Destination.Downloads.name -> 1
    Destination.Library.name -> 2
    "search" -> 3
    "settings" -> 4
    else -> 0
}

@Composable
private fun TabScreen(onSearch: () -> Unit, onSettings: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader(onSearch, onSettings)
        Box(Modifier.weight(1f)) { content() }
    }
}
