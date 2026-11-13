package dev.edgestyle.module;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.res.ColorStateList;
import java.util.*;

/** Native web-row adapter; NTP layout is returned to its original controller. */
final class NativeBar implements AutoCloseable {
    final ViewGroup toolbar;
    final NativeToolbar nativeToolbar;
    private final Map<String,NativeActions.Binding> bindings=new LinkedHashMap<>();
    private final Map<String,NativeActions> actions=new LinkedHashMap<>();
    private final Map<ViewGroup,Search> searches=new IdentityHashMap<>();
    private final Map<View,Integer> originalVisibility=new IdentityHashMap<>();
    private BarConfig config;
    private boolean closed,updating,parked=true,webApplied;
    private List<String> arranged=Collections.emptyList();
    Object nativeAssembly;
    private boolean assemblyBound;
    private final View.OnLayoutChangeListener addressWidthChanged=(view,l,t,r,b,oldL,oldT,oldR,oldB)->{
        if(!closed&&"center".equals(config.addressAlignment)&&r-l!=oldR-oldL&&!view.hasFocus())view.requestLayout();
    };
    private final android.text.TextWatcher addressTextChanged=new android.text.TextWatcher(){
        @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
        @Override public void onTextChanged(CharSequence text,int start,int before,int count){}
        @Override public void afterTextChanged(android.text.Editable text){if(!closed)toolbar.requestLayout();}
    };

    NativeBar(ViewGroup toolbar,BarConfig config)throws ReflectiveOperationException {
        this.toolbar=toolbar;this.config=config;nativeToolbar=new NativeToolbar(toolbar);
        EdgeRuntime.bind(toolbar.getContext());
        EdgeAccess.find(toolbar,"url_bar").addOnLayoutChangeListener(addressWidthChanged);
        ((TextView)EdgeAccess.find(toolbar,"url_bar")).addTextChangedListener(addressTextChanged);
        android.util.Log.i("EdgeBar","Native layout adapter attached; address parent unchanged");
    }
    boolean ready(){return actions.containsKey("tabs")&&actions.containsKey("menu")&&!closed;}
    boolean webLayout(){return ready()&&!nativeToolbar.regularNewTabPage();}
    void syncPageLayout(){
        if(!ready())return;
        try{
            if(nativeToolbar.regularNewTabPage()){park();for(Search search:searches.values())search.ensure();}
            else if(parked){parked=false;if(!webApplied)arrange(arranged);hideOriginalActions();}
        }catch(Exception error){android.util.Log.w("EdgeBar","Native page layout boundary",error);}
    }
    private void park(){
        parked=true;
        for(NativeActions action:actions.values())if(!action.role.startsWith("ntp_"))action.view.setVisibility(View.GONE);
    }
    boolean belongs(View v){for(View node=v;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null)if(node==toolbar)return true;return false;}
    boolean owns(View v){for(NativeActions a:actions.values())if(a.view==v)return true;return false;}
    void binding(NativeActions.Binding binding){
        if(closed)return;
        try{
            View source=NativeToolbar.view(binding.source);
            String role=binding.role;
            if(role.equals("tabs")){
                if(!belongs(source))role="ntp_tabs";
            }else if(role.equals("menu")){
                if(source==EdgeAccess.find(toolbar.getRootView(),"overflow_button_bottom"))role="ntp_menu";
                else if(source!=EdgeAccess.find(toolbar,"overflow_button_top"))return;
            }else if(!belongs(source))return;
            if(!role.equals(binding.role))binding=new NativeActions.Binding(role,binding.source,binding.args,binding.bind);
        }catch(Exception e){return;}
        NativeActions.Binding previous=bindings.get(binding.role);
        // Once a model has been rebound, old copies must release their old supplier.
        if(previous!=null&&previous.source==binding.source){bindings.put(binding.role,binding);update(config);return;}
        bindings.put(binding.role,binding);
        NativeActions old=actions.remove(binding.role);if(old!=null){unregister(old);old.close();}
        if(binding.role.startsWith("ntp_"))for(Search search:searches.values())search.clear();
        arranged=Collections.emptyList();update(config);
        if(binding.role.equals("menu")&&nativeAssembly!=null&&!assemblyBound){
            assemblyBound=true;
            try{EdgeEntry.nativeBindings.method("bindAssembly").invoke(nativeAssembly);}catch(ReflectiveOperationException e){android.util.Log.e("EdgeBar","Native compact row binding",e);}
        }
    }
    void update(BarConfig next){
        if(closed||updating)return;updating=true;boolean alignmentChanged=!config.addressAlignment.equals(next.addressAlignment);config=next;
        try {
            List<String> roles=new ArrayList<>();
            for(String role:config.roles(false))if(role.equals("address")||bindings.containsKey(role))roles.add(role);
            List<String> needed=new ArrayList<>(roles);for(String core:new String[]{"tabs","menu"})if(bindings.containsKey(core)&&!needed.contains(core))needed.add(core);

            for(String role:needed)if(!role.equals("address")&&!actions.containsKey(role)){
                try{NativeActions action=NativeActions.create(nativeToolbar,nativeToolbar.right,bindings.get(role));actions.put(role,action);if(parked)action.park();}
                catch(Exception error){android.util.Log.w("EdgeBar","Native action unavailable: "+role,error);}
            }
            if(!ready())return;
            roles.removeIf(role->!role.equals("address")&&!actions.containsKey(role));
            if(nativeToolbar.regularNewTabPage()){arranged=new ArrayList<>(roles);park();}
            else if(parked||!roles.equals(arranged)){parked=false;arrange(roles);}
            if(webLayout())hideOriginalActions();
            for(Search search:searches.values())search.ensure();
            nativeStateChanged();
            if(alignmentChanged)EdgeAccess.find(toolbar,"location_bar").requestLayout();
        }catch(Exception error){android.util.Log.e("EdgeBar","Native layout transaction",error);}
        finally{updating=false;}
    }
    private void arrange(List<String> roles)throws ReflectiveOperationException {
        for(NativeActions a:actions.values())if(!a.role.startsWith("ntp_")){unregister(a);if(a.view.getParent() instanceof ViewGroup)((ViewGroup)a.view.getParent()).removeView(a.view);}
        List<Object> left=nativeToolbar.icons(true),right=nativeToolbar.icons(false);boolean start=true;
        for(String role:roles){
            if(role.equals("address")){start=false;continue;}
            NativeActions action=actions.get(role);ViewGroup parent=start?nativeToolbar.left:nativeToolbar.right;
            parent.addView(action.view);if(action.capture!=null)(start?left:right).add(action.capture);
        }
        arranged=new ArrayList<>(roles);webApplied=true;toolbar.requestLayout();toolbar.invalidate();
        android.util.Log.i("EdgeBar","Native slot layout: "+roles);
    }
    private void unregister(NativeActions action){try{
        Object found=nativeToolbar.capture(action.view,action.coordinator);if(found!=null)action.capture=found;
        if(action.capture!=null){nativeToolbar.icons(true).remove(action.capture);nativeToolbar.icons(false).remove(action.capture);}
    }catch(Exception e){android.util.Log.w("EdgeBar","Capture entry release",e);}}
    void capturesChanged(){
        if(!ready()){update(config);return;}
        for(NativeActions a:actions.values())try{
            unregister(a);
            if(a.capture!=null){
                List<Object> target=NativeToolbar.inside(a.view,nativeToolbar.left)?nativeToolbar.icons(true):NativeToolbar.inside(a.view,nativeToolbar.right)?nativeToolbar.icons(false):null;
                if(target!=null&&!target.contains(a.capture))target.add(a.capture);
            }
        }catch(Exception e){android.util.Log.w("EdgeBar","Native button capture ownership",e);}
    }
    void nativeStateChanged(){
        if(!ready())return;
        syncPageLayout();if(!parked){hideOriginalActions();for(String role:arranged)if(!role.equals("address"))actions.get(role).view.setVisibility(View.VISIBLE);}
    }
    void beforeNativeState(){syncPageLayout();}
    void hideOriginalActions(){
        boolean web=webLayout();
        for(String id:new String[]{"overflow_button_top","edge_toolbar_back_button","edge_toolbar_forward_button","edge_toolbar_plus_button","edge_toolbar_tab_stack_button","sharing_button","edge_toolbar_copilot","edge_toolbar_copilot_icon","edge_toolbar_copilot_nudge","edge_location_bar_copilot_button_container"}){
            View view=EdgeAccess.find(toolbar,id);if(view!=null&&!owns(view)&&web){
                originalVisibility.putIfAbsent(view,view.getVisibility());view.setVisibility(View.GONE);
            }
        }
    }
    void searchAttached(ViewGroup view){
        if(closed)return;
        Search search=searches.get(view);if(search==null){search=new Search(view);searches.put(view,search);}search.ensure();
    }
    void findSearch(View view){
        if((view.getClass().getName().equals(NativeToolbar.SEARCH)||view.getClass().getName().equals(NativeToolbar.TOP_BAR))&&view instanceof ViewGroup)searchAttached((ViewGroup)view);
        else if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)findSearch(((ViewGroup)view).getChildAt(i));
    }
    void searchDestroyed(View page){
        for(ViewGroup search:new ArrayList<>(searches.keySet()))for(View node=search;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null)if(node==page){searches.remove(search).clear();break;}
    }
    @Override public void close(){
        if(closed)return;closed=true;
        EdgeAccess.find(toolbar,"url_bar").removeOnLayoutChangeListener(addressWidthChanged);
        ((TextView)EdgeAccess.find(toolbar,"url_bar")).removeTextChangedListener(addressTextChanged);
        for(Search s:searches.values())s.clear();searches.clear();
        for(NativeActions a:actions.values()){unregister(a);a.close();}actions.clear();bindings.clear();
        for(Map.Entry<View,Integer> e:originalVisibility.entrySet())e.getKey().setVisibility(e.getValue());originalVisibility.clear();
        try{nativeToolbar.addressStatusInset((ViewGroup)EdgeAccess.find(toolbar,"location_bar"),false);}catch(ReflectiveOperationException error){android.util.Log.w("EdgeBar","Native address edge restore",error);}
        toolbar.requestLayout();toolbar.invalidate();
    }
    private final class Search {
        final ViewGroup view;
        final boolean top;
        final Map<String,NativeActions> copies=new LinkedHashMap<>();
        Search(ViewGroup view){this.view=view;top=view.getClass().getName().equals(NativeToolbar.TOP_BAR);}
        void ensure(){
            if(top!="top".equals(config.ntpPosition)){clear();return;}
            if(!ready()||!nativeToolbar.regularNewTabPage())return;
            if(!view.isAttachedToWindow())return;
            View end=top?view:EdgeAccess.find(view,"search_box_right_container");if(!(end instanceof LinearLayout))return;
            try{
                for(String role:new String[]{"tabs","menu"}){
                    NativeActions a=copies.get(role);
                    if(a==null){NativeActions.Binding binding=bindings.get("ntp_"+role);if(binding==null)return;a=NativeActions.create(nativeToolbar,(ViewGroup)end,binding);copies.put(role,a);}
                    if(a.view.getParent()!=end){
                        if(a.view.getParent() instanceof ViewGroup)((ViewGroup)a.view.getParent()).removeView(a.view);
                        ((ViewGroup)end).addView(a.view);
                    }
                    a.view.setVisibility(View.VISIBLE);copies.put(role,a);
                    EdgeEntry.nativeBindings.method(role.equals("tabs")?"tabsPalette":"menuPalette").invoke(a.coordinator);
                    View nativeColorSource=EdgeAccess.find(view,top?"edge_change_ntp_button":"search_box_text");
                    ColorStateList nativeColors=top?((ImageView)nativeColorSource).getImageTintList():((TextView)nativeColorSource).getTextColors();
                    if(role.endsWith("tabs"))a.view.getClass().getMethod("setTextColor",ColorStateList.class).invoke(a.view,nativeColors);
                    else ((ImageView)EdgeAccess.find(a.view,"edge_overflow_button")).setImageTintList(nativeColors);
                }
            }catch(Exception error){clear();android.util.Log.w("EdgeBar","Native search actions",error);}
        }
        void clear(){for(NativeActions action:copies.values())action.close();copies.clear();}
    }
}
