package dev.edgestyle.module;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
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
    static Object invoke(Object target,String name)throws Exception{Method method=target.getClass().getMethod(name);method.setAccessible(true);return method.invoke(target);}
    static String url(Activity activity){Object tab=tab(activity);if(tab==null)return "";try{return urlSpec(invoke(tab,"getUrl"));}catch(Exception e){android.util.Log.d("EdgeBar","Current URL: "+e.getClass().getSimpleName());return "";}}
    static String urlSpec(Object url){
        if(url==null)return "";
        try{return (String)invoke(url,"getSpec");}catch(Exception ignored){
            for(Field field:url.getClass().getDeclaredFields())if(field.getType()==String.class&&!Modifier.isStatic(field.getModifiers()))try{
                field.setAccessible(true);String spec=(String)field.get(url);
                if(spec!=null&&spec.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*"))return spec;
            }catch(Exception ignoredField){}
            return "";
        }
    }
    static Object webContents(Activity activity){Object tab=tab(activity);if(tab==null)return null;try{return invoke(tab,"getWebContents");}catch(Exception e){return null;}}
    static void evaluate(Activity activity,String script,java.util.function.Consumer<String> result){
        Object web=webContents(activity);if(web==null)return;
        try {
            Class<?> cb=Class.forName("org.chromium.content_public.browser.JavaScriptCallback",false,web.getClass().getClassLoader());
            Method method=NativeToolbar.signature(web.getClass(),void.class,String.class,cb);
            Object callback=result==null?null:Proxy.newProxyInstance(cb.getClassLoader(),new Class[]{cb},(p,m,args)->{if(args!=null&&args.length>0&&args[0] instanceof String)result.accept((String)args[0]);return null;});method.invoke(web,script,callback);
        }catch(Exception e){android.util.Log.d("EdgeBar","JavaScript adapter: "+e.getClass().getSimpleName());}
    }
    static boolean click(Activity activity,String... ids){if(activity==null)return false;View view=find(activity.getWindow().getDecorView(),ids);return view!=null&&view.hasOnClickListeners()&&view.performClick();}
    static void browse(Activity activity,String url){Intent intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url));intent.setPackage("com.microsoft.emmx");activity.startActivity(intent);}
    static void nativeInstall(Activity activity,ExtensionPackages.Prepared prepared)throws Exception {
        ClassLoader loader=activity.getClassLoader();
        Class<?> controller=Class.forName("com.microsoft.edge.extensions.GlobalExtensionStateController",false,loader);
        NativeToolbar.signature(controller,void.class,boolean.class).invoke(null,true);
        Intent intent=new Intent();intent.setClassName("com.microsoft.emmx",CRX_ACTIVITY);
        intent.putExtra("install_by_crx_title",prepared.title());intent.putExtra("install_by_crx_button_text","选择扩展包");
        intent.putExtra(CRX_PATH,prepared.file.getAbsolutePath());activity.startActivity(intent);
    }
    private static final String CRX_ACTIVITY="com.microsoft.edge.extensions.developer.ExtensionInstallByCrxActivity";
    private static final String CRX_PATH="dev.edgestyle.module.preparedCrx";
    static void created(Activity activity){
        if(!activity.getClass().getName().equals(CRX_ACTIVITY))return;
        String path=activity.getIntent().getStringExtra(CRX_PATH);if(path==null)return;activity.getIntent().removeExtra(CRX_PATH);
        try{
            java.io.File file=new java.io.File(path);
            NativeToolbar.fieldOfType(activity.getClass(),android.net.Uri.class).set(activity,android.net.Uri.fromFile(file));
            NativeToolbar.fieldOfType(activity.getClass(),String.class).set(activity,file.getName());
            ((android.widget.TextView)find(activity.getWindow().getDecorView(),"extension_crx_file_name")).setText(file.getName());
            EdgeRuntime.MAIN.post(()->click(activity,"extension_submit_button"));
        }catch(ReflectiveOperationException error){EdgeRuntime.error(activity,error);}
    }
    static boolean installed(Activity activity,String id){
        try{
            Class<?> api=Class.forName("com.microsoft.edge.extensions.EdgeAndroidExtensionsAPI",false,activity.getClassLoader());
            for(Method method:api.getDeclaredMethods())if(Modifier.isStatic(method.getModifiers())&&method.getParameterCount()==0&&Iterable.class.isAssignableFrom(method.getReturnType())){
                method.setAccessible(true);Object items=method.invoke(null);
                for(Object item:(Iterable<?>)items)for(Field field:item.getClass().getDeclaredFields())if(field.getType()==String.class&&!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);if(id.equals(field.get(item)))return true;}
            }
        }catch(Exception e){android.util.Log.d("EdgeBar","Extension registry: "+e.getClass().getSimpleName());}return false;
    }
}
