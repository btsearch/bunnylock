package dev.ririxi.bunnylock.shizuku

import android.content.Context
import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal object HiddenApi {
    init {
        // Lifted on first use rather than at app start, so the platform's checks stay on until the app needs a hidden API.
        HiddenApiBypass.setHiddenApiExemptions("L")
    }

    fun activityManager(): Any = service("android.app.IActivityManager", Context.ACTIVITY_SERVICE)

    fun packageManager(): Any = service("android.content.pm.IPackageManager", "package")

    fun method(
        className: String,
        name: String,
        parameterCount: Int,
    ): Method =
        Class
            .forName(className)
            .methods
            .firstOrNull { it.name == name && it.parameterCount == parameterCount }
            ?: throw NoSuchMethodException("$className.$name")

    private fun service(
        interfaceName: String,
        serviceName: String,
    ): Any {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(serviceName))
        val asInterface = Class.forName("$interfaceName\$Stub").getMethod("asInterface", IBinder::class.java)
        return checkNotNull(asInterface.invoke(null, binder)) { "$interfaceName has no service behind it" }
    }
}

internal fun Method.invokeUnwrapped(
    target: Any,
    vararg args: Any?,
): Any? =
    try {
        invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
