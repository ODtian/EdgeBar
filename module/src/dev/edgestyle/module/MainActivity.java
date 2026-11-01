package dev.edgestyle.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.UUID;
import java.util.*;
import java.util.zip.*;

public final class MainActivity extends Activity {
    static final String COMMAND="dev.edgestyle.module.COMMAND",VALUE="dev.edgestyle.module.VALUE";
    private Switch enabled;private TextView status;private LinearLayout orderBody;
    private final ArrayList<String> orderRoles=new ArrayList<>();private final Set<String> selectedRoles=new HashSet<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(48),dp(20),dp(24));scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText("EdgeBar 设置");title.setTextSize(28);body.addView(title);
        TextView subtitle=new TextView(this);subtitle.setText("网页单行地址栏 · 空标签页按钮位置 · 扩展安装");subtitle.setPadding(0,dp(8),0,dp(24));body.addView(subtitle);
        SharedPreferences p=ConfigBridge.prefs(this);BarConfig c=BarConfig.read(p);
        enabled=new Switch(this);enabled.setText("启用单行顶栏");enabled.setChecked(c.enabled);body.addView(enabled);
        TextView original=new TextView(this);original.setText("网页地址栏与按钮同一行。空标签页保留原版中间地址栏，工具按钮的位置可选择。输入时地址栏展开。");original.setPadding(0,dp(16),0,dp(8));body.addView(original);
        label(body,"空标签页工具按钮位置");
        RadioGroup ntpPosition=new RadioGroup(this);ntpPosition.setOrientation(1);
        RadioButton besideSearch=new RadioButton(this);besideSearch.setId(View.generateViewId());besideSearch.setText("中间地址栏旁边");ntpPosition.addView(besideSearch);
        RadioButton atTop=new RadioButton(this);atTop.setId(View.generateViewId());atTop.setText("顶部");ntpPosition.addView(atTop);
        ntpPosition.check("top".equals(c.ntpPosition)?atTop.getId():besideSearch.getId());body.addView(ntpPosition);
        label(body,"网页地址对齐");
        RadioGroup addressAlignment=new RadioGroup(this);addressAlignment.setOrientation(1);
        RadioButton alignLeft=new RadioButton(this);alignLeft.setId(View.generateViewId());alignLeft.setText("左对齐");addressAlignment.addView(alignLeft);
        RadioButton alignCenter=new RadioButton(this);alignCenter.setId(View.generateViewId());alignCenter.setText("居中");addressAlignment.addView(alignCenter);
        addressAlignment.check("center".equals(c.addressAlignment)?alignCenter.getId():alignLeft.getId());body.addView(addressAlignment);
        label(body,"按钮排列");
        orderBody=new LinearLayout(this);orderBody.setOrientation(1);body.addView(orderBody);setOrder(c.order);
        TextView hint=new TextView(this);hint.setText("网页按此排列，菜单置于右侧。刷新 / 停止保留在地址栏内。首页显示标签页和菜单；输入时地址栏展开并显示建议列表。");hint.setTextSize(12);body.addView(hint);
        button(body,"保存并在 Edge 应用",()->{ArrayList<String> selected=new ArrayList<>();for(String role:orderRoles)if(selectedRoles.contains(role))selected.add(role);ConfigBridge.save(this,enabled.isChecked(),String.join(",",selected),ntpPosition.getCheckedRadioButtonId()==atTop.getId()?"top":"search",addressAlignment.getCheckedRadioButtonId()==alignCenter.getId()?"center":"left");launch("apply","");});
        label(body,"扩展安装");
        button(body,"浏览 Chrome Web Store",()->launch("browse","https://chromewebstore.google.com/category/extensions"));
        button(body,"浏览 Edge Add-ons 桌面商店",()->launch("browse","https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home"));
        button(body,"通过商店详情 / 自定义 URL 安装",this::urlDialog);
        button(body,"安装本地 CRX / ZIP",this::pickFile);
        button(body,"安装开发源码目录",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),3));
        status=new TextView(this);status.setPadding(0,dp(18),0,0);body.addView(status);
        String command=getIntent().getStringExtra(COMMAND);if("pick".equals(command))pickFile();else if("tree".equals(command))startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),3);
    }
    @Override public void onResume(){super.onResume();if(status!=null)status.setText(ConfigBridge.service!=null?"Vector 已连接 · Edge 主进程作用域":"请在 Vector 启用 EdgeBar，并选择 Edge 作用域");}
    private void setOrder(String value){orderRoles.clear();selectedRoles.clear();for(String role:value.split(","))if(Arrays.asList("address","tabs","back","forward","newtab").contains(role)&&!orderRoles.contains(role)){orderRoles.add(role);selectedRoles.add(role);}for(String role:new String[]{"address","tabs","back","forward","newtab"})if(!orderRoles.contains(role))orderRoles.add(role);orderRoles.add("menu");selectedRoles.add("address");selectedRoles.add("menu");renderOrder();}
    private void renderOrder(){orderBody.removeAllViews();for(int index=0;index<orderRoles.size();index++){final int i=index;String role=orderRoles.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);CheckBox check=new CheckBox(this);check.setText(roleName(role));check.setChecked(selectedRoles.contains(role));check.setEnabled(!role.equals("address")&&!role.equals("menu"));row.addView(check,new LinearLayout.LayoutParams(0,-2,1));check.setOnCheckedChangeListener((b,v)->{if(v)selectedRoles.add(role);else selectedRoles.remove(role);});for(int direction:new int[]{-1,1}){Button move=new Button(this);move.setText(direction==-1?"↑":"↓");move.setEnabled(!role.equals("menu")&&i+direction>=0&&i+direction<orderRoles.size()-1);row.addView(move,new LinearLayout.LayoutParams(dp(52),dp(48)));move.setOnClickListener(v->{Collections.swap(orderRoles,i,i+direction);renderOrder();});}orderBody.addView(row);}}
    private String roleName(String role){switch(role){case "address":return "地址栏";case "tabs":return "标签页（原生计数）";case "menu":return "菜单（原生右侧槽位）";case "back":return "后退";case "forward":return "前进";case "reload":return "刷新 / 停止加载";default:return "新建标签页";}}
    private EditText field(LinearLayout body,String text,String value,boolean numeric){label(body,text);EditText e=new EditText(this);e.setSingleLine();e.setText(value);if(numeric)e.setInputType(2);body.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private void label(LinearLayout body,String text){TextView t=new TextView(this);t.setText(text);t.setPadding(0,dp(18),0,dp(4));t.setTextSize(15);body.addView(t);}
    private void button(LinearLayout body,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);body.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());}
    private void pickFile(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),2);}
    private void urlDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("扩展详情页或 HTTPS CRX 地址");new AlertDialog.Builder(this).setTitle("安装扩展").setView(e).setPositiveButton("取得扩展包",(d,w)->{try{StoreSource.resolve(e.getText().toString());launch("url",e.getText().toString());}catch(RuntimeException ex){message(ex.getMessage());}}).setNegativeButton("返回",null).show();}
    private void launch(String command,String value){Intent i=getPackageManager().getLaunchIntentForPackage("com.microsoft.emmx");if(i==null){message("请安装 Microsoft Edge");return;}i.putExtra(COMMAND,command).putExtra(VALUE,value).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();status.setText("准备扩展包…");new Thread(()->{try{File dir=new File(getCacheDir(),"imports");dir.mkdirs();File file=new File(dir,UUID.randomUUID()+ (request==3?".zip":".bin"));if(request==3){try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){archiveTree(uri,DocumentsContract.getTreeDocumentId(uri),"",zip,new long[]{0});}}else{byte[] bytes=ExtensionPackages.readLimited(getContentResolver().openInputStream(uri),ExtensionPackages.MAX_FILE);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}}
            Uri shared=Uri.parse("content://dev.edgestyle.module.files/"+file.getName());runOnUiThread(()->{Intent i=getPackageManager().getLaunchIntentForPackage("com.microsoft.emmx");if(i==null)return;i.putExtra(COMMAND,"local").putExtra(VALUE,shared.toString());i.setClipData(ClipData.newRawUri("extension",shared));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);grantUriPermission("com.microsoft.emmx",shared,Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);});
        }catch(Exception e){runOnUiThread(()->message(e.getMessage()));}},"EdgeBar-import").start();}
    private void archiveTree(Uri tree,String id,String prefix,ZipOutputStream zip,long[] size)throws Exception {
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(Cursor cursor=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(cursor==null)throw new IOException("目录读取失败");while(cursor.moveToNext()){String child=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);if(name.contains("/")||name.equals("..")||name.equals("."))throw new IOException("目录文件名无效");if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){archiveTree(tree,child,prefix+name+"/",zip,size);continue;}byte[] bytes=ExtensionPackages.readLimited(getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,child)),ExtensionPackages.MAX_FILE);size[0]+=bytes.length;if(size[0]>ExtensionPackages.MAX_FILE)throw new IOException("源码目录超过大小限制");zip.putNextEntry(new ZipEntry(prefix+name));zip.write(bytes);zip.closeEntry();}}
    }
    private void message(String text){new AlertDialog.Builder(this).setMessage(text==null?"操作需要重试":text).setPositiveButton("确定",null).show();}
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
