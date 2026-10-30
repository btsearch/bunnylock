package dev.ririxi.bunnylock.shizuku;

import android.os.IBinder;

/**
 * Home for facade lookups that need a {@code null} receiver, which Kotlin is reluctant to pass on Java methods like
 * {@code IActivityManager.Stub.asInterface} (a SDK-hidden class, so no stub exists and it stays on reflection).
 */
final class JavaBridge {
    private JavaBridge() {}

    static Object iActivityManager(IBinder binder) throws Exception {
        Class<?> stub = Class.forName("android.app.IActivityManager$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    static Object iPackageManager(IBinder binder) throws Exception {
        Class<?> stub = Class.forName("android.content.pm.IPackageManager$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
}
