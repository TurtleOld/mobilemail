package com.mobilemail.data.jmap

import java.net.ConnectException
import java.net.UnknownHostException

/**
 * Отправка могла быть принята сервером, но подтверждение получить не удалось.
 *
 * Возникает, когда запрос `EmailSubmission/set` уже отправлен, а ответ потерян или не
 * позволяет определить результат. Такой запрос нельзя повторять автоматически: сервер
 * мог создать отправку, и повторная попытка доставит письмо дважды.
 *
 * @param message описание ситуации для журнала и экрана очереди.
 * @param cause исходная ошибка транспорта или разбора ответа.
 */
class SendOutcomeUnknownException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Проверяет, что сбой произошёл до отправки запроса на сервер.
 *
 * [ConnectException] и [UnknownHostException] означают, что соединение не установлено,
 * поэтому сервер запрос точно не получил и его можно безопасно повторить.
 *
 * @param error ошибка транспортного уровня.
 * @return True, когда запрос достоверно не ушёл на сервер.
 */
internal fun isSendDefinitelyNotDispatched(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is ConnectException || current is UnknownHostException) return true
        current = current.cause
    }
    return false
}
