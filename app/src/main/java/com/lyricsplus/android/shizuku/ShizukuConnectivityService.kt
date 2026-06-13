package com.lyricsplus.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.lyricsplus.android.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuConnectivityService {
    private const val BIND_TIMEOUT_MS = 10_000L
    private val mutex = Mutex()
    private var cachedService: IPrivilegedConnectivityService? = null

    suspend fun setXmsfNetworkingEnabled(enabled: Boolean): Boolean {
        return mutex.withLock {
            val service = cachedService?.takeIf { it.asBinder().pingBinder() } ?: bindService()
            service.setXmsfNetworkingEnabled(enabled)
        }
    }

    private suspend fun bindService(): IPrivilegedConnectivityService {
        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        if (service == null) {
                            continuation.resumeWithException(IllegalStateException("Shizuku 服务 Binder 为空"))
                            return
                        }
                        val privileged = IPrivilegedConnectivityService.Stub.asInterface(service)
                        cachedService = privileged
                        continuation.resume(privileged)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        cachedService = null
                    }
                }
                val args = Shizuku.UserServiceArgs(
                    ComponentName(BuildConfig.APPLICATION_ID, PrivilegedConnectivityService::class.java.name)
                )
                    .daemon(true)
                    .processNameSuffix("connectivity")
                    .debuggable(BuildConfig.DEBUG)
                    .version(1)

                try {
                    Shizuku.bindUserService(args, connection)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }

                continuation.invokeOnCancellation {
                    runCatching { Shizuku.unbindUserService(args, connection, true) }
                }
            }
        } ?: throw IllegalStateException("绑定 Shizuku 网络服务超时")
    }
}
