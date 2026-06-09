package com.mobilemail.ui.security

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.security.PinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PinLockUiState(
    val pin: String = "",
    val error: String? = null,
    val failedAttempts: Int = 0,
    val remainingLockoutMillis: Long = 0L,
    val isUnlocked: Boolean = false,
    val shouldLogout: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val showBiometricPrompt: Boolean = false
)

class PinLockViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val pinManager = PinManager(application)
    private val biometricManager = BiometricManager.from(application)

    private val _uiState = MutableStateFlow(PinLockUiState())
    val uiState: StateFlow<PinLockUiState> = _uiState.asStateFlow()

    init {
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        // Отпечаток — основной способ входа: запрашиваем его сразу,
        // если устройство поддерживает биометрию. PIN остаётся как fallback.
        _uiState.update {
            it.copy(
                failedAttempts = pinManager.getFailedAttempts(),
                remainingLockoutMillis = pinManager.getRemainingLockoutMillis(),
                isBiometricEnabled = canAuthenticate,
                isBiometricAvailable = canAuthenticate,
                showBiometricPrompt = canAuthenticate
            )
        }

        if (pinManager.hasExceededMaxAttempts()) {
            handleMaxAttemptsExceeded()
        }
    }

    fun onPinChanged(pin: String) {
        if (!canAttemptPin()) return
        if (pin.length <= PinManager.PIN_LENGTH && pin.all { it.isDigit() }) {
            _uiState.update {
                it.copy(pin = pin, error = null)
            }

            if (pin.length == PinManager.PIN_LENGTH) {
                verifyPin(pin)
            }
        }
    }

    fun onPinDigitEntered(digit: String) {
        if (!canAttemptPin()) return
        val currentPin = _uiState.value.pin
        if (currentPin.length < PinManager.PIN_LENGTH) {
            val newPin = currentPin + digit
            _uiState.update {
                it.copy(pin = newPin, error = null)
            }

            if (newPin.length == PinManager.PIN_LENGTH) {
                verifyPin(newPin)
            }
        }
    }

    fun onPinDigitDeleted() {
        val currentPin = _uiState.value.pin
        if (currentPin.isNotEmpty()) {
            _uiState.update {
                it.copy(pin = currentPin.dropLast(1), error = null)
            }
        }
    }

    private fun verifyPin(pin: String) {
        if (!canAttemptPin()) return
        if (pinManager.verifyPin(pin)) {
            pinManager.resetFailedAttempts()
            _uiState.update {
                it.copy(isUnlocked = true, error = null)
            }
        } else {
            val attempts = pinManager.incrementFailedAttempts()
            val remainingAttempts = PinManager.MAX_FAILED_ATTEMPTS - attempts
            val remainingLockoutMillis = pinManager.getRemainingLockoutMillis()

            if (pinManager.hasExceededMaxAttempts()) {
                handleMaxAttemptsExceeded()
            } else {
                val delayText = formatDelay(remainingLockoutMillis)
                _uiState.update {
                    it.copy(
                        pin = "",
                        error = "Неверный PIN-код. Повторите через $delayText. Осталось попыток: $remainingAttempts",
                        failedAttempts = attempts,
                        remainingLockoutMillis = remainingLockoutMillis
                    )
                }
            }
        }
    }

    private fun canAttemptPin(): Boolean {
        val remainingLockoutMillis = pinManager.getRemainingLockoutMillis()
        if (remainingLockoutMillis <= 0L) {
            _uiState.update { it.copy(remainingLockoutMillis = 0L) }
            return true
        }

        _uiState.update {
            it.copy(
                pin = "",
                remainingLockoutMillis = remainingLockoutMillis,
                error = "Слишком много быстрых попыток. Повторите через ${formatDelay(remainingLockoutMillis)}."
            )
        }
        return false
    }

    private fun handleMaxAttemptsExceeded() {
        pinManager.clearPin()
        _uiState.update {
            it.copy(
                shouldLogout = true,
                error = "Превышено количество попыток. PIN-код сброшен."
            )
        }
    }

    fun onBiometricSuccess() {
        pinManager.resetFailedAttempts()
        _uiState.update {
            it.copy(
                isUnlocked = true,
                showBiometricPrompt = false,
                remainingLockoutMillis = 0L
            )
        }
    }

    fun onBiometricError(errorMessage: String?) {
        _uiState.update {
            it.copy(
                showBiometricPrompt = false,
                error = errorMessage
            )
        }
    }

    fun onBiometricDismissed() {
        _uiState.update {
            it.copy(showBiometricPrompt = false)
        }
    }

    fun requestBiometric() {
        if (_uiState.value.isBiometricEnabled) {
            _uiState.update {
                it.copy(showBiometricPrompt = true)
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(error = null)
        }
    }

    private fun formatDelay(delayMillis: Long): String {
        val seconds = ((delayMillis + 999L) / 1_000L).coerceAtLeast(1L)
        return if (seconds < 60L) {
            "$seconds сек."
        } else {
            val minutes = (seconds + 59L) / 60L
            "$minutes мин."
        }
    }
}

class PinLockViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PinLockViewModel(application) as T
    }
}
