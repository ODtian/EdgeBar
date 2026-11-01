package dev.edgestyle.module;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.*;

/** Native button views retain their original coordinators and listeners. */
final class NativeActions implements AutoCloseable {
    static final class Binding {
        final String role;
        final Object source;
        final Object[] args;
        final Method bind;
        Binding(String role,Object source,Object[] args,Method bind){this.role=role;this.source=source;this.args=args.clone();this.bind=bind;}
    }
    final String role;
    final View view;
    final Object coordinator;
    Object capture;
    private final Method destroy;
    private final ViewGroup originalParent;
    private final ViewGroup.LayoutParams originalParams;
    private final int originalIndex,originalVisibility;
    private boolean closed;

    private NativeActions(String role,View view,Object coordinator,Object capture,Method destroy){
        this.role=role;this.view=view;this.coordinator=coordinator;this.capture=capture;this.destroy=destroy;
        originalParent=destroy==null?(ViewGroup)view.getParent():null;
        originalParams=destroy==null?view.getLayoutParams():null;
        originalIndex=originalParent==null?-1:originalParent.indexOfChild(view);originalVisibility=view.getVisibility();
    }
    static NativeActions create(NativeToolbar nativeToolbar,ViewGroup destination,Binding binding)throws ReflectiveOperationException {
        if(binding.role.startsWith("ntp_")){
            NativeAssembly.startPageActions();
            try{
                int resource=EdgeEntry.nativeBindings.field(binding.role.endsWith("tabs")?"tabsLayout":"menuLayout").getInt(null);
                View view=LayoutInflater.from(destination.getContext()).inflate(resource,destination,false);view.setId(NativeToolbar.view(binding.source).getId());
                Object coordinator;
                if(binding.role.endsWith("tabs")){
                    int padding=EdgeEntry.nativeBindings.field("iconPadding").getInt(null);
                    Object history=EdgeEntry.nativeBindings.field("tabsHistory").get(binding.source);
                    coordinator=EdgeEntry.nativeBindings.constructor("tabsConstructor").newInstance(view,nativeToolbar.topTabStyle,history,false,padding);
                }else coordinator=EdgeEntry.nativeBindings.constructor("menuConstructor").newInstance(view);
                Object[] args=binding.args.clone();if(binding.role.endsWith("menu"))for(int i=0;i<args.length;i++)if(binding.bind.getParameterTypes()[i]==boolean.class)args[i]=true;
                binding.bind.invoke(coordinator,args);
                return new NativeActions(binding.role,view,coordinator,null,EdgeEntry.nativeBindings.method(binding.role.endsWith("tabs")?"tabsDestroy":"menuDestroy"));
            }finally{NativeAssembly.finishPageActions();}
        }
        View view=NativeToolbar.view(binding.source);
        if(binding.role.equals("tabs"))nativeToolbar.topTabStyle=EdgeEntry.nativeBindings.field("iconStyle").get(binding.source);
        Object capture=nativeToolbar.capture(view,binding.source);
        return new NativeActions(binding.role,view,binding.source,capture,null);
    }
    void park(){
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        if(originalParent!=null){originalParent.addView(view,Math.min(originalIndex,originalParent.getChildCount()),originalParams);view.setVisibility(originalVisibility);}
    }
    @Override public void close(){
        if(closed)return;closed=true;
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        if(originalParent!=null){
            originalParent.addView(view,Math.min(originalIndex,originalParent.getChildCount()),originalParams);view.setVisibility(originalVisibility);
            if(!role.startsWith("ntp_")&&!role.equals("menu"))try{EdgeEntry.nativeBindings.method("iconOrientation").invoke(coordinator,view.getResources().getConfiguration().orientation);}catch(ReflectiveOperationException e){android.util.Log.e("EdgeBar","Native button presentation release",e);}
            return;
        }
        try{destroy.invoke(coordinator);}catch(Exception error){android.util.Log.w("EdgeBar","Native action release: "+role,error);}
    }
}
