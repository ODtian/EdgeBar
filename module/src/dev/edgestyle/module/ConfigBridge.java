package dev.edgestyle.module;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.Map;

public final class ConfigBridge extends Application {
    static volatile XposedService service;
    static Context app;
    @Override public void onCreate(){super.onCreate();app=this;XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener(){
        public void onServiceBind(XposedService value){service=value;SharedPreferences local=app.getSharedPreferences("bar",MODE_PRIVATE);if(local.getBoolean("pending",false)){SharedPreferences.Editor edit=value.getRemotePreferences(BarConfig.GROUP).edit();copy(local,edit);edit.commit();local.edit().putBoolean("pending",false).apply();}}
        public void onServiceDied(XposedService value){if(service==value)service=null;}
    });}
    static SharedPreferences prefs(Context context){try{if(service!=null)return service.getRemotePreferences(BarConfig.GROUP);}catch(RuntimeException ignored){}return context.getSharedPreferences("bar",MODE_PRIVATE);}
    static void save(Context context,boolean enabled,String order,String ntpPosition,String addressAlignment){
        SharedPreferences local=context.getSharedPreferences("bar",MODE_PRIVATE);
        local.edit().putBoolean("enabled",enabled).putString("order",order).putString("ntpPosition",ntpPosition).putString("addressAlignment",addressAlignment).putBoolean("pending",true).commit();
        if(service!=null){SharedPreferences.Editor edit=service.getRemotePreferences(BarConfig.GROUP).edit();copy(local,edit);edit.commit();local.edit().putBoolean("pending",false).apply();}
    }
    static void copy(SharedPreferences source,SharedPreferences.Editor target){for(Map.Entry<String,?> entry:source.getAll().entrySet()){String key=entry.getKey();if(key.equals("pending"))continue;Object value=entry.getValue();if(value instanceof Boolean)target.putBoolean(key,(Boolean)value);else if(value instanceof Integer)target.putInt(key,(Integer)value);else if(value instanceof String)target.putString(key,(String)value);}}
}
