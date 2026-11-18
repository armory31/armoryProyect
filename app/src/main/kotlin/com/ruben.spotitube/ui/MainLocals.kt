package com.ruben.spotitube.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.playback.DownloadUtil
import com.ruben.spotitube.playback.PlayerConnection
import com.ruben.spotitube.sync.LibraryStateHolder
import com.ruben.spotitube.sync.SyncManager
import com.ruben.spotitube.utils.SyncUtils

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncManager =
    staticCompositionLocalOf<SyncManager> { error("No SyncManager provided") }
val LocalLibraryStateHolder =
    staticCompositionLocalOf<LibraryStateHolder> { error("No LibraryStateHolder provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
