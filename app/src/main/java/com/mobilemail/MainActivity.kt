package com.mobilemail

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.security.PinManager
import com.mobilemail.domain.usecase.HandleMessagesStartupUseCase
import com.mobilemail.domain.usecase.LogoutAccountUseCase
import com.mobilemail.domain.usecase.LogoutAllUseCase
import com.mobilemail.domain.usecase.PublishPushNavigationFromIntentUseCase
import com.mobilemail.domain.usecase.ResolveMessagesViewModelContextUseCase
import com.mobilemail.domain.usecase.ResolvePushNavigationUseCase
import com.mobilemail.domain.usecase.ResolveValidSessionUseCase
import com.mobilemail.notifications.NtfyAccountPushTopicsAdapter
import com.mobilemail.ui.navigation.AppNavigationDependencies
import com.mobilemail.ui.navigation.AppNavigationHost
import com.mobilemail.ui.navigation.AppRoutes
import com.mobilemail.ui.login.OAuthBrowserSession
import com.mobilemail.ui.theme.MobileMailTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : FragmentActivity() {
    private val database by lazy {
        AppDatabase.getInstance(applicationContext)
    }
    private val preferencesManager by lazy { PreferencesManager(applicationContext) }
    private val tokenStore by lazy { TokenStore(applicationContext) }
    private val pinManager by lazy { PinManager(applicationContext) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val publishPushNavigationFromIntentUseCase = PublishPushNavigationFromIntentUseCase()
    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable = Runnable {
        if (pinManager.isPinEnabled() && !isPinLocked) {
            isPinLocked = true
        }
    }

    private val navigationDependencies by lazy {
        AppNavigationDependencies(
            database = database,
            preferencesManager = preferencesManager,
            tokenStore = tokenStore,
            activityScope = activityScope,
            resolveValidSessionUseCase = ResolveValidSessionUseCase(),
            logoutAccountUseCase = LogoutAccountUseCase(),
            logoutAllUseCase = LogoutAllUseCase(),
            resolvePushNavigationUseCase = ResolvePushNavigationUseCase(),
            handleMessagesStartupUseCase = HandleMessagesStartupUseCase(),
            resolveMessagesViewModelContextUseCase = ResolveMessagesViewModelContextUseCase(),
            accountPushTopicsPort = NtfyAccountPushTopicsAdapter(),
        )
    }

    private var navigationIntent by mutableStateOf<Intent?>(null)
    private var isPinLocked by mutableStateOf(false)
    private var lastUserInteractionAtMillis = 0L

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationIntent = intent
        publishPushNavigationFromIntentUseCase(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isPinLocked = pinManager.isPinEnabled()
        lastUserInteractionAtMillis = SystemClock.elapsedRealtime()
        syncSecureWindowFlag()
        scheduleAutoLock()
        navigationIntent = intent
        publishPushNavigationFromIntentUseCase(intent)
        setContent {
            MobileMailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startDestination = if (isPinLocked) AppRoutes.PinLock else AppRoutes.Login
                    AppNavigationHost(
                        dependencies = navigationDependencies,
                        startDestination = startDestination,
                        intent = navigationIntent,
                        isPinLocked = isPinLocked,
                        onPinUnlocked = {
                            isPinLocked = false
                            lastUserInteractionAtMillis = SystemClock.elapsedRealtime()
                            scheduleAutoLock()
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncSecureWindowFlag()
        scheduleAutoLock()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && pinManager.isPinEnabled() && !OAuthBrowserSession.isActive()) {
            isPinLocked = true
        }
        autoLockHandler.removeCallbacks(autoLockRunnable)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastUserInteractionAtMillis = SystemClock.elapsedRealtime()
        scheduleAutoLock()
    }

    private fun scheduleAutoLock() {
        autoLockHandler.removeCallbacks(autoLockRunnable)
        if (!pinManager.isPinEnabled() || isPinLocked) return

        val elapsedSinceInteraction = SystemClock.elapsedRealtime() - lastUserInteractionAtMillis
        val remainingDelay = (AUTO_LOCK_TIMEOUT_MILLIS - elapsedSinceInteraction).coerceAtLeast(0L)
        autoLockHandler.postDelayed(autoLockRunnable, remainingDelay)
    }

    private fun syncSecureWindowFlag() {
        if (pinManager.isPinEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private companion object {
        const val AUTO_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1_000L
    }
}
