package com.mobilemail.ui.common

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun isExpandedWindowWidth(): Boolean {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    return adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun isMediumOrExpandedWindowWidth(): Boolean {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    return widthClass == WindowWidthSizeClass.MEDIUM || widthClass == WindowWidthSizeClass.EXPANDED
}
