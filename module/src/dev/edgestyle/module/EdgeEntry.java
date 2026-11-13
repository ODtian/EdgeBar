package dev.edgestyle.module;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;

public final class EdgeEntry extends XposedModule {
    static EdgeEntry instance;
    static NativeBindings nativeBindings;
    static volatile BarConfig config=new BarConfig();
    private boolean main,activityHooksInstalled,bindingsInstalled,bindingsInstalling,deferredLogged;
    private SharedPreferences preferences;
    private ApplicationInfo edgeInfo;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private final Runnable preferenceReload=EdgeEntry::reload;
    private static final Map<ViewGroup,NativeBar> bars=new IdentityHashMap<>();
    private static final Map<View,NativeActions.Binding> sources=new IdentityHashMap<>();
    private static final Map<Object,View> footerOwners=new IdentityHashMap<>();
    private static final ThreadLocal<ViewGroup> addressLayout=new ThreadLocal<>();

    @Override public void onModuleLoaded(ModuleLoadedParam param){
        main="com.microsoft.emmx".equals(param.getProcessName());if(!main){detach();return;}
        instance=this;preferences=getRemotePreferences(BarConfig.GROUP);config=BarConfig.read(preferences);
        listener=(p,key)->{EdgeRuntime.MAIN.removeCallbacks(preferenceReload);EdgeRuntime.MAIN.postDelayed(preferenceReload,80);};preferences.registerOnSharedPreferenceChangeListener(listener);
        log(Log.INFO,"EdgeBar","Loaded native-layout adapter in Edge main process");installActivityHooks();
    }
    @Override public void onPackageReady(PackageReadyParam param){
        if(main&&"com.microsoft.emmx".equals(param.getPackageName())){edgeInfo=param.getApplicationInfo();installBindings(param.getClassLoader());}
    }
    static void reload(){
        if(instance!=null)config=BarConfig.read(instance.preferences);
        if(!config.enabled){for(NativeBar bar:new ArrayList<>(bars.values()))bar.close();bars.clear();restoreFooter();}
        else{
            Activity activity=EdgeRuntime.activity();if(activity!=null&&instance!=null)instance.recover(activity);
            for(NativeBar bar:new ArrayList<>(bars.values()))bar.update(config);syncFooter();
        }
    }
    private synchronized void installActivityHooks(){
        if(activityHooksInstalled)return;List<HookHandle> installed=new ArrayList<>();
        try{
            installed.add(hook(Instrumentation.class.getDeclaredMethod("callActivityOnCreate",Activity.class,android.os.Bundle.class)).intercept(chain->{
                Activity activity=(Activity)chain.getArg(0);
                if("com.microsoft.emmx".equals(activity.getPackageName())){
                    edgeInfo=activity.getApplicationInfo();
                    installBindings(activity.getClass().getClassLoader());installBindings(activity.getClassLoader());
                }
                Object result=chain.proceed();EdgeAccess.created(activity);return result;
            }));
            installed.add(hook(Activity.class.getDeclaredMethod("onResume")).intercept(chain->{
                Activity activity=(Activity)chain.getThisObject();
                if("com.microsoft.emmx".equals(activity.getPackageName())){
                    edgeInfo=activity.getApplicationInfo();
                    installBindings(activity.getClassLoader());installBindings(activity.getClass().getClassLoader());
                }
                Object result=chain.proceed();EdgeRuntime.resumed(activity);if(config.enabled)recover(activity);return result;
            }));
            installed.add(hook(Activity.class.getDeclaredMethod("onDestroy")).intercept(chain->{
                Activity activity=(Activity)chain.getThisObject();
                for(NativeBar bar:new ArrayList<>(bars.values()))if(NativeToolbar.owner(bar.toolbar)==activity){bars.remove(bar.toolbar);bar.close();}
                sources.entrySet().removeIf(e->NativeToolbar.owner(e.getKey())==activity);
                footerOwners.entrySet().removeIf(e->NativeToolbar.owner(e.getValue())==activity);
                return chain.proceed();
            }));
            installed.add(hook(Instrumentation.class.getDeclaredMethod("callActivityOnNewIntent",Activity.class,Intent.class)).intercept(chain->{
                Object result=chain.proceed();Activity activity=(Activity)chain.getArg(0);EdgeRuntime.resumed(activity);EdgeRuntime.route(activity,(Intent)chain.getArg(1));return result;
            }));
            activityHooksInstalled=true;
        }catch(Throwable error){rollback(installed);log(Log.ERROR,"EdgeBar","Activity hook transaction",error);}
    }
    private synchronized void installBindings(ClassLoader loader){
        if(bindingsInstalled||bindingsInstalling||loader==null||edgeInfo==null)return;bindingsInstalling=true;
        List<HookHandle> installed=new ArrayList<>();
        try{
            Class<?> toolbar=Class.forName(NativeToolbar.PHONE,false,loader);
            Class<?> phone=Class.forName("org.chromium.chrome.browser.toolbar.top.ToolbarPhone",false,loader);
            Class<?> search=Class.forName(NativeToolbar.SEARCH,false,loader);
            Class<?> topBar=Class.forName(NativeToolbar.TOP_BAR,false,loader);
            List<String> apks=new ArrayList<>();apks.add(edgeInfo.sourceDir);if(edgeInfo.splitSourceDirs!=null)Collections.addAll(apks,edgeInfo.splitSourceDirs);
            if(nativeBindings==null)nativeBindings=new NativeBindings(apks.toArray(new String[0]),loader);
            Class<?> ntpPage=Class.forName("org.chromium.chrome.browser.edge_ntp.v2.NewTabPageV2Layout",false,loader);
            for(Executable executable:nativeBindings.deoptimize)deoptimize(executable);
            installed.add(hook(nativeBindings.method("createTab")).intercept(chain->{
                if(config.enabled&&!nativeBindings.field("creatorIncognito").getBoolean(chain.getThisObject())){
                    Object params=chain.getArg(0);Field urlField=nativeBindings.field("loadUrlSpec");
                    android.net.Uri url=android.net.Uri.parse((String)urlField.get(params));
                    if("newtab".equals(url.getHost())&&("edge".equals(url.getScheme())||"chrome".equals(url.getScheme())||"chrome-native".equals(url.getScheme())))urlField.set(params,"about:blank");
                }
                return chain.proceed();
            }));
            Method assemble=nativeBindings.method("assemble");
            installed.add(hook(assemble).intercept(chain->{
                if(!config.enabled)return chain.proceed();
                NativeBar bar=bars.get(nativeBindings.field("assemblyToolbar").get(chain.getThisObject()));
                if(bar!=null)bar.nativeAssembly=chain.getThisObject();
                NativeAssembly.enter();
                Object result;try{result=chain.proceed();}finally{NativeAssembly.leave();}
                return result;
            }));
            installed.add(hook(nativeBindings.method("orientation")).intercept(chain->NativeAssembly.active()?Configuration.ORIENTATION_LANDSCAPE:chain.proceed()));
            installed.add(hook(nativeBindings.method("toolbarConfiguration")).intercept(chain->{
                if(!config.enabled)return chain.proceed();
                NativeAssembly.enterConfiguration();
                try{return chain.proceed();}finally{NativeAssembly.leaveConfiguration();}
            }));
            installed.add(hook(nativeBindings.method("iconOrientation")).intercept(chain->{
                View view=NativeToolbar.view(chain.getThisObject());
                if(config.enabled)for(NativeBar bar:bars.values())if(bar.owns(view))return chain.proceed(new Object[]{Configuration.ORIENTATION_LANDSCAPE});
                return chain.proceed();
            }));
            for(Method palette:new Method[]{nativeBindings.method("SearchBoxPalette"),nativeBindings.method("TopBarPalette")})installed.add(hook(palette).intercept(chain->{
                Object r=chain.proceed();if(config.enabled)for(NativeBar bar:new ArrayList<>(bars.values()))if(((View)chain.getThisObject()).getRootView()==bar.toolbar.getRootView())bar.searchAttached((ViewGroup)chain.getThisObject());return r;
            }));
            installed.add(hook(ntpPage.getDeclaredMethod("destroy")).intercept(chain->{
                for(NativeBar bar:new ArrayList<>(bars.values()))bar.searchDestroyed((View)chain.getThisObject());
                return chain.proceed();
            }));
            Method bindTabs=nativeBindings.method("bindTabs"),bindMenu=nativeBindings.method("bindMenu");
            Method bindNavigation=nativeBindings.method("bindNavigation"),bindPlus=nativeBindings.method("bindPlus");
            installed.add(hook(toolbar.getDeclaredMethod("onFinishInflate")).intercept(chain->{Object r=chain.proceed();if(config.enabled)attach((ViewGroup)chain.getThisObject());return r;}));
            Class<?> location=Class.forName("org.chromium.chrome.browser.omnibox.LocationBarPhone",false,loader);
            Method measureAddress=location.getDeclaredMethod("onMeasure",int.class,int.class);
            installed.add(hook(measureAddress).intercept(chain->{
                ViewGroup address=(ViewGroup)chain.getThisObject();
                if(config.enabled)for(NativeBar bar:bars.values())if(bar.belongs(address)){
                    bar.hideOriginalActions();
                    bar.nativeToolbar.addressStatusInset(address,bar.webLayout()&&!EdgeAccess.find(address,"url_bar").hasFocus());
                    break;
                }
                ViewGroup previous=addressLayout.get();addressLayout.set(address);
                try{return chain.proceed();}finally{addressLayout.set(previous);}
            }));
            installed.add(hook(nativeBindings.method("addressAlignmentUpdate")).intercept(chain->{
                ViewGroup previous=addressLayout.get();addressLayout.set((ViewGroup)chain.getThisObject());
                try{return chain.proceed();}finally{addressLayout.set(previous);}
            }));
            installed.add(hook(nativeBindings.method("addressPageAlignment")).intercept(chain->{
                ViewGroup address=addressLayout.get();
                if(config.enabled&&address!=null)for(NativeBar bar:bars.values())if(bar.webLayout()&&bar.belongs(address))return false;
                return chain.proceed();
            }));
            installed.add(hook(nativeBindings.method("addressPresentation")).intercept(chain->{
                ViewGroup address=(ViewGroup)chain.getThisObject();
                if(config.enabled&&"center".equals(config.addressAlignment)&&!EdgeAccess.find(address,"url_bar").hasFocus())for(NativeBar bar:bars.values())if(bar.webLayout()&&bar.belongs(address)){
                    // Native constraints bound the text between the visible status and actions.
                    return chain.proceed(new Object[]{true,NativeToolbar.addressTextFits(address),0,chain.getArg(3)});
                }
                return chain.proceed();
            }));
            deoptimize(measureAddress);
            installed.add(hook(toolbar.getDeclaredMethod("destroy")).intercept(chain->{
                ViewGroup view=(ViewGroup)chain.getThisObject();NativeBar bar=bars.remove(view);if(bar!=null)bar.close();return chain.proceed();
            }));
            for(String name:new String[]{"setLeftIconCoordinators","setRightIconCoordinators"})installed.add(hook(toolbar.getDeclaredMethod(name,List.class)).intercept(chain->{
                Object r=chain.proceed();NativeBar bar=bars.get(chain.getThisObject());if(config.enabled&&bar!=null)bar.capturesChanged();return r;
            }));
            // Follow native page state to place the merged buttons.
            for(Method state:new Method[]{NativeToolbar.method(phone,"onLayout",5),phone.getDeclaredMethod("setTabSwitcherMode",boolean.class),toolbar.getDeclaredMethod("setUrlFocusChangeFraction",float.class,boolean.class)})installed.add(hook(state).intercept(chain->{
                NativeBar bar=bars.get(chain.getThisObject());if(bar!=null)bar.beforeNativeState();
                Object r=chain.proceed();if(config.enabled&&bar!=null)bar.nativeStateChanged();return r;
            }));
            installed.add(hook(toolbar.getDeclaredMethod("setOrientation",int.class)).intercept(chain->{
                Object r=chain.proceed();NativeBar bar=bars.get(chain.getThisObject());if(config.enabled&&bar!=null)bar.hideOriginalActions();return r;
            }));
            for(Method method:new Method[]{bindTabs,bindMenu,bindNavigation,bindPlus})installed.add(hook(method).intercept(chain->{
                if(NativeAssembly.creating())return chain.proceed();
                Object r=chain.proceed();View view=NativeToolbar.view(chain.getThisObject());String role=method==bindTabs?"tabs":method==bindMenu?"menu":method==bindPlus?"newtab":NativeToolbar.role(view);
                if(!role.isEmpty()){
                    Object[] args=new Object[method.getParameterCount()];for(int i=0;i<args.length;i++)args[i]=chain.getArg(i);
                    NativeActions.Binding binding=new NativeActions.Binding(role,chain.getThisObject(),args,method);
                    sources.put(view,binding);if(config.enabled)for(NativeBar bar:new ArrayList<>(bars.values()))if(view.getRootView()==bar.toolbar.getRootView())bar.binding(binding);
                    syncFooter();
                }
                return r;
            }));
            installed.add(hook(nativeBindings.method("plusState")).intercept(chain->{
                Object r=chain.proceed();View view=NativeToolbar.view(chain.getThisObject());
                if(config.enabled)for(NativeBar bar:bars.values())if(bar.owns(view))bar.nativeStateChanged();return r;
            }));
            installed.add(hook(phone.getDeclaredMethod("onMeasure",int.class,int.class)).intercept(chain->{
                NativeBar bar=bars.get(chain.getThisObject());if(config.enabled&&bar!=null)bar.nativeStateChanged();
                return chain.proceed();
            }));
            installed.add(hook(nativeBindings.method("drawAddressBackground")).intercept(chain->{
                if(config.enabled)for(NativeBar bar:bars.values())if(bar.ready()&&nativeBindings.field("addressBackgroundDrawable").get(bar.toolbar)==chain.getThisObject()&&bar.nativeToolbar.regularNewTabPage()&&!EdgeAccess.find(bar.toolbar,"url_bar").hasFocus())return null;
                return chain.proceed();
            }));
            for(Class<?> pageSlot:new Class<?>[]{search,topBar})installed.add(hook(pageSlot.getDeclaredMethod("onAttachedToWindow")).intercept(chain->{
                Object r=chain.proceed();if(config.enabled)for(NativeBar bar:new ArrayList<>(bars.values()))if(((View)chain.getThisObject()).getRootView()==bar.toolbar.getRootView())bar.searchAttached((ViewGroup)chain.getThisObject());syncFooter();return r;
            }));
            installed.add(hook(nativeBindings.method("toolbarFormFactor")).intercept(chain->NativeAssembly.footerActive()||NativeAssembly.takeConfigurationFormFactor()?true:chain.proceed()));
            installed.add(hook(nativeBindings.method("updateFooter")).intercept(chain->{
                Object owner=chain.getThisObject();View view=(View)nativeBindings.field("footerView").get(owner);
                footerOwners.put(owner,view);
                if(!config.enabled)return chain.proceed();
                NativeAssembly.enterFooter();
                try{return chain.proceed();}finally{NativeAssembly.leaveFooter();}
            }));
            bindingsInstalled=true;startStores();
            log(Log.INFO,"EdgeBar","Native lifecycle/action/presentation hooks installed");
        }catch(ClassNotFoundException|NoClassDefFoundError error){
            rollback(installed);if(!deferredLogged){deferredLogged=true;log(Log.INFO,"EdgeBar","Awaiting Edge's chrome class loader: "+error.getMessage());}
        }catch(Throwable error){rollback(installed);log(Log.ERROR,"EdgeBar","Native symbols unavailable; keeping Edge native UI",error);}
        finally{bindingsInstalling=false;}
    }
    private void rollback(List<HookHandle> handles){for(int i=handles.size()-1;i>=0;i--)try{handles.get(i).unhook();}catch(Throwable e){log(Log.ERROR,"EdgeBar","Hook rollback",e);}}
    private void attach(ViewGroup toolbar){
        if(bars.containsKey(toolbar))return;
        try{
            NativeBar bar=new NativeBar(toolbar,config);bars.put(toolbar,bar);
            for(Map.Entry<View,NativeActions.Binding> e:new ArrayList<>(sources.entrySet()))if(e.getKey().getRootView()==toolbar.getRootView())bar.binding(e.getValue());
            toolbar.post(()->{bar.findSearch(toolbar.getRootView());syncFooter();});
        }catch(Throwable error){log(Log.ERROR,"EdgeBar","Native layout adapter unavailable; address untouched",error);}
    }
    private void recover(Activity activity){
        if(!bindingsInstalled||!"com.microsoft.emmx".equals(activity.getPackageName()))return;
        View toolbar=EdgeAccess.find(activity.getWindow().getDecorView(),"toolbar");
        if(toolbar instanceof ViewGroup&&toolbar.getClass().getName().equals(NativeToolbar.PHONE))attach((ViewGroup)toolbar);
    }
    private static boolean restoringFooter;
    private static void syncFooter(){
        refreshFooters();
    }
    private static void restoreFooter(){
        refreshFooters();
    }
    private static void refreshFooters(){
        if(restoringFooter)return;restoringFooter=true;
        try{
        for(Object owner:footerOwners.keySet())try{nativeBindings.method("updateFooter").invoke(owner);}catch(Exception error){Log.w("EdgeBar","Native footer restore",error);}
        }finally{restoringFooter=false;}
    }
    private static boolean storesStarted;
    private static void startStores(){
        if(storesStarted)return;storesStarted=true;
        EdgeRuntime.MAIN.postDelayed(new Runnable(){public void run(){Activity owner=EdgeRuntime.activity();if(owner!=null)EdgeRuntime.stores(owner);EdgeRuntime.MAIN.postDelayed(this,900);}},900);
    }
}
