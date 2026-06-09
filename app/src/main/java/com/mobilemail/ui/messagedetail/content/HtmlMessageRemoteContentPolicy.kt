package com.mobilemail.ui.messagedetail.content

internal object HtmlMessageRemoteContentPolicy {
    val remoteImageDomainAllowlist: Set<String> = emptySet()

    fun settingsFor(blockRemoteLoads: Boolean): WebViewRemoteContentSettings {
        return when (blockRemoteLoads) {
            true -> WebViewRemoteContentSettings(
                mode = RemoteImageMode.BlockAll,
                blockNetworkImages = true,
                blockNetworkLoads = true
            )
            false -> WebViewRemoteContentSettings(
                mode = RemoteImageMode.AllowAllAfterUserConsent,
                blockNetworkImages = false,
                blockNetworkLoads = false
            )
        }
    }
}

internal data class WebViewRemoteContentSettings(
    val mode: RemoteImageMode,
    val blockNetworkImages: Boolean,
    val blockNetworkLoads: Boolean,
)

internal enum class RemoteImageMode {
    BlockAll,
    AllowAllAfterUserConsent,
}
