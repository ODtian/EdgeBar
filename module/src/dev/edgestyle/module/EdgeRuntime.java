package dev.edgestyle.module;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;

final class EdgeRuntime {
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> current = new WeakReference<>(null);

    static Activity activity() {
        Activity activity = current.get();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed() ? activity : null;
    }

    static void bind(Context context) {
        Activity owner = NativeToolbar.ownerContext(context);
        if (owner != null && owner.getClass().getName().contains("ChromeTabbedActivity")) current = new WeakReference<>(owner);
    }

    static void resumed(Activity activity) {
        if (!"com.microsoft.emmx".equals(activity.getPackageName())) return;
        String name = activity.getClass().getName();
        if (name.contains("ChromeTabbedActivity") || name.equals("com.microsoft.ruby.Main")) current = new WeakReference<>(activity);
        route(activity.getIntent());
    }

    static void route(Intent intent) {
        if (intent == null || !"apply".equals(intent.getStringExtra(MainActivity.COMMAND))) return;
        intent.removeExtra(MainActivity.COMMAND);
        MAIN.post(EdgeEntry::reload);
    }
}
