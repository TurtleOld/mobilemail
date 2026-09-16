package com.mobilemail.ui.settings

import com.mobilemail.data.error.ErrorMapper
import com.mobilemail.domain.port.UpdateCheckPort
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Координирует и автоматическую, и ручную проверку обновлений.
 *
 * Живёт один экземпляр на процесс (см. [UpdateCheckCoordinatorHolder]), поэтому
 * служит единой точкой правды и для тихой автопроверки при запуске, и для
 * ручной команды из настроек: обе используют одно и то же [state] и один
 * и тот же [checkMutex], так что параллельных запросов не возникает.
 *
 * Публичная граница — команды [checkOnStartupOnce] и [checkForUpdate],
 * команда [dismissOffer] и наблюдаемые [state] и [isOfferDismissed].
 */
class UpdateCheckCoordinator(
    private val port: UpdateCheckPort
) {
    private val _state = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val state: StateFlow<UpdateCheckUiState> = _state.asStateFlow()

    private val _isOfferDismissed = MutableStateFlow(false)
    val isOfferDismissed: StateFlow<Boolean> = _isOfferDismissed.asStateFlow()

    private val checkMutex = Mutex()
    private val hasStartupCheckRun = AtomicBoolean(false)

    /**
     * Автопроверка при пользовательском открытии приложения.
     *
     * Выполняется не более одного раза за процесс и не сообщает об ошибке
     * пользователю: сетевой сбой автопроверки тихо оставляет [state] в [UpdateCheckUiState.Idle]
     * или предыдущем значении, а не показывает [UpdateCheckUiState.Failed] поверх почты.
     */
    suspend fun checkOnStartupOnce(currentVersionCode: Int) {
        if (!hasStartupCheckRun.compareAndSet(false, true)) return
        runCheck(currentVersionCode, silent = true)
    }

    /**
     * Ручная команда «Проверить обновления» из настроек.
     *
     * Если проверка уже выполняется (мьютекс занят), команда ничего не делает —
     * в частности не снимает «Позже», чтобы клик во время уже идущей проверки
     * не отменял отложение без получения нового результата.
     */
    suspend fun checkForUpdate(currentVersionCode: Int) {
        val didRun = runCheck(currentVersionCode, silent = false)
        if (didRun) {
            _isOfferDismissed.value = false
        }
    }

    /** «Позже»: скрывает предложение обновления до конца текущего процесса. */
    fun dismissOffer() {
        _isOfferDismissed.value = true
    }

    private suspend fun runCheck(currentVersionCode: Int, silent: Boolean): Boolean {
        if (!checkMutex.tryLock()) return false
        try {
            _state.value = UpdateCheckUiState.Checking
            val result = runCatching { port.checkForUpdate(currentVersionCode) }
                .fold(
                    onSuccess = { it.toUiState() },
                    onFailure = { UpdateCheckUiState.Failed(ErrorMapper.mapException(it)) }
                )
            if (silent && result is UpdateCheckUiState.Failed) {
                _state.value = UpdateCheckUiState.Idle
            } else {
                _state.value = result
            }
            return true
        } finally {
            checkMutex.unlock()
        }
    }
}
