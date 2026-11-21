package com.pedroaba.spix

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.pedroaba.spix.playback.PlaybackInfo
import com.pedroaba.spix.playback.PlaybackViewModel
import com.pedroaba.spix.playback.canEnterPictureInPicture
import com.pedroaba.spix.playback.pictureInPictureAspect
import com.pedroaba.spix.playback.LocalPictureInPictureSourceBounds
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.pedroaba.spix.playback.OPEN_PLAYER
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.doOnPreDraw
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.pedroaba.spix.ui.SpixApp
import com.pedroaba.spix.ui.theme.SpixTheme

class MainActivity : ComponentActivity() {
    private var playerOpenRequest by mutableIntStateOf(0)
    private var appContentStarted = false
    private val playback: PlaybackViewModel by viewModels()
    private var pictureInPicture by mutableStateOf(false)
    private var enteredPictureInPicture = false
    private var videoSourceBounds: Rect? = null
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == OPEN_PLAYER) playerOpenRequest++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val app = application as SpixApplication
        if (packageName.endsWith(".benchmark")) app.processScope.launch {
            try { app.repository.awaitReady(); app.downloadScheduler.bootstrap() }
            catch (failure: Exception) { android.util.Log.w("SpixFixture", "Fixture startup failed", failure) }
        }
        val systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setTheme(if (app.settingsRepository.settings.value.theme.isDark(systemDark)) R.style.Theme_Spix_Dark else R.style.Theme_Spix_Light)
        super.onCreate(savedInstanceState)
        if (intent.action == OPEN_PLAYER) playerOpenRequest++
        enableEdgeToEdge()
        if (savedInstanceState == null && ValueAnimator.areAnimatorsEnabled()) {
            LottieCompositionFactory.fromRawRes(this, R.raw.spix_neon_splash)
            splashScreen.setOnExitAnimationListener { splash -> playSplash(splash, app) }
            // Let the starting window hand off before composing the home screen or binding playback.
            setContentView(FrameLayout(this).apply {
                setBackgroundColor(getColor(R.color.spix_splash_background))
            })
        } else {
            showApp(app)
        }
    }

    private fun playSplash(splash: SplashScreenViewProvider, app: SpixApplication) {
        fun revealApp() {
            if (isFinishing || isDestroyed) {
                splash.remove()
                return
            }
            showApp(app)
            window.decorView.doOnPreDraw { splash.remove() }
        }

        val surface = splash.view as? FrameLayout
        if (surface == null) {
            revealApp()
            return
        }
        val artwork = LottieAnimationView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            speed = 0.6f
            setFontMap(mapOf(
                "SpixBold" to Typeface.create("sans-serif", Typeface.BOLD),
                "SpixRegular" to Typeface.create("sans-serif", Typeface.NORMAL),
            ))
            setFailureListener { revealApp() }
            addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    splash.iconView.alpha = 0f
                }

                override fun onAnimationEnd(animation: Animator) {
                    revealApp()
                }
            })
            setAnimation(R.raw.spix_neon_splash)
        }
        surface.addView(artwork, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        artwork.doOnPreDraw { artwork.playAnimation() }
    }

    private fun showApp(app: SpixApplication) {
        if (appContentStarted) return
        appContentStarted = true
        lifecycleScope.launch {
            playback.info.collect { updatePictureInPicture(it) }
        }
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark(isSystemInDarkTheme())
            SpixTheme(darkTheme = dark) {
                val darkBackground = MaterialTheme.colorScheme.background.luminance() < .18f
                DisposableEffect(darkBackground) {
                    val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkBackground }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                CompositionLocalProvider(LocalPictureInPictureSourceBounds provides ::updateVideoSourceBounds) {
                SpixApp(app.repository, app.settingsRepository, app.metadataRepository, app.downloadScheduler,
                    app.downloader, playback, playerOpenRequest, pictureInPicture)
                }
            }
        }
    }

    private fun supportsPictureInPicture(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @Suppress("DEPRECATION")
    private fun canShowPictureInPicture(): Boolean = supportsPictureInPicture() &&
        getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED &&
        getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun pictureInPictureParams(info: PlaybackInfo): PictureInPictureParams =
        PictureInPictureParams.Builder().apply {
            setAspectRatio(pictureInPictureAspect(info.aspect))
            videoSourceBounds?.let(::setSourceRectHint)
            if (Build.VERSION.SDK_INT >= 31) {
                setAutoEnterEnabled(canShowPictureInPicture() && canEnterPictureInPicture(info))
                setSeamlessResizeEnabled(true)
            }
            if (Build.VERSION.SDK_INT >= 33) setTitle(info.title)
        }.build()

    private fun updateVideoSourceBounds(bounds: Rect) {
        if (videoSourceBounds == bounds) return
        videoSourceBounds = Rect(bounds)
        updatePictureInPicture()
    }

    private fun updatePictureInPicture(info: PlaybackInfo = playback.info.value) {
        if (supportsPictureInPicture() && !isFinishing && !isDestroyed) {
            setPictureInPictureParams(pictureInPictureParams(info))
        }
    }

    override fun onResume() {
        super.onResume()
        pictureInPicture = isInPictureInPictureMode
        if (appContentStarted) updatePictureInPicture()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (appContentStarted && Build.VERSION.SDK_INT < 31 && canShowPictureInPicture() && canEnterPictureInPicture(playback.info.value)) {
            enterPictureInPictureMode(pictureInPictureParams(playback.info.value))
        }
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= 35 && pipState.isTransitioningToPip) pictureInPicture = true
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = enteredPictureInPicture
        enteredPictureInPicture = isInPictureInPictureMode
        pictureInPicture = isInPictureInPictureMode
        if (wasInPip && !isInPictureInPictureMode) {
            // Expanding resumes the activity; dismissing the floating window leaves it stopped.
            window.decorView.post {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    getSystemService(PowerManager::class.java).isInteractive &&
                    !getSystemService(KeyguardManager::class.java).isKeyguardLocked) playback.stop()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferSixtyHz()
    }

    @Suppress("DEPRECATION")
    private fun preferSixtyHz() {
        val screen = windowManager.defaultDisplay
        val current = screen.mode
        val sixtyHz = screen.supportedModes.firstOrNull { mode ->
            mode.physicalWidth == current.physicalWidth && mode.physicalHeight == current.physicalHeight &&
                kotlin.math.abs(mode.refreshRate - 60f) < 0.5f
        }
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 60f
            // Keep the current resolution while selecting the display's native 60 Hz mode.
            if (sixtyHz != null) preferredDisplayModeId = sixtyHz.modeId
        }
    }

}
