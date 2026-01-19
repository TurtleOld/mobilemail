# Примеры работы с JMAP

Практические примеры использования JMAP клиента.

## Получение почтовых ящиков

```kotlin
suspend fun loadMailboxes(): List<JmapMailbox> {
    return jmapClient.getMailboxes()
}
```

## Загрузка писем из папки

```kotlin
suspend fun loadEmailsFromFolder(
    mailboxId: String,
    limit: Int = 50
): List<JmapEmail> {
    val queryResult = jmapClient.queryEmails(
        mailboxId = mailboxId,
        limit = limit
    )
    
    return jmapClient.getEmails(ids = queryResult.ids)
}
```

## Поиск писем

```kotlin
suspend fun searchEmails(query: String): List<JmapEmail> {
    val queryResult = jmapClient.queryEmails(
        searchText = query,
        limit = 100
    )
    
    return jmapClient.getEmails(ids = queryResult.ids)
}
```

## Пагинация

```kotlin
suspend fun loadMoreEmails(
    mailboxId: String,
    position: Int,
    limit: Int = 50
): List<JmapEmail> {
    val queryResult = jmapClient.queryEmails(
        mailboxId = mailboxId,
        position = position,
        limit = limit
    )
    
    return jmapClient.getEmails(ids = queryResult.ids)
}
```

## Загрузка вложения

```kotlin
suspend fun downloadAttachment(
    blobId: String,
    filename: String
): File {
    val data = jmapClient.downloadAttachment(blobId = blobId)
    val file = File(context.getExternalFilesDir(null), filename)
    file.writeBytes(data)
    return file
}
```
