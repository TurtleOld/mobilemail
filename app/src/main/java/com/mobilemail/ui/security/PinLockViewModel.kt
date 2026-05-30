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
        if (pinManager.verifyPin(pin)) {
            pinManager.resetFailedAttempts()
            _uiState.update {
                it.copy(isUnlocked = true, error = null)
            }
        } else {
            val attempts = pinManager.incrementFailedAttempts()
            val remainingAttempts = PinManager.MAX_FAILED_ATTEMPTS - attempts

            if (pinManager.hasExceededMaxAttempts()) {
                handleMaxAttemptsExceeded()
            } else {
                _uiState.update {
                    it.copy(
                        pin = "",
                        error = "Неверный PIN-код. Осталось попыток: $remainingAttempts",
                        failedAttempts = attempts
                    )
                }
            }
        }
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
            it.copy(isUnlocked = true, showBiometricPrompt = false)
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
}

class PinLockViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PinLockViewModel(application) as T
    }
}
