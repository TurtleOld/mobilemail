package com.mobilemail.ui.settings

import com.mobilemail.data.error.ErrorMapper
import com.mobilemail.domain.port.UpdateCheckPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Координирует ручную проверку обновлений в настройках.
 *
 * Публичная граница — команда [checkForUpdate] и наблюдаемое [state].
 * Одновременно выполняется не более одной проверки: повторный вызов,
 * пока предыдущая проверка не завершилась, ничего не делает.
 */
class UpdateCheckCoordinator(
    private val port: UpdateCheckPort
) {
    private val _state = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val state: StateFlow<UpdateCheckUiState> = _state.asStateFlow()

    private val checkMutex = Mutex()

    suspend fun checkForUpdate(currentVersionCode: Int) {
        if (!checkMutex.tryLock()) return
        try {
            _state.value = UpdateCheckUiState.Checking
            _state.value = runCatching { port.checkForUpdate(currentVersionCode) }
                .fold(
                    onSuccess = { it.toUiState() },
                    onFailure = { UpdateCheckUiState.Failed(ErrorMapper.mapException(it)) }
                )
        } finally {
            checkMutex.unlock()
        }
    }
}
