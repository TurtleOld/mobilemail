package com.mobilemail.ui.common

sealed class NotificationState {
    object None : NotificationState()
    data class Snackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        val duration: SnackbarDuration = SnackbarDuration.Short
    ) : NotificationState()
    
    data class Alert(
        val title: String,
        val message: String,
        val confirmLabel: String = "OK",
        val cancelLabel: String? = null,
        val onConfirm: (() -> Unit)? = null,
        val onCancel: (() -> Unit)? = null
    ) : NotificationState()
}

enum class SnackbarDuration {
    Short,
    Long,
    Indefinite
}
