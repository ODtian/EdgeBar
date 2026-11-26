package dev.edgestyle.module;

import android.app.Activity;
import android.view.View;
import java.lang.reflect.*;

final class EdgeAccess {
    static View find(View root,String... names){for(String name:names){int id=root.getResources().getIdentifier(name,"id","com.microsoft.emmx");if(id!=0){View view=root.findViewById(id);if(view!=null)return view;}}return null;}
    static Object tab(Activity activity){
        if(activity==null)return null;
        try{
            Class<?> provider=Class.forName("org.chromium.chrome.browser.ActivityTabProvider",false,activity.getClassLoader());
            Object source=NativeToolbar.fieldOfType(activity.getClass(),provider).get(activity);
            return ((java.util.function.Supplier<?>)source).get();
        }catch(Exception ignored){}
        return null;
    }
    static String urlSpec(Object url){
        if(url==null)return "";
        try{
            Method getSpec=url.getClass().getMethod("getSpec");getSpec.setAccessible(true);return (String)getSpec.invoke(url);
        }catch(Exception ignored){
            for(Field field:url.getClass().getDeclaredFields())if(field.getType()==String.class&&!Modifier.isStatic(field.getModifiers()))try{
                field.setAccessible(true);String spec=(String)field.get(url);
                if(spec!=null&&spec.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*"))return spec;
            }catch(Exception ignoredField){}
            return "";
        }
    }
}
