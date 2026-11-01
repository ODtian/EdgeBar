package dev.edgestyle.module;

import java.lang.reflect.*;
import java.util.*;

/** Keeps the resolved native members; the APK index is released after resolution. */
final class NativeBindings {
    private final Map<String,Method> methods=new HashMap<>();
    private final Map<String,Constructor<?>> constructors=new HashMap<>();
    private final Map<String,Field> fields=new HashMap<>();
    final List<Executable> deoptimize=new ArrayList<>();
    final Class<?> icon;
    NativeBindings(String[] apks,ClassLoader loader)throws Exception{
        long start=android.os.SystemClock.uptimeMillis();
        NativeRoles roles=new NativeRoles(new NativeDex(apks));
        for(Map.Entry<String,NativeDex.MethodRef> entry:roles.methods.entrySet()){
            NativeDex.MethodRef method=entry.getValue();
            if(method.name.equals("<init>"))constructors.put(entry.getKey(),method.constructor(loader));
            else methods.put(entry.getKey(),method.reflect(loader));
        }
        for(Map.Entry<String,NativeDex.FieldRef> entry:roles.fields.entrySet())fields.put(entry.getKey(),entry.getValue().reflect(loader));
        Class<?> addressParams=methods.get("addressPresentation").getParameterTypes()[3];
        Object defaults=addressParams.getConstructor(int.class,int.class).newInstance(0,0);
        for(NativeDex.FieldRef candidate:roles.relativeGoneMargins){
            Field field=candidate.reflect(loader);
            if(field.getInt(defaults)==Integer.MIN_VALUE){fields.put("addressGoneStart",field);break;}
        }
        for(NativeDex.MethodRef method:roles.deoptimize)deoptimize.add(method.name.equals("<init>")?method.constructor(loader):method.reflect(loader));
        icon=roles.icon.load(loader);
        android.util.Log.i("EdgeBar","Resolved native toolbar by resources, signatures and calls in "+(android.os.SystemClock.uptimeMillis()-start)+" ms");
    }
    Method method(String role){return methods.get(role);}
    Constructor<?> constructor(String role){return constructors.get(role);}
    Field field(String role){return fields.get(role);}
}
