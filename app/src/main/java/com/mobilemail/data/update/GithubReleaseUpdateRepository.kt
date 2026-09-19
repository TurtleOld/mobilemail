package com.mobilemail.data.update

import com.mobilemail.data.error.ErrorMapper
import com.mobilemail.domain.model.UpdateCheckResult
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.UpdateCheckPort
import com.mobilemail.ui.common.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val METADATA_ASSET_NAME = "update-metadata.json"
private const val DEFAULT_MAX_METADATA_BYTES = 8L * 1024
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_FORBIDDEN = 403

/**
 * Клиент ручной проверки обновлений через GitHub Releases.
 *
 * Проверяет только стабильные (не draft, не prerelease) релизы репозитория
 * [repoOwnerAndName], сверяет метаданные с фактическими assets релиза и
 * никогда не скачивает сам APK — только его метаданные.
 *
 * Самодостаточно потокобезопасен: параллельные вызовы [checkForUpdate] не
 * требуют внешней сериализации, ETag-кеш защищён собственным [cacheMutex].
 */
class GithubReleaseUpdateRepository(
    private val httpClient: OkHttpClient,
    private val repoOwnerAndName: String,
    private val expectedApplicationId: String,
    private val deviceSdkInt: Int,
    private val apiBaseUrl: String = "https://api.github.com",
    private val maxMetadataBytes: Long = DEFAULT_MAX_METADATA_BYTES
) : UpdateCheckPort {

    private val cacheMutex = Mutex()
    private var cachedETag: String? = null
    private var cachedReleases: List<GithubRelease>? = null

    override suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        val releases = when (val outcome = fetchReleases()) {
            is FetchOutcome.Success -> outcome.releases
            FetchOutcome.RateLimited -> return@withContext UpdateCheckResult.Failed(rateLimitedError())
            is FetchOutcome.Error -> return@withContext UpdateCheckResult.Failed(ErrorMapper.mapException(outcome.throwable))
        }

        val candidate = releases.firstOrNull { !it.draft && !it.prerelease }
            ?: return@withContext UpdateCheckResult.ReleaseNotReady

        resolveCandidate(candidate, currentVersionCode)
    }

    private fun resolveCandidate(candidate: GithubRelease, currentVersionCode: Int): UpdateCheckResult {
        val metadataAsset = candidate.assets.firstOrNull { it.name == METADATA_ASSET_NAME }
            ?: return UpdateCheckResult.ReleaseNotReady

        val metadata = when (val outcome = fetchMetadata(metadataAsset.downloadUrl)) {
            is MetadataOutcome.Success -> outcome.metadata
            is MetadataOutcome.Error -> return UpdateCheckResult.Failed(ErrorMapper.mapException(outcome.throwable))
        }

        val check = checkConsistency(candidate, metadata)
        val apkAsset = (check as? ConsistencyCheck.Consistent)?.apkAsset ?: return UpdateCheckResult.ReleaseNotReady

        return if (metadata.versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(
                versionName = metadata.versionName,
                apkSizeBytes = metadata.apkSizeBytes,
                manifest = UpdateReleaseManifest(
                    versionName = metadata.versionName,
                    versionCode = metadata.versionCode,
                    applicationId = metadata.applicationId,
                    minSdk = metadata.minSdk,
                    apkDownloadUrl = apkAsset.downloadUrl,
                    apkSizeBytes = metadata.apkSizeBytes,
                    apkSha256 = metadata.apkSha256
                )
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun checkConsistency(candidate: GithubRelease, metadata: UpdateMetadata): ConsistencyCheck {
        val expectedVersionCode = UpdateVersionCode.encodeFromTag(candidate.tagName)
        val apkAsset = candidate.assets.firstOrNull { it.name == metadata.apkAssetName }

        return when {
            metadata.applicationId != expectedApplicationId -> ConsistencyCheck.ApplicationIdMismatch
            metadata.minSdk > deviceSdkInt -> ConsistencyCheck.MinSdkTooHigh
            expectedVersionCode == null -> ConsistencyCheck.UnparseableReleaseTag
            expectedVersionCode != metadata.versionCode -> ConsistencyCheck.VersionCodeTagMismatch
            apkAsset == null -> ConsistencyCheck.ApkAssetMissing
            apkAsset.sizeBytes != metadata.apkSizeBytes -> ConsistencyCheck.ApkSizeMismatch
            else -> ConsistencyCheck.Consistent(apkAsset)
        }
    }

    /**
     * Причина, по которой релиз признан непригодным к предложению, — вместо
     * плоского &&-выражения из 5 условий, где по логу было не видно, какое
     * именно условие сорвалось в проде (тег, SDK, размер APK или что-то ещё).
     */
    private sealed class ConsistencyCheck {
        data class Consistent(val apkAsset: GithubReleaseAsset) : ConsistencyCheck()
        data object ApplicationIdMismatch : ConsistencyCheck()
        data object MinSdkTooHigh : ConsistencyCheck()
        data object UnparseableReleaseTag : ConsistencyCheck()
        data object VersionCodeTagMismatch : ConsistencyCheck()
        data object ApkAssetMissing : ConsistencyCheck()
        data object ApkSizeMismatch : ConsistencyCheck()
    }

    /**
     * Читает и обновляет [cachedETag]/[cachedReleases] под [cacheMutex], а не под
     * `@Volatile`: между чтением ETag для условного запроса и записью ответа
     * не должен вклиниться параллельный вызов — иначе гонка перезаписывает кеш
     * несогласованной парой (ETag от одного ответа, releases от другого).
     * Раньше эта сериализация держалась на checkMutex вызывающего координатора,
     * а не была гарантирована самим репозиторием.
     */
    private suspend fun fetchReleases(): FetchOutcome = cacheMutex.withLock {
        val requestBuilder = Request.Builder()
            .url("$apiBaseUrl/repos/$repoOwnerAndName/releases")
            .header("Accept", "application/vnd.github+json")
        cachedETag?.let { requestBuilder.header("If-None-Match", it) }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.code == HTTP_NOT_MODIFIED -> notModifiedOutcome()
                    isRateLimited(response) -> FetchOutcome.RateLimited
                    !response.isSuccessful -> FetchOutcome.Error(IOException("GitHub API вернул ${response.code}"))
                    else -> successOutcome(response.body, response.header("ETag"))
                }
            }
        } catch (e: IOException) {
            FetchOutcome.Error(e)
        }
    }

    private fun notModifiedOutcome(): FetchOutcome {
        val cached = cachedReleases
        return if (cached != null) FetchOutcome.Success(cached) else FetchOutcome.Error(IOException("304 без локального кэша"))
    }

    private fun successOutcome(body: ResponseBody?, etag: String?): FetchOutcome {
        val json = body?.string().orEmpty()
        return try {
            val releases = GithubReleaseParser.parseReleases(json)
            cachedETag = etag
            cachedReleases = releases
            FetchOutcome.Success(releases)
        } catch (e: UpdateMetadataException) {
            FetchOutcome.Error(e)
        }
    }

    private fun isRateLimited(response: Response): Boolean =
        (response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS) &&
            response.header("X-RateLimit-Remaining") == "0"

    private fun fetchMetadata(url: String): MetadataOutcome {
        val request = Request.Builder().url(url).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return MetadataOutcome.Error(IOException("Не удалось загрузить метаданные обновления: ${response.code}"))
                }
                val body = response.body ?: return MetadataOutcome.Error(IOException("Пустой ответ метаданных обновления"))
                val bytes = body.byteStream().readLimited(maxMetadataBytes)
                    ?: return MetadataOutcome.Error(IOException("Метаданные обновления превышают допустимый размер"))
                MetadataOutcome.Success(UpdateMetadata.parse(bytes.toString(Charsets.UTF_8)))
            }
        } catch (e: IOException) {
            MetadataOutcome.Error(e)
        } catch (e: UpdateMetadataException) {
            MetadataOutcome.Error(e)
        }
    }

    private fun rateLimitedError() = AppError.ServerError(
        errorMessage = "Превышен лимит запросов к GitHub. Повторите попытку позже.",
        statusCode = HTTP_TOO_MANY_REQUESTS
    )

    private sealed class FetchOutcome {
        data class Success(val releases: List<GithubRelease>) : FetchOutcome()
        data object RateLimited : FetchOutcome()
        data class Error(val throwable: Throwable) : FetchOutcome()
    }

    private sealed class MetadataOutcome {
        data class Success(val metadata: UpdateMetadata) : MetadataOutcome()
        data class Error(val throwable: Throwable) : MetadataOutcome()
    }
}

private fun java.io.InputStream.readLimited(limit: Long): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    use { stream ->
        while (true) {
            val read = stream.read(chunk)
            if (read == -1) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
    }
    return buffer.toByteArray()
}
