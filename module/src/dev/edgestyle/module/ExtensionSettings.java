package dev.edgestyle.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedInterface.HookHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;
import dev.edgestyle.module.NativeDex.FieldRef;
import dev.edgestyle.module.NativeDex.MethodRef;
import dev.edgestyle.module.NativeDex.Type;

/** Adds an extension installation screen to Edge's own settings navigation. */
final class ExtensionSettings {
    private static final String PAGE_KEY = "dev.edgestyle.module.extension_settings";
    private static final String MAIN = "org.chromium.chrome.browser.edge_settings.EdgeMainSettingsV2";
    private static final String SHELL = "org.chromium.chrome.browser.about_settings.edge_settings.EdgeTermOfUseSettings";
    private static final String EDGE_ADDONS = "https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home";
    private static final String CHROME_STORE = "https://chromewebstore.google.com/category/extensions";
    private static final String PREFERENCE = "Landroidx/preference/Preference;";
    private static final String SCREEN = "Landroidx/preference/PreferenceScreen;";
    private static final String CONTEXT = "Landroid/content/Context;";
    private static final int TRANSITION_AUTO_BOOKMARK = 2, TAB_FROM_BROWSER_UI = 2;

    private final Method mainCreate, pageCreate, pageView, nativeView, fragmentCreate;
    private final Method getArguments, requireActivity, getContext, getScreen, newScreen, setScreen;
    private final Method addPreference, findPreference, getParent, getOrder, setOrder, setKey, setTitle, setPersistent,
        setFragment, getExtras, setClick, setIntent, setPageTitle, getTabCreator, createTab;
    private final Field manager, titleSupplier;
    private final Constructor<?> row, navigationParams;

    ExtensionSettings(NativeDex dex, ClassLoader loader) throws ReflectiveOperationException {
        Type main = dex.type(descriptor(MAIN));
        Type shell = dex.type(descriptor(SHELL));
        Type pref = dex.type(PREFERENCE);
        Type screen = dex.type(SCREEN);
        Type group = dex.type(screen.parent);
        Type fragment = dex.type(dex.type(shell.parent).parent);
        MethodRef preferences = fragment.method("preference creation", m -> m.signature("(Landroid/os/Bundle;Ljava/lang/String;)V") && Modifier.isAbstract(m.flags));
        mainCreate = main.method("main preferences", m -> m.name.equals(preferences.name) && m.signature.equals(preferences.signature)).reflect(loader);
        MethodRef pagePreferences = shell.method("settings page preferences", m -> m.name.equals(preferences.name) && m.signature.equals(preferences.signature));
        pageCreate = pagePreferences.reflect(loader);
        pageView = shell.load(loader).getDeclaredMethod("onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class);
        nativeView = fragment.load(loader).getDeclaredMethod("onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class);
        fragmentCreate = fragment.load(loader).getDeclaredMethod("onCreate", Bundle.class);
        Class<?> fragmentClass = fragment.load(loader);
        getArguments = fragmentClass.getMethod("getArguments");
        requireActivity = fragmentClass.getMethod("requireActivity");
        getContext = fragmentClass.getMethod("getContext");
        MethodRef screenGetter = fragment.method("get preference screen", m -> m.shape(SCREEN, 0) && !Modifier.isAbstract(m.flags));
        getScreen = screenGetter.reflect(loader);
        FieldRef screenField = NativeDex.one(fragment.fields, field -> dex.type(field.type) != null && dex.type(field.type).hasField(SCREEN), "preference manager");
        manager = screenField.reflect(loader);
        newScreen = dex.type(screenField.type).method("create preference screen", m -> m.signature("(" + CONTEXT + ")" + SCREEN)).reflect(loader);
        setScreen = fragment.method("set preference screen", m -> m.signature("(" + SCREEN + ")V")).reflect(loader);
        addPreference = group.method("add preference", m -> m.params.equals(List.of(PREFERENCE)) && m.calls("Ljava/util/Collections;", "binarySearch")).reflect(loader);
        findPreference = group.method("find preference", m -> m.signature("(Ljava/lang/CharSequence;)" + PREFERENCE)).reflect(loader);
        FieldRef titleField = NativeDex.one(shell.fields, field -> pagePreferences.reads(field.type) && !Modifier.isStatic(field.flags), "settings page title supplier");
        titleSupplier = titleField.reflect(loader);
        setPageTitle = dex.definition(NativeDex.one(pagePreferences.calls(), m -> m.owner.equals(titleField.type) && m.signature("(Ljava/lang/Object;)V"), "update settings page title")).reflect(loader);

        Class<?> preference = pref.load(loader);
        row = Class.forName("com.microsoft.edge.dewey.settings.DeweyPreference", false, loader).getConstructor(Context.class);
        setKey = preference.getMethod("setKey", String.class);
        setTitle = preference.getMethod("setTitle", CharSequence.class);
        getParent = preference.getMethod("getParent");
        getOrder = preference.getMethod("getOrder");
        setOrder = preference.getMethod("setOrder", int.class);
        setPersistent = preference.getMethod("setPersistent", boolean.class);
        setFragment = preference.getMethod("setFragment", String.class);
        getExtras = preference.getMethod("getExtras");
        setIntent = preference.getMethod("setIntent", Intent.class);
        setClick = pref.method("preference click listener", m -> m.name.equals("setOnPreferenceClickListener") && m.shape("V", 1)).reflect(loader);
        String params = "Lorg/chromium/content_public/browser/LoadUrlParams;";
        navigationParams = dex.type(params).load(loader).getConstructor(String.class, int.class);
        String createTabSignature = "(" + params + "I" + NativeRoles.TAB + ")" + NativeRoles.TAB;
        Type browser = dex.type("Lorg/chromium/chrome/browser/ChromeTabbedActivity;");
        MethodRef creator = browser.method("browser tab creator", m -> m.params.equals(List.of("Z")) && dex.type(m.result) != null
            && dex.type(m.result).methods.stream().anyMatch(candidate -> candidate.signature(createTabSignature) && !Modifier.isAbstract(candidate.flags)));
        getTabCreator = creator.reflect(loader);
        createTab = dex.type(creator.result).method("open new browser tab", m -> m.signature(createTabSignature)).reflect(loader);
    }

    void hook(EdgeEntry entry, List<HookHandle> hooks) {
        entry.deoptimize(fragmentCreate);
        hooks.add(entry.hook(mainCreate).intercept(chain -> {
            Object result = chain.proceed();
            try {
                addEntry(chain.getThisObject());
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.e("EdgeBar", "Could not add the Edge extension settings entry", error);
            }
            return result;
        }));
        hooks.add(entry.hook(pageCreate).intercept(chain -> {
            Object fragment = chain.getThisObject();
            if (!isPage((Bundle) getArguments.invoke(fragment))) return chain.proceed();
            showPage(fragment);
            return null;
        }));
        // Edge's simple settings fragment supplies the title and recycler shell.
        hooks.add(entry.hook(pageView).intercept(chain -> {
            Object fragment = chain.getThisObject();
            if (!isPage((Bundle) getArguments.invoke(fragment))) return chain.proceed();
            return entry.getInvoker(nativeView).invokeSpecial(fragment, chain.getArg(0), chain.getArg(1), chain.getArg(2));
        }));
    }

    private void addEntry(Object fragment) throws ReflectiveOperationException {
        Object screen = getScreen.invoke(fragment);
        Context context = (Context) getContext.invoke(fragment);
        Object entry = preference(context, PAGE_KEY, pageTitle(context));
        setFragment.invoke(entry, SHELL);
        ((Bundle) getExtras.invoke(entry)).putBoolean(PAGE_KEY, true);
        Object anchor = findPreference.invoke(screen, "privacy");
        if (anchor != null) {
            Object parent = getParent.invoke(anchor);
            int order = (int) getOrder.invoke(anchor);
            if (parent != null && order < Integer.MAX_VALUE) {
                setOrder.invoke(entry, order + 1);
                screen = parent;
            }
        }
        addPreference.invoke(screen, entry);
    }

    private void showPage(Object fragment) throws ReflectiveOperationException {
        Activity activity = (Activity) requireActivity.invoke(fragment);
        Context context = (Context) getContext.invoke(fragment);
        Object screen = newScreen.invoke(manager.get(fragment), context);
        addCatalog(screen, activity);
        addStore(screen, context, "edge_store", text(context, "浏览 Edge 加载项", "Browse Edge Add-ons"), EDGE_ADDONS);
        addStore(screen, context, "chrome_store", text(context, "浏览 Chrome 应用商店", "Browse Chrome Web Store"), CHROME_STORE);
        setScreen.invoke(fragment, screen);
        setPageTitle.invoke(titleSupplier.get(fragment), pageTitle(context));
    }

    private void openCatalog(Activity settings) throws ReflectiveOperationException {
        Activity browser = EdgeRuntime.activity();
        if (browser == null || !getTabCreator.getDeclaringClass().isInstance(browser) || browser.getTaskId() != settings.getTaskId()) {
            settings.startActivity(storeIntent(EDGE_ADDONS));
            return;
        }
        Object creator = getTabCreator.invoke(browser, false);
        createTab.invoke(creator, navigationParams.newInstance("edge://extensions", TRANSITION_AUTO_BOOKMARK),
            TAB_FROM_BROWSER_UI, EdgeAccess.tab(browser));
        settings.startActivity(new Intent(settings, browser.getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private Object preference(Context context, String key, String title) throws ReflectiveOperationException {
        Object item = row.newInstance(context);
        setKey.invoke(item, key);
        setTitle.invoke(item, title);
        setPersistent.invoke(item, false);
        return item;
    }

    private void addCatalog(Object screen, Activity activity) throws ReflectiveOperationException {
        String key = PAGE_KEY + ".catalog";
        Object item = preference(activity, key, text(activity, "打开 Edge 扩展目录", "Open the Edge extension catalog"));
        Class<?> listener = setClick.getParameterTypes()[0];
        Object click = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[] {listener}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return key;
                default:
                    try {
                        openCatalog(activity);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        Log.e("EdgeBar", "Could not open the Edge extension catalog", error);
                        new AlertDialog.Builder(activity).setTitle(pageTitle(activity))
                            .setMessage(text(activity, "无法打开 Edge 扩展目录", "Could not open the Edge extension catalog"))
                            .setPositiveButton(android.R.string.ok, null).show();
                    }
                    return true;
            }
        });
        setClick.invoke(item, click);
        addPreference.invoke(screen, item);
    }

    private void addStore(Object screen, Context context, String key, String title, String url) throws ReflectiveOperationException {
        Object item = preference(context, PAGE_KEY + "." + key, title);
        setIntent.invoke(item, storeIntent(url));
        addPreference.invoke(screen, item);
    }

    private static Intent storeIntent(String url) { return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.microsoft.emmx"); }
    private static boolean isPage(Bundle args) { return args != null && args.getBoolean(PAGE_KEY); }
    private static String descriptor(String name) { return "L" + name.replace('.', '/') + ";"; }
    private static String pageTitle(Context context) { return text(context, "扩展安装", "Install extensions"); }
    private static String text(Context context, String zh, String en) {
        return "zh".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage()) ? zh : en;
    }
}
