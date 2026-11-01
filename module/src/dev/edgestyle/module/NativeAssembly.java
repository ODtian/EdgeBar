package dev.edgestyle.module;

/** Selects Edge's existing compact-row button assembly. */
final class NativeAssembly {
    private static final ThreadLocal<Boolean> assembling=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> creatingPageActions=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> suppressingFooter=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> configuringToolbar=ThreadLocal.withInitial(()->false);
    static boolean active(){return assembling.get();}
    static void enter(){assembling.set(true);}
    static void leave(){assembling.remove();}
    static boolean creating(){return creatingPageActions.get();}
    static void startPageActions(){creatingPageActions.set(true);}
    static void finishPageActions(){creatingPageActions.remove();}
    static boolean footerActive(){return suppressingFooter.get();}
    static void enterFooter(){suppressingFooter.set(true);}
    static void leaveFooter(){suppressingFooter.remove();}
    // The callback selects its row mode once; nested controls keep their own device mode.
    static boolean takeConfigurationFormFactor(){boolean configuring=configuringToolbar.get();configuringToolbar.remove();return configuring;}
    static void enterConfiguration(){configuringToolbar.set(true);}
    static void leaveConfiguration(){configuringToolbar.remove();}
}
