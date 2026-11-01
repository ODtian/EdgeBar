package dev.edgestyle.module;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BarConfig {
    static final String GROUP="edgebar";
    boolean enabled=true;
    String order="address,tabs,menu";
    String ntpPosition="search";
    String addressAlignment="left";
    static BarConfig read(SharedPreferences p) {
        BarConfig c=new BarConfig();
        try {
            c.enabled=p.getBoolean("enabled",true);
            c.order=p.getString("order",c.order);
            c.ntpPosition="top".equals(p.getString("ntpPosition","search"))?"top":"search";
            c.addressAlignment="center".equals(p.getString("addressAlignment","left"))?"center":"left";
            if("back,address,extensions,tabs,menu".equals(c.order))c.order="address,tabs,menu";
        } catch (RuntimeException ignored) {}
        return c;
    }
    List<String> roles(boolean editing) {
        List<String> out=new ArrayList<>();
        List<String> allowed=Arrays.asList("back","forward","newtab","address","tabs","menu");
        for(String r:order.split(",")) {r=r.trim();if(allowed.contains(r)&&!out.contains(r)&&(!editing||r.equals("address")||r.equals("menu")))out.add(r);}
        if(!out.contains("address"))out.add(0,"address");
        out.remove("menu");out.add("menu");
        return out;
    }
}
