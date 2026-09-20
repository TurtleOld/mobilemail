package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateInstallState
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.InstallCallback
import com.mobilemail.domain.port.InstallOutcome
import com.mobilemail.domain.port.InstallStartResult
import com.mobilemail.domain.port.UpdateInstallPort
import com.mobilemail.ui.common.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private data class InstallTarget(
    val manifest: UpdateReleaseManifest,
    val apkFilePath: String
) {
    val key: String get() = "${manifest.versionCode}:$apkFilePath"
}

/**
 * Координирует установку уже скачанного и проверенного APK через системный
 * `PackageInstaller`: разрешение источника, обязательное подтверждение
 * пользователя, отмена и ошибки.
 *
 * Живёт один экземпляр на процесс (см. [UpdateInstallCoordinatorHolder]), не
 * хранит Activity и callbacks. Результат установки принимается только от
 * активной попытки: повторный или посторонний callback игнорируется. Системное
 * подтверждение запускается только из foreground и только после разблокировки —
 * из receiver оно лишь откладывается.
 */
class UpdateInstallCoordinator(
    private val installPort: UpdateInstallPort,
    private val apkExists: (String) -> Boolean = { File(it).exists() },
    private val nextAttemptId: () -> Long = defaultAttemptIdGenerator()
) {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    private val _isOfferDismissed = MutableStateFlow(false)
    val isOfferDismissed: StateFlow<Boolean> = _isOfferDismissed.asStateFlow()

    private val mutex = Mutex()
    private var currentInstall: InstallTarget? = null
    private var activeAttemptId: Long? = null
    private var isUserActionDeferred = false

    @Volatile
    private var isForeground = false

    @Volatile
    private var isUnlocked = false

    /**
     * Загрузка завершилась: показываем предложение «Установить» / «Позже».
     * Если пользователь дождался загрузки в foreground ([autoContinue]),
     * попытка один раз продолжается к системному подтверждению.
     */
    fun onDownloadCompleted(
        scope: CoroutineScope,
        manifest: UpdateReleaseManifest,
        apkFilePath: String,
        autoContinue: Boolean
    ) {
        scope.launch {
            mutex.withLock {
                val target = InstallTarget(manifest, apkFilePath)
                if (currentInstall?.key == target.key) return@withLock
                currentInstall = target
                _isOfferDismissed.value = false
                if (autoContinue) {
                    startInstallLocked()
                } else {
                    _state.value = UpdateInstallState.Ready(manifest, apkFilePath)
                }
            }
        }
    }

    /** Явное согласие пользователя: «Установить» или «Повторить». */
    fun install(scope: CoroutineScope) {
        scope.launch {
            mutex.withLock { startInstallLocked() }
        }
    }

    /** «Позже»: предложение скрывается, но готовый APK остаётся для установки. */
    fun dismissOffer() {
        _isOfferDismissed.value = true
    }

    /**
     * Приложение вернулось в foreground. Пока [unlocked] равно `false` (например,
     * действует PIN-блокировка), ни отложенное подтверждение, ни проверка
     * разрешения не выполняются.
     */
    fun onAppForegrounded(scope: CoroutineScope, unlocked: Boolean) {
        scope.launch {
            mutex.withLock {
                isForeground = true
                isUnlocked = unlocked
                if (!unlocked) return@withLock
                if (isUserActionDeferred) {
                    launchDeferredUserActionLocked()
                    return@withLock
                }
                if (_state.value is UpdateInstallState.AwaitingPermission) {
                    resumeAfterPermissionLocked()
                }
            }
        }
    }

    fun onAppBackgrounded() {
        isForeground = false
        isUnlocked = false
    }

    private suspend fun startInstallLocked() {
        val target = currentInstall ?: return
        if (!apkExists(target.apkFilePath)) {
            activeAttemptId = null
            _state.value = UpdateInstallState.Failed(
                AppError.UnknownError("Скачанный файл обновления не найден"),
                target.manifest,
                target.apkFilePath
            )
            return
        }
        if (!installPort.isInstallPermissionGranted()) {
            if (installPort.openInstallPermissionSettings()) {
                _state.value = UpdateInstallState.AwaitingPermission(target.manifest, target.apkFilePath)
            } else {
                _state.value = UpdateInstallState.Failed(
                    AppError.UnknownError("Не удалось открыть настройки разрешения установки"),
                    target.manifest,
                    target.apkFilePath
                )
            }
            return
        }
        val attemptId = nextAttemptId()
        activeAttemptId = attemptId
        when (val result = installPort.startInstall(target.apkFilePath, attemptId)) {
            InstallStartResult.Started -> {
                _state.value = UpdateInstallState.AwaitingConfirmation(target.manifest, target.apkFilePath)
            }
            is InstallStartResult.Failed -> {
                activeAttemptId = null
                _state.value = UpdateInstallState.Failed(
                    AppError.UnknownError(result.reason),
                    target.manifest,
                    target.apkFilePath
                )
            }
        }
    }

    private suspend fun resumeAfterPermissionLocked() {
        val target = currentInstall ?: return
        if (installPort.isInstallPermissionGranted()) {
            startInstallLocked()
        } else {
            _state.value = UpdateInstallState.Ready(target.manifest, target.apkFilePath)
        }
    }

    private fun launchDeferredUserActionLocked() {
        if (!isUserActionDeferred || !isForeground || !isUnlocked) return
        if (installPort.launchDeferredUserAction()) {
            isUserActionDeferred = false
        }
    }

    private suspend fun onInstallCallback(callback: InstallCallback) {
        mutex.withLock {
            val attemptId = activeAttemptId ?: return@withLock
            if (callback.attemptId != attemptId) return@withLock
            val target = currentInstall ?: return@withLock
            when (val outcome = callback.outcome) {
                InstallOutcome.Success -> {
                    activeAttemptId = null
                    isUserActionDeferred = false
                    _state.value = UpdateInstallState.Installed(target.manifest)
                }
                InstallOutcome.Cancelled -> {
                    activeAttemptId = null
                    isUserActionDeferred = false
                    _state.value = UpdateInstallState.Cancelled(target.manifest, target.apkFilePath)
                }
                InstallOutcome.AwaitingUserAction -> {
                    isUserActionDeferred = true
                    _state.value = UpdateInstallState.AwaitingConfirmation(target.manifest, target.apkFilePath)
                    launchDeferredUserActionLocked()
                }
                is InstallOutcome.Failed -> {
                    activeAttemptId = null
                    isUserActionDeferred = false
                    _state.value = UpdateInstallState.Failed(
                        AppError.UnknownError(outcome.reason),
                        target.manifest,
                        target.apkFilePath
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Создаёт координатор и начинает слушать результаты системного
         * установщика. Обычный конструктор годится только для тестов.
         */
        fun create(
            scope: CoroutineScope,
            installPort: UpdateInstallPort,
            apkExists: (String) -> Boolean = { File(it).exists() },
            nextAttemptId: () -> Long = defaultAttemptIdGenerator()
        ): UpdateInstallCoordinator {
            val coordinator = UpdateInstallCoordinator(installPort, apkExists, nextAttemptId)
            scope.launch { installPort.callbacks.collect { coordinator.onInstallCallback(it) } }
            return coordinator
        }
    }
}

/**
 * Идентификаторы попыток не должны повторяться между перезапусками процесса:
 * тот же `requestCode` PendingIntent иначе принял бы чужой callback за активную
 * попытку.
 */
private fun defaultAttemptIdGenerator(): () -> Long {
    val counter = AtomicLong(System.currentTimeMillis())
    return { counter.incrementAndGet() }
}
