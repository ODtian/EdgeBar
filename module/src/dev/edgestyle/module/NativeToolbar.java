package dev.edgestyle.module;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Modifier;

/** Accesses Edge 156's native toolbar slots and location coordinator. */
final class NativeToolbar {
    static final String PHONE="org.chromium.chrome.browser.toolbar.top.EdgeToolbarPhone";
    static final String SEARCH="org.chromium.chrome.browser.edge_ntp.v2.view.NewTabPageV2SearchBox";
    static final String TOP_BAR="org.chromium.chrome.browser.edge_ntp.v2.view.NewTabPageV2TopBar";
    final ViewGroup toolbar;
    final ViewGroup left,right;
    final ClassLoader loader;
    final Class<?> icon;
    Object topTabStyle;
    private final Field leftIcons,rightIcons;
    private final int originalAddressGoneStart;
    private boolean lastNewTabPage=true,pageKnown;

    NativeToolbar(ViewGroup toolbar)throws ReflectiveOperationException {
        this.toolbar=toolbar;loader=toolbar.getClass().getClassLoader();
        icon=EdgeEntry.nativeBindings.icon;
        View l=EdgeAccess.find(toolbar,"toolbar_left_buttons"),r=EdgeAccess.find(toolbar,"toolbar_buttons");
        if(!(l instanceof android.widget.LinearLayout)||!(r instanceof android.widget.LinearLayout))
            throw new NoSuchFieldException("Native button slots unavailable");
        left=(ViewGroup)l;right=(ViewGroup)r;
        leftIcons=EdgeEntry.nativeBindings.field("leftIcons");rightIcons=EdgeEntry.nativeBindings.field("rightIcons");
        originalAddressGoneStart=EdgeEntry.nativeBindings.field("addressGoneStart").getInt(EdgeAccess.find(toolbar,"url_bar").getLayoutParams());
        if(leftIcons.getType()!=List.class||rightIcons.getType()!=List.class)
            throw new NoSuchFieldException("Unsupported native capture lists");
    }
    @SuppressWarnings("unchecked") List<Object> icons(boolean start)throws IllegalAccessException {
        return (List<Object>)(start?leftIcons:rightIcons).get(toolbar);
    }
    Object capture(View view,Object source)throws ReflectiveOperationException {
        for(boolean start:new boolean[]{true,false}){
            List<Object> list=icons(start);if(list==null)continue;
            for(Object entry:list)if(entry!=null)for(View captured:views(entry))if(inside(captured,view))return entry;
        }
        return icon.isInstance(source)?source:null;
    }
    static boolean inside(View child,View parent){
        for(View node=child;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null)if(node==parent)return true;
        return false;
    }
    boolean newTabPage(){
        String spec="";
        try{
            Object provider=EdgeEntry.nativeBindings.field("toolbarDataProvider").get(toolbar);
            if(provider!=null){
                Object tab=EdgeEntry.nativeBindings.field("addressTab").get(provider);
                Object web=tab==null?null:method(tab.getClass(),"getWebContents",0).invoke(tab);
                if(web!=null)spec=EdgeAccess.urlSpec(EdgeEntry.nativeBindings.method("webVisibleUrl").invoke(web));
                if(spec.isEmpty())spec=EdgeAccess.urlSpec(EdgeEntry.nativeBindings.field("visibleUrl").get(provider));
            }
        }catch(Exception ignored){}
        if(!spec.isEmpty()){
            pageKnown=true;
            android.net.Uri url=android.net.Uri.parse(spec);
            lastNewTabPage="newtab".equals(url.getHost())&&("edge".equals(url.getScheme())||"chrome".equals(url.getScheme())||"chrome-native".equals(url.getScheme()));
        }
        return lastNewTabPage;
    }
    boolean pageKnown(){newTabPage();return pageKnown;}
    void addressStatusInset(ViewGroup address,boolean browsing)throws ReflectiveOperationException {
        View url=EdgeAccess.find(address,"url_bar");
        Object params=url.getLayoutParams();
        Field goneStart=EdgeEntry.nativeBindings.field("addressGoneStart");
        int inset=browsing?EdgeAccess.find(address,"location_bar_status_icon_holding_space").getLayoutParams().width:originalAddressGoneStart;
        if(goneStart.getInt(params)==inset)return;
        goneStart.setInt(params,inset);
        url.setLayoutParams((ViewGroup.LayoutParams)params);
        android.util.Log.i("EdgeBar","Native status edge inset="+inset+" statusVisibility="+EdgeAccess.find(address,"location_bar_status").getVisibility());
    }
    static boolean addressTextFits(ViewGroup address){
        android.widget.TextView url=(android.widget.TextView)EdgeAccess.find(address,"url_bar");
        int available=url.getWidth()-url.getPaddingStart()-url.getPaddingEnd();
        return url.getWidth()==0||android.text.Layout.getDesiredWidth(url.getText(),url.getPaint())<=available;
    }
    static List<View> views(Object owner)throws ReflectiveOperationException {
        List<View> result=new ArrayList<>();
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&View.class.isAssignableFrom(field.getType())){
            field.setAccessible(true);View view=(View)field.get(owner);if(view!=null&&!result.contains(view))result.add(view);
        }
        return result;
    }
    static View view(Object owner)throws ReflectiveOperationException {return NativeDex.one(views(owner),view->true,"native coordinator view");}
    static Field fieldOfType(Class<?> type,Class<?> value)throws NoSuchFieldException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass()){
            Field found=null;
            for(Field field:current.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==value){
                if(found!=null)throw new NoSuchFieldException("Multiple fields for "+value.getName());found=field;
            }
            if(found!=null){found.setAccessible(true);return found;}
        }
        throw new NoSuchFieldException(type.getName()+" field type "+value.getName());
    }
    static Method signature(Class<?> type,Class<?> result,Class<?>... parameters)throws NoSuchMethodException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass()){
            Method found=null;
            for(Method method:current.getDeclaredMethods())if(method.getReturnType()==result&&Arrays.equals(method.getParameterTypes(),parameters)){
                if(found!=null)throw new NoSuchMethodException("Multiple methods for "+result.getName());found=method;
            }
            if(found!=null){found.setAccessible(true);return found;}
        }
        throw new NoSuchMethodException(type.getName()+" return "+result.getName());
    }
    static Method method(Class<?> type,String name,int count)throws NoSuchMethodException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()){
            Method found=null;
            for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==count){
                if(found!=null)throw new NoSuchMethodException("Ambiguous "+name);m.setAccessible(true);found=m;
            }
            if(found!=null)return found;
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    static android.app.Activity owner(View view){
        return ownerContext(view.getContext());
    }
    static android.app.Activity ownerContext(android.content.Context context){
        for(int i=0;i<16;i++){
            if(context instanceof android.app.Activity)return (android.app.Activity)context;
            if(!(context instanceof android.content.ContextWrapper))break;
            context=((android.content.ContextWrapper)context).getBaseContext();
        }
        return null;
    }
    static String role(View view){
        if(view==null||view.getId()==View.NO_ID)return "";
        String name;try{name=view.getResources().getResourceEntryName(view.getId());}catch(android.content.res.Resources.NotFoundException e){return "";}
        if(name.endsWith("_stub"))name=name.substring(0,name.length()-5);
        switch(name){
            case "tab_center_button":case "edge_toolbar_tab_stack_button":return "tabs";
            case "overflow_button_bottom":case "overflow_button_top":return "menu";
            case "back_button":case "edge_toolbar_back_button":return "back";
            case "forward_button":case "edge_toolbar_forward_button":return "forward";
            case "edge_bottom_bar_plus_button":case "edge_toolbar_plus_button":return "newtab";
            default:return "";
        }
    }
}
