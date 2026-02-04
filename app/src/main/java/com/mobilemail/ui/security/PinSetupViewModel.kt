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

data class PinSetupUiState(
    val isPinEnabled: Boolean = false,
    val isPinSaved: Boolean = false, // PIN уже сохранён в хранилище
    val isBiometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val pin: String = "",
    val confirmPin: String = "",
    val isConfirmStep: Boolean = false,
    val error: String? = null,
    val showSavedMessage: Boolean = false, // Для показа snackbar
    val showBiometricPrompt: Boolean = false
)

class PinSetupViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val pinManager = PinManager(application)
    private val biometricManager = BiometricManager.from(application)

    private val _uiState = MutableStateFlow(PinSetupUiState())
    val uiState: StateFlow<PinSetupUiState> = _uiState.asStateFlow()

    init {
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        val pinEnabled = pinManager.isPinEnabled()
        _uiState.update {
            it.copy(
                isPinEnabled = pinEnabled,
                isPinSaved = pinEnabled, // Если PIN включён, значит он уже сохранён
                isBiometricEnabled = pinManager.isBiometricEnabled(),
                isBiometricAvailable = canAuthenticate
            )
        }
    }

    fun onPinEnabledChanged(enabled: Boolean) {
        if (!enabled) {
            pinManager.clearPin()
            _uiState.update {
                it.copy(
                    isPinEnabled = false,
                    isPinSaved = false,
                    isBiometricEnabled = false,
                    pin = "",
                    confirmPin = "",
                    isConfirmStep = false,
                    error = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isPinEnabled = true,
                    pin = "",
                    confirmPin = "",
                    isConfirmStep = false,
                    error = null
                )
            }
        }
    }

    fun onPinChanged(pin: String) {
        if (pin.length <= PinManager.PIN_LENGTH && pin.all { it.isDigit() }) {
            _uiState.update {
                it.copy(pin = pin, error = null)
            }
        }
    }

    fun onConfirmPinChanged(confirmPin: String) {
        if (confirmPin.length <= PinManager.PIN_LENGTH && confirmPin.all { it.isDigit() }) {
            _uiState.update {
                it.copy(confirmPin = confirmPin, error = null)
            }
        }
    }

    fun onPinEntered() {
        val state = _uiState.value
        if (state.pin.length != PinManager.PIN_LENGTH) {
            _uiState.update {
                it.copy(error = "PIN-код должен содержать ${PinManager.PIN_LENGTH} цифр")
            }
            return
        }
        _uiState.update {
            it.copy(isConfirmStep = true, error = null)
        }
    }

    fun onConfirmPinEntered() {
        val state = _uiState.value
        if (state.confirmPin != state.pin) {
            _uiState.update {
                it.copy(
                    error = "PIN-коды не совпадают",
                    confirmPin = "",
                    isConfirmStep = false,
                    pin = ""
                )
            }
            return
        }
        pinManager.savePin(state.pin)
        _uiState.update {
            it.copy(
                isPinSaved = true,
                showSavedMessage = true,
                error = null,
                pin = "",
                confirmPin = "",
                isConfirmStep = false
            )
        }
    }

    fun onBiometricToggleRequested(enabled: Boolean) {
        android.util.Log.d("PinSetupViewModel", "onBiometricToggleRequested: enabled=$enabled")
        if (enabled) {
            // Запросить биометрию для подтверждения
            android.util.Log.d("PinSetupViewModel", "Setting showBiometricPrompt=true")
            _uiState.update {
                it.copy(showBiometricPrompt = true)
            }
        } else {
            // Отключение без подтверждения
            pinManager.setBiometricEnabled(false)
            _uiState.update {
                it.copy(isBiometricEnabled = false)
            }
        }
    }

    fun onBiometricSuccess() {
        pinManager.setBiometricEnabled(true)
        _uiState.update {
            it.copy(
                isBiometricEnabled = true,
                showBiometricPrompt = false
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

    fun resetSavedMessage() {
        _uiState.update {
            it.copy(showSavedMessage = false)
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(error = null)
        }
    }

    fun goBackToPin() {
        _uiState.update {
            it.copy(
                isConfirmStep = false,
                confirmPin = "",
                pin = ""
            )
        }
    }
}

class PinSetupViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PinSetupViewModel(application) as T
    }
}
