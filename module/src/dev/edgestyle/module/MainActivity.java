package dev.edgestyle.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    static final String COMMAND="dev.edgestyle.module.COMMAND";
    private Switch enabled;private TextView status;private LinearLayout orderBody;
    private final ArrayList<String> orderRoles=new ArrayList<>();private final Set<String> selectedRoles=new HashSet<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(48),dp(20),dp(24));scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText("EdgeBar 设置");title.setTextSize(28);body.addView(title);
        TextView subtitle=new TextView(this);subtitle.setText("网页单行地址栏 · 新标签页按钮位置");subtitle.setPadding(0,dp(8),0,dp(24));body.addView(subtitle);
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
        button(body,"保存并在 Edge 应用",()->{ArrayList<String> selected=new ArrayList<>();for(String role:orderRoles)if(selectedRoles.contains(role))selected.add(role);ConfigBridge.save(this,enabled.isChecked(),String.join(",",selected),ntpPosition.getCheckedRadioButtonId()==atTop.getId()?"top":"search",addressAlignment.getCheckedRadioButtonId()==alignCenter.getId()?"center":"left");applyInEdge();});
        status=new TextView(this);status.setPadding(0,dp(18),0,0);body.addView(status);
    }
    @Override public void onResume(){super.onResume();if(status!=null)status.setText(ConfigBridge.service!=null?"Vector 已连接 · Edge 主进程作用域":"请在 Vector 启用 EdgeBar，并选择 Edge 作用域");}
    private void setOrder(String value){orderRoles.clear();selectedRoles.clear();for(String role:value.split(","))if(Arrays.asList("address","tabs","back","forward","newtab").contains(role)&&!orderRoles.contains(role)){orderRoles.add(role);selectedRoles.add(role);}for(String role:new String[]{"address","tabs","back","forward","newtab"})if(!orderRoles.contains(role))orderRoles.add(role);orderRoles.add("menu");selectedRoles.add("address");selectedRoles.add("menu");renderOrder();}
    private void renderOrder(){orderBody.removeAllViews();for(int index=0;index<orderRoles.size();index++){final int i=index;String role=orderRoles.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);CheckBox check=new CheckBox(this);check.setText(roleName(role));check.setChecked(selectedRoles.contains(role));check.setEnabled(!role.equals("address")&&!role.equals("menu"));row.addView(check,new LinearLayout.LayoutParams(0,-2,1));check.setOnCheckedChangeListener((b,v)->{if(v)selectedRoles.add(role);else selectedRoles.remove(role);});for(int direction:new int[]{-1,1}){Button move=new Button(this);move.setText(direction==-1?"↑":"↓");move.setEnabled(!role.equals("menu")&&i+direction>=0&&i+direction<orderRoles.size()-1);row.addView(move,new LinearLayout.LayoutParams(dp(52),dp(48)));move.setOnClickListener(v->{Collections.swap(orderRoles,i,i+direction);renderOrder();});}orderBody.addView(row);}}
    private String roleName(String role){switch(role){case "address":return "地址栏";case "tabs":return "标签页（原生计数）";case "menu":return "菜单（原生右侧槽位）";case "back":return "后退";case "forward":return "前进";case "reload":return "刷新 / 停止加载";default:return "新建标签页";}}
    private void label(LinearLayout body,String text){TextView t=new TextView(this);t.setText(text);t.setPadding(0,dp(18),0,dp(4));t.setTextSize(15);body.addView(t);}
    private void button(LinearLayout body,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);body.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());}
    private void applyInEdge(){Intent i=getPackageManager().getLaunchIntentForPackage("com.microsoft.emmx");if(i==null){message("请安装 Microsoft Edge");return;}i.putExtra(COMMAND,"apply").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}
    private void message(String text){new AlertDialog.Builder(this).setMessage(text==null?"操作需要重试":text).setPositiveButton("确定",null).show();}
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
