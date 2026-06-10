package com.lyricsplus.android.shizuku

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object XmsfNetworkHelper {
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"

    suspend fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
                requireShizukuPermissionGranted {
                    if (ShizukuConnectivityService.setXmsfNetworkingEnabled(enabled)) {
                        true
                    } else {
                        ShizukuNetworkHook.setPackageNetworkingEnabled(uid, enabled)
                        true
                    }
                }
            }.getOrDefault(false)
        }
}
