package com.lyricsplus.android.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku

class ShizukuUnavailableException(cause: Throwable? = null) :
    Exception("Shizuku 未运行或权限被拒绝。", cause)

suspend fun <T> requireShizukuPermissionGranted(action: suspend () -> T): T {
    callbackFlow {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.pingBinder()) {
                trySend(Unit)
            } else {
                close(ShizukuUnavailableException())
            }
            awaitClose()
            return@callbackFlow
        }

        val requestCode = (Int.MIN_VALUE..Int.MAX_VALUE).random()
        val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code != requestCode) return@OnRequestPermissionResultListener
            if (result == PackageManager.PERMISSION_GRANTED) {
                trySend(Unit)
            } else {
                close(ShizukuUnavailableException())
            }
        }

        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(requestCode)
        awaitClose { Shizuku.removeRequestPermissionResultListener(listener) }
    }.catch {
        throw ShizukuUnavailableException(it)
    }.first()

    return action()
}

fun isShizukuGranted(): Boolean =
    runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
