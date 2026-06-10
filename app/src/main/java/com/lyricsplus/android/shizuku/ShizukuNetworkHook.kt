package com.lyricsplus.android.shizuku

import android.content.Context
import android.os.IBinder
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuNetworkHook {
    private const val TAG = "ShizukuNetworkHook"
    private const val OEM_DENY_CHAIN = 9

    private data class Backend(
        val serviceName: String,
        val stubClassName: String,
        val label: String
    )

    private val backends = listOf(
        Backend(
            serviceName = Context.CONNECTIVITY_SERVICE,
            stubClassName = "android.net.IConnectivityManager\$Stub",
            label = "ConnectivityManager"
        ),
        Backend(
            serviceName = "network_management",
            stubClassName = "android.os.INetworkManagementService\$Stub",
            label = "NetworkManagementService"
        )
    )

    private val cache = ConcurrentHashMap<String, Any>()

    fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean) {
        val rule = if (enabled) 0 else 2
        val failures = mutableListOf<String>()

        for (backend in backends) {
            try {
                val service = getHookedService(backend)
                if (!enabled) {
                    runCatching {
                        callMethod(service, listOf("setFirewallChainEnabled"), OEM_DENY_CHAIN, true)
                    }.onFailure {
                        Log.w(TAG, "Firewall chain enable unavailable on ${backend.label}; trying uid rule directly", it)
                    }
                }
                callMethod(
                    service,
                    listOf("setUidFirewallRule", "setFirewallUidRule"),
                    OEM_DENY_CHAIN,
                    uid,
                    rule
                )
                Log.d(TAG, "Set network enabled=$enabled for uid=$uid via ${backend.label}")
                return
            } catch (error: Throwable) {
                failures += "${backend.label}: ${error.message}"
                Log.w(TAG, "Failed ${backend.label} network toggle", error)
            }
        }

        throw IllegalStateException("No compatible Shizuku network backend. ${failures.joinToString(" | ")}")
    }

    private fun getHookedService(backend: Backend): Any {
        cache[backend.stubClassName]?.let { return it }

        return synchronized(this) {
            cache[backend.stubClassName]?.let { return@synchronized it }

            val originalBinder = SystemServiceHelper.getSystemService(backend.serviceName)
                ?: throw IllegalStateException("${backend.label} binder is null")
            val wrapper: IBinder = ShizukuBinderWrapper(originalBinder)
            val stubClass = Class.forName(backend.stubClassName)
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val hooked = asInterface.invoke(null, wrapper)
                ?: throw IllegalStateException("${backend.label}.Stub.asInterface returned null")

            cache[backend.stubClassName] = hooked
            hooked
        }
    }

    private fun callMethod(target: Any, names: List<String>, vararg args: Any): String {
        val methods = target.javaClass.methods.filter {
            it.name in names && it.parameterCount == args.size
        }
        if (methods.isEmpty()) {
            throw NoSuchMethodException("Missing ${names.joinToString()}(${args.size}) on ${target.javaClass.name}")
        }

        var lastError: Throwable? = null
        for (method in methods) {
            try {
                method.isAccessible = true
                method.invoke(target, *adaptArgs(method.parameterTypes, args))
                return method.name
            } catch (error: InvocationTargetException) {
                lastError = error.targetException ?: error
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("No overload accepted ${names.joinToString()}")
    }

    private fun adaptArgs(parameterTypes: Array<Class<*>>, args: Array<out Any>): Array<Any> =
        Array(args.size) { index ->
            val expected = parameterTypes[index]
            val arg = args[index]
            when (expected) {
                Int::class.javaPrimitiveType -> when (arg) {
                    is Int -> arg
                    is Number -> arg.toInt()
                    is Boolean -> if (arg) 1 else 0
                    else -> arg
                }
                Boolean::class.javaPrimitiveType -> when (arg) {
                    is Boolean -> arg
                    is Number -> arg.toInt() != 0
                    else -> arg
                }
                else -> arg
            }
        }
}
