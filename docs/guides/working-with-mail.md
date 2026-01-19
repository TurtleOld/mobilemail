# Работа с почтой

Руководство по работе с почтой в MobileMail.

## Получение списка писем

```kotlin
val queryResult = jmapClient.queryEmails(
    mailboxId = "inbox",
    position = 0,
    limit = 50
)

val emails = jmapClient.getEmails(ids = queryResult.ids)
```

## Чтение письма

```kotlin
val email = emails.first()
val subject = email.subject
val from = email.from
val body = email.bodyValues?.values?.first()?.value
```

## Отметка как прочитанное

```kotlin
jmapClient.updateEmailKeywords(
    emailId = email.id,
    keywords = mapOf("$seen" to true)
)
```

## Удаление письма

```kotlin
jmapClient.deleteEmail(emailId = email.id)
```

## Перемещение письма

```kotlin
jmapClient.moveEmail(
    emailId = email.id,
    fromMailboxId = "inbox",
    toMailboxId = "archive"
)
```

## Работа с вложениями

```kotlin
val attachmentData = jmapClient.downloadAttachment(
    blobId = attachment.id,
    accountId = accountId
)

// Сохранение вложения
saveAttachmentToFile(attachmentData, attachment.filename)
```
