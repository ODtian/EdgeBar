package dev.edgestyle.module;

import java.util.*;
import dev.edgestyle.module.NativeDex.*;

/** Resolves toolbar responsibilities from native view types, resources and calls. */
final class NativeRoles {
    static final String TOOLBAR="Lorg/chromium/chrome/browser/toolbar/top/EdgeToolbarPhone;";
    static final String LOCATION="Lorg/chromium/chrome/browser/omnibox/LocationBarPhone;";
    static final String TAB="Lorg/chromium/chrome/browser/tab/Tab;";
    static final String PROVIDER="Lorg/chromium/chrome/browser/ActivityTabProvider;";
    static final String VIEW="Landroid/view/View;",CONTEXT="Landroid/content/Context;",CLICK="Landroid/view/View$OnClickListener;";
    static final String OVERFLOW="Lorg/chromium/chrome/browser/edge_bottombar/EdgeToolBarOverflowButton;";
    static final String TAB_BUTTON="Lorg/chromium/chrome/browser/edge_bottombar/TabStackButton;";
    static final String TOOLBAR_LAYOUT="Lorg/chromium/chrome/browser/toolbar/top/ToolbarLayout;";
    static final String BOTTOM="Lorg/chromium/chrome/browser/edge_bottombar/EdgeBottomNavBarLayout;";
    final Map<String,MethodRef> methods=new LinkedHashMap<>();
    final Map<String,FieldRef> fields=new LinkedHashMap<>();
    final List<MethodRef> deoptimize=new ArrayList<>();
    final List<FieldRef> relativeGoneMargins=new ArrayList<>();
    final Type tabs,menu,icon,orientationIcon;

    NativeRoles(NativeDex dex){
        tabs=dex.findType("tab coordinator",t->t.methods.stream().anyMatch(m->m.name.equals("<init>")&&m.params.size()==5&&m.params.get(0).equals(TAB_BUTTON)));
        menu=dex.findType("menu coordinator",t->t.methods.stream().anyMatch(m->m.name.equals("<init>")&&m.signature("("+OVERFLOW+")V")));
        MethodRef tabsConstructor=tabs.method("tab constructor",m->m.name.equals("<init>"));methods.put("tabsConstructor",tabsConstructor);
        methods.put("menuConstructor",menu.method("menu constructor",m->m.name.equals("<init>")));
        orientationIcon=dex.type(tabs.parent);icon=dex.type(orientationIcon.parent);
        MethodRef bindTabs=tabs.method("tab binding",m->m.shape("V",7)&&m.params.get(1).equals(CLICK)&&m.params.contains(PROVIDER));methods.put("bindTabs",bindTabs);
        MethodRef bindMenu=menu.method("menu binding",m->m.shape("V",6)&&m.params.get(0).equals(CLICK)&&m.params.get(1).equals("Landroid/view/View$OnLongClickListener;"));methods.put("bindMenu",bindMenu);

        Type navigation=dex.findType("navigation coordinator",t->orientationIcon.name.equals(t.parent)&&t.methods.stream().anyMatch(m->m.name.equals("<init>")&&m.params.size()==4&&m.params.get(0).equals("Lcom/microsoft/edge/components/browser_ui/edge_ui/EdgeFontIconView;")&&m.params.get(2).equals("Z")&&m.params.get(3).equals("Z")));
        methods.put("bindNavigation",navigation.method("navigation binding",m->m.shape("V",4)&&m.params.get(3).equals(PROVIDER)));
        Type assemblyOwner=dex.findType("top toolbar coordinator",t->t.hasField(TOOLBAR_LAYOUT)&&t.hasField("Lorg/chromium/chrome/browser/toolbar/top/ToolbarControlContainer;")&&t.methods.stream().anyMatch(m->m.shape("V",0)&&m.resource("edge_toolbar_tab_stack_button_stub")));
        MethodRef assemble=assemblyOwner.method("compact row assembly",m->m.shape("V",0)&&m.resource("edge_toolbar_tab_stack_button_stub"));methods.put("assemble",assemble);
        methods.put("bindAssembly",assemblyOwner.method("compact row binding",m->m.shape("V",0)&&m.calls(bindTabs)));
        fields.put("assemblyToolbar",assemblyOwner.field(TOOLBAR_LAYOUT));
        MethodRef orientation=NativeDex.one(assemble.calls(),m->m.signature("("+CONTEXT+")I"),"native orientation");
        methods.put("orientation",dex.definition(orientation));

        boolean afterPlus=false;Type plus=null;
        for(Use use:assemble.uses()){
            if(use.value instanceof FieldRef&&((FieldRef)use.value).name.equals("edge_toolbar_plus_button_stub"))afterPlus=true;
            if(afterPlus&&use.op==0x22&&dex.extendsType((String)use.value,orientationIcon.name)){plus=dex.type((String)use.value);break;}
        }
        if(plus==null)throw new IllegalStateException("Native plus coordinator");
        methods.put("bindPlus",plus.method("new tab binding",m->m.shape("V",4)&&m.params.get(3).equals(PROVIDER)));
        methods.put("plusState",plus.method("new tab presentation",m->m.signature("("+TAB+")V")));
        methods.put("iconOrientation",orientationIcon.method("icon orientation",m->m.signature("(I)V")));
        MethodRef configuration=dex.findType("toolbar configuration observer",t->t.methods.stream().anyMatch(m->m.name.equals("onConfigurationChanged")&&m.signature("(Landroid/content/res/Configuration;)V")&&m.calls(methods.get("iconOrientation"))&&m.calls(TOOLBAR,"setOrientation")))
            .method("toolbar configuration callback",m->m.name.equals("onConfigurationChanged")&&m.signature("(Landroid/content/res/Configuration;)V"));
        methods.put("toolbarConfiguration",configuration);
        MethodRef iconDestroy=icon.method("icon release",m->m.shape("V",0)&&m.calls(VIEW,"setOnClickListener")&&m.calls(VIEW,"setOnLongClickListener"));
        methods.put("tabsDestroy",tabs.method("tab release",m->m.shape("V",0)&&m.calls(iconDestroy)));
        methods.put("menuDestroy",menu.method("menu release",m->m.shape("V",0)&&m.writes(menu.name,bindMenu.params.get(2))));
        String style=tabsConstructor.params.get(1);
        fields.put("iconStyle",icon.field(style));fields.put("tabsHistory",tabs.field(tabsConstructor.params.get(2)));
        methods.put("tabsPalette",icon.method("tab palette",m->m.shape("V",0)&&m.calls().stream().anyMatch(c->c.owner.equals(style)&&c.signature("("+VIEW+"Z)V"))));
        methods.put("menuPalette",menu.method("menu palette",m->m.shape("V",0)&&m.calls().stream().anyMatch(c->c.owner.equals("Lorg/chromium/ui/modelutil/PropertyModel;")&&c.result.equals("V")&&c.params.size()==2&&c.params.get(1).equals("Z"))));

        Type toolbar=dex.type(TOOLBAR);
        Type phone=dex.type("Lorg/chromium/chrome/browser/toolbar/top/ToolbarPhone;");
        MethodRef drawAddress=phone.method("native address drawing",m->m.signature("(Landroid/graphics/Canvas;J)Z"));
        MethodRef drawBackground=NativeDex.one(drawAddress.calls(),m->m.name.equals("draw")&&m.signature("(Landroid/graphics/Canvas;)V")&&!m.owner.startsWith("Landroid/"),"native address background drawing");
        methods.put("drawAddressBackground",dex.definition(drawBackground));fields.put("addressBackgroundDrawable",phone.field(drawBackground.owner));deoptimize.add(drawAddress);
        String gurl="Lorg/chromium/url/GURL;";
        fields.put("toolbarDataProvider",NativeDex.one(dex.type(TOOLBAR_LAYOUT).fields,field->{
            Type provider=dex.type(field.type);
            return provider!=null&&provider.methods.stream().anyMatch(m->m.shape(TAB,0))&&provider.methods.stream().anyMatch(m->m.shape(gurl,0));
        },"toolbar address data provider"));
        Type addressModel=dex.type("Lorg/chromium/chrome/browser/toolbar/LocationBarModel;");
        fields.put("visibleUrl",addressModel.field(gurl));
        fields.put("addressTab",addressModel.field(TAB));
        MethodRef tabUrl=dex.type("Lorg/chromium/chrome/browser/tab/TabImpl;").method("tab URL",m->m.name.equals("getUrl")&&m.shape(gurl,0));
        methods.put("webVisibleUrl",dex.definition(NativeDex.one(tabUrl.calls(),m->m.owner.equals("Lorg/chromium/content_public/browser/WebContents;")&&m.shape(gurl,0),"pending navigation URL")));
        String loadParams="Lorg/chromium/content_public/browser/LoadUrlParams;";
        Type creator=dex.findType("browser tab creator",t->t.hasField("Landroid/app/Activity;")&&t.hasField("Lorg/chromium/ui/base/WindowAndroid;")&&t.methods.stream().anyMatch(m->m.signature("("+loadParams+"Ljava/lang/String;I"+TAB+"ILandroid/content/Intent;Z)"+TAB)));
        MethodRef createTab=creator.method("browser tab creation",m->m.signature("("+loadParams+"Ljava/lang/String;I"+TAB+"ILandroid/content/Intent;Z)"+TAB));
        methods.put("createTab",createTab);fields.put("creatorIncognito",creator.field("Z"));
        fields.put("loadUrlSpec",(FieldRef)NativeDex.one(tabUrl.uses(),u->u.op==0x54&&u.value instanceof FieldRef&&((FieldRef)u.value).owner.equals(loadParams)&&((FieldRef)u.value).type.equals("Ljava/lang/String;"),"navigation URL field").value);
        for(Type type:dex.types.values())if(dex.extendsType(type.name,creator.name))for(MethodRef method:type.methods)if(method.calls(createTab))deoptimize.add(method);
        for(String side:new String[]{"Left","Right"}){
            MethodRef setter=toolbar.method(side+" native icon list",m->m.name.equals("set"+side+"IconCoordinators"));
            Use store=NativeDex.one(setter.uses(),u->u.op==0x5b&&u.value instanceof FieldRef&&((FieldRef)u.value).type.equals("Ljava/util/List;"),side+" icon list field");
            fields.put(side.toLowerCase()+"Icons",(FieldRef)store.value);
        }
        Type location=dex.type(LOCATION);
        MethodRef constraints=location.method("address constraints",m->m.signature("(Z)V")&&m.calls(VIEW,"getLayoutParams")&&m.calls(VIEW,"setPaddingRelative")&&m.calls(VIEW,"setLayoutParams"));methods.put("addressConstraints",constraints);
        MethodRef alignmentUpdate=location.method("address alignment update",m->m.shape("V",0)&&m.calls(constraints));methods.put("addressAlignmentUpdate",alignmentUpdate);
        List<MethodRef> alignmentCalls=new ArrayList<>(alignmentUpdate.calls());
        for(MethodRef call:alignmentUpdate.calls())if(call.owner.equals(LOCATION)&&call.signature("()Z")){
            MethodRef predicate=dex.definition(call);alignmentCalls.addAll(predicate.calls());deoptimize.add(predicate);
        }
        methods.put("addressPageAlignment",dex.definition(NativeDex.one(alignmentCalls,m->m.signature("(I)Z"),"address page alignment")));
        MethodRef presentation=location.method("address text presentation",m->m.shape("V",4)&&m.params.get(0).equals("Z")&&m.params.get(1).equals("Z")&&m.params.get(2).equals("I")&&m.calls("Landroid/widget/TextView;","setGravity")&&m.calls("Landroid/widget/TextView;","setHorizontallyScrolling"));
        methods.put("addressPresentation",presentation);
        MethodRef measure=location.method("native address measurement",m->m.signature("(II)V")&&m.calls(presentation));
        Type addressParams=dex.type(presentation.params.get(3));
        MethodRef resolve=addressParams.method("relative address constraints",m->m.name.equals("resolveLayoutDirection"));
        for(Use use:resolve.uses())if(use.op==0x52&&use.value instanceof FieldRef){
            FieldRef field=(FieldRef)use.value;
            if(field.owner.equals(addressParams.name)&&!java.lang.reflect.Modifier.isFinal(field.flags)&&!relativeGoneMargins.contains(field)&&
                resolve.uses().stream().filter(u->u.op==0x52&&u.value==field).count()==2)relativeGoneMargins.add(field);
        }
        for(String slot:new String[]{"TopBar","SearchBox"}){
            Type page=dex.type("Lorg/chromium/chrome/browser/edge_ntp/v2/view/NewTabPageV2"+slot+";");
            methods.put(slot+"Palette",page.method(slot+" palette",m->m.shape("V",0)&&(slot.equals("TopBar")?m.calls("Landroid/widget/ImageView;","setImageTintList"):m.resource("ic_fluent_camera_24_regular")&&m.calls("Landroid/widget/ImageView;","setImageDrawable"))));
        }
        Type footer=dex.findType("bottom toolbar coordinator",t->t.hasField(BOTTOM)&&t.hasField("Lorg/chromium/chrome/browser/edge_bottombar/EdgeBottomNavBarResourceFrameLayout;"));
        MethodRef updateFooter=footer.method("native bottom controls visibility",m->m.shape("V",0)&&m.reads(BOTTOM)&&m.calls(VIEW,"setVisibility")&&m.writes(footer.name,"I"));methods.put("updateFooter",updateFooter);
        fields.put("footerView",footer.field(BOTTOM));
        methods.put("toolbarFormFactor",dex.definition(NativeDex.one(configuration.calls(),m->m.owner.equals("Lorg/chromium/ui/base/DeviceFormFactor;")&&m.signature("("+CONTEXT+")Z"),"native toolbar form factor")));
        for(String[] resource:new String[][]{{"tabsLayout","edge_tab_stack_button"},{"menuLayout","edge_toolbar_overflow_button_mvp"},{"iconPadding","edge_toolbar_icon_padding"}}){
            List<FieldRef> matches=new ArrayList<>();
            for(Type type:dex.types.values())for(FieldRef field:type.fields)if(field.name.equals(resource[1])&&field.type.equals("I")&&java.lang.reflect.Modifier.isStatic(field.flags))matches.add(field);
            fields.put(resource[0],NativeDex.one(matches,field->true,resource[1]));
        }

        Type manager=dex.findType("toolbar lifecycle owner",t->t.hasField(assemblyOwner.name)&&t.hasField(TOOLBAR_LAYOUT)&&t.hasField("Lorg/chromium/chrome/browser/toolbar/LocationBarModel;"));
        for(MethodRef method:manager.methods)if(method.name.equals("<init>")||method.calls().stream().anyMatch(c->c.owner.equals(assemblyOwner.name)))deoptimize.add(method);
        deoptimize.add(assemble);deoptimize.add(methods.get("bindAssembly"));deoptimize.add(measure);deoptimize.add(methods.get("addressAlignmentUpdate"));deoptimize.add(presentation);deoptimize.add(updateFooter);deoptimize.add(configuration);
    }
}
