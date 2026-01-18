package com.mobilemail.data.error

import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper as UiErrorMapper

object ErrorMapper {
    fun mapException(exception: Throwable): AppError {
        return UiErrorMapper.mapException(exception)
    }
}
