package dev.edgestyle.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class EdgeRuntime {
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"EdgeBar-extensions"));
    static WeakReference<Activity> current=new WeakReference<>(null);
    static volatile boolean installing;
    static Activity activity(){Activity a=current.get();return a!=null&&!a.isDestroyed()&&!a.isFinishing()?a:null;}
    static void bind(android.content.Context context){for(int i=0;i<16;i++){if(context instanceof Activity){Activity owner=(Activity)context;if(owner.getClass().getName().contains("ChromeTabbedActivity")){current=new WeakReference<>(owner);android.util.Log.i("EdgeBar","Browser activity bound from native toolbar");}return;}if(!(context instanceof android.content.ContextWrapper))return;context=((android.content.ContextWrapper)context).getBaseContext();}}
    static void resumed(Activity activity){
        if(!activity.getPackageName().equals("com.microsoft.emmx"))return;
        if(activity.getClass().getName().contains("ChromeTabbedActivity")||activity.getClass().getName().equals("com.microsoft.ruby.Main"))current=new WeakReference<>(activity);
        route(activity,activity.getIntent());
    }
    static void route(Activity activity,Intent intent){
        if(intent==null)return;String command=intent.getStringExtra(MainActivity.COMMAND);if(command==null)return;
        android.util.Log.i("EdgeBar","Configuration command: "+command);
        String value=intent.getStringExtra(MainActivity.VALUE);intent.removeExtra(MainActivity.COMMAND);intent.removeExtra(MainActivity.VALUE);
        MAIN.postDelayed(()->{Activity owner=activity();if(owner==null)owner=activity;switch(command){case "browse":EdgeAccess.browse(owner,value);break;case "url":remote(owner,value);break;case "local":local(owner,Uri.parse(value));break;case "apply":EdgeEntry.reload();break;}},600);
    }
    static void panel(Activity owner){
        String page=EdgeAccess.url(owner);
        java.util.List<String> labels=new java.util.ArrayList<>();java.util.List<Runnable> actions=new java.util.ArrayList<>();
        if(!StoreSource.idFromPage(page).isEmpty()){labels.add("安装当前商店详情的扩展");actions.add(()->remote(owner,page));}
        labels.add("Chrome Web Store");actions.add(()->EdgeAccess.browse(owner,"https://chromewebstore.google.com/category/extensions"));
        labels.add("Edge Add-ons 桌面商店");actions.add(()->EdgeAccess.browse(owner,"https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home"));
        labels.add("从详情页 / 自定义 URL 安装");actions.add(()->urlDialog(owner));
        labels.add("本地 CRX / ZIP");actions.add(()->module(owner,"pick"));
        labels.add("开发源码目录");actions.add(()->module(owner,"tree"));
        labels.add("原生扩展管理");actions.add(()->EdgeAccess.browse(owner,"edge://extensions"));
        labels.add("地址栏与工具栏设置");actions.add(()->module(owner,"settings"));
        new AlertDialog.Builder(owner).setTitle("扩展与商店").setItems(labels.toArray(new String[0]),(d,which)->actions.get(which).run()).show();
    }
    static void module(Activity owner,String command){Intent intent=new Intent();intent.setClassName("dev.edgestyle.module","dev.edgestyle.module.MainActivity");intent.putExtra(MainActivity.COMMAND,command);owner.startActivity(intent);}
    static void urlDialog(Activity owner){EditText edit=new EditText(owner);edit.setSingleLine();edit.setHint("Chrome / Edge 商店详情或 CRX URL");new AlertDialog.Builder(owner).setTitle("安装扩展").setView(edit).setPositiveButton("取得扩展包",(d,w)->remote(owner,edit.getText().toString())).setNegativeButton("返回",null).show();}
    static void remote(Activity owner,String url){prepare(owner,()->ExtensionPackages.remote(owner,url));}
    static void local(Activity owner,Uri uri){prepare(owner,()->ExtensionPackages.local(owner,uri));}
    interface Work{ExtensionPackages.Prepared run()throws Exception;}
    static void prepare(Activity owner,Work work){
        if(installing){Toast.makeText(owner,"正在取得扩展包",Toast.LENGTH_SHORT).show();return;}installing=true;Toast.makeText(owner,"正在取得扩展包…",Toast.LENGTH_SHORT).show();
        IO.execute(()->{try{ExtensionPackages.Prepared pkg=work.run();MAIN.post(()->{installing=false;if(owner.isFinishing()||owner.isDestroyed())return;new AlertDialog.Builder(owner).setTitle(pkg.title()).setMessage("来源："+pkg.source+"\n扩展 ID："+pkg.id+"\n\n"+pkg.permissions()).setPositiveButton("交给 Edge 安装",(d,w)->{try{EdgeAccess.nativeInstall(owner,pkg);android.util.Log.i("EdgeBar","Native installation submitted id="+pkg.id);watchInstall(owner,pkg,0);}catch(Exception e){error(owner,e);}}).setNegativeButton("返回",null).show();});}catch(Exception error){MAIN.post(()->{installing=false;error(owner,error);});}});
    }
    static void watchInstall(Activity owner,ExtensionPackages.Prepared pkg,int attempt){MAIN.postDelayed(()->{if(owner.isDestroyed())return;if(EdgeAccess.installed(owner,pkg.id)){android.util.Log.i("EdgeBar","Native registry confirmed id="+pkg.id);Toast.makeText(owner,"已安装："+pkg.title(),Toast.LENGTH_LONG).show();}else if(attempt<30)watchInstall(owner,pkg,attempt+1);},1000);}
    static void error(Activity owner,Throwable error){android.util.Log.e("EdgeBar","Operation error",error);if(!owner.isDestroyed())new AlertDialog.Builder(owner).setTitle("扩展操作结果").setMessage(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()).setPositiveButton("确定",null).show();}
    static void stores(Activity owner){
        String url=EdgeAccess.url(owner);
        if(StoreSource.idFromPage(url).isEmpty())return;
        String script="(()=>{const u=location.href;if(!/^(https:\\/\\/)(chromewebstore\\.google\\.com|chrome\\.google\\.com|microsoftedge\\.microsoft\\.com)\\//.test(u))return;if(!document.body)return;if(window.__edgebarBoundUrl===u&&document.getElementById('edgebar-install-action'))return;window.__edgebarBoundUrl=u;const q=()=>window.__edgebarInstallRequest=location.href;document.addEventListener('click',e=>{const b=e.target.closest('button,[role=button]');if(!b)return;const t=(b.innerText||b.getAttribute('aria-label')||'').trim();if(/^(Add to (Chrome|Microsoft Edge)|Get|Add to desktop|添加到桌面|添加至Chrome|添加到Chrome|添加至 Chrome|添加到 Chrome|获取|添加到 Microsoft Edge|安装到 Edge)$/i.test(t)){e.preventDefault();e.stopImmediatePropagation();q();}},true);if(!document.getElementById('edgebar-install-action')){const b=document.createElement('button');b.id='edgebar-install-action';b.textContent='安装到 Edge';b.style.cssText='padding:10px 18px;border-radius:20px;background:#1769e0;color:white;border:0;font:inherit;margin:12px 0;';b.onclick=q;const h=document.querySelector('h1');if(h)h.insertAdjacentElement('afterend',b);else document.body.prepend(b);}document.querySelectorAll('button').forEach(b=>{if(/Add to Chrome|添加.*Chrome/.test(b.innerText||'')){b.disabled=false;b.style.pointerEvents='auto';}});})();";
        EdgeAccess.evaluate(owner,script,null);
        EdgeAccess.evaluate(owner,"(()=>{const u=window.__edgebarInstallRequest||'';window.__edgebarInstallRequest='';return u;})()",value->{try{String requested=new org.json.JSONArray("["+value+"]").optString(0);if(!requested.isEmpty()&&StoreSource.idFromPage(requested).equals(StoreSource.idFromPage(EdgeAccess.url(owner))))MAIN.post(()->remote(owner,requested));}catch(Exception ignored){}});
    }
}

