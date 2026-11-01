package dev.edgestyle.module;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class StoreSource {
    public final String name,id,downloadUrl,detailUrl;
    private StoreSource(String name,String id,String download,String detail){this.name=name;this.id=id;downloadUrl=download;detailUrl=detail;}
    public static StoreSource resolve(String value) {
        URI uri=URI.create(value.trim());
        String scheme=uri.getScheme(),host=uri.getHost();
        if(!"https".equalsIgnoreCase(scheme)||host==null)throw new IllegalArgumentException("请输入 HTTPS 扩展详情或 CRX 地址");
        host=host.toLowerCase(Locale.ROOT);
        String path=uri.getPath()==null?"":uri.getPath();
        String[] segments=path.split("/");
        String id="";
        for(String segment:segments)if(segment.matches("[a-p]{32}"))id=segment;
        boolean chrome=host.equals("chromewebstore.google.com")||host.equals("chrome.google.com");
        boolean edge=host.equals("microsoftedge.microsoft.com");
        if(chrome||edge) {
            if(id.isEmpty())throw new IllegalArgumentException("详情地址需要包含 32 位扩展 ID");
            String x=URLEncoder.encode("id="+id+"&installsource=ondemand&uc",StandardCharsets.UTF_8);
            String endpoint=chrome?"https://clients2.google.com/service/update2/crx":"https://edge.microsoft.com/extensionwebstorebase/v1/crx";
            String download=endpoint+"?response=redirect&prodversion=153.0.0.0&acceptformat=crx3,crx2&x="+x;
            return new StoreSource(chrome?"Chrome Web Store":"Edge Add-ons",id,download,value);
        }
        return new StoreSource("自定义扩展来源","",value,value);
    }
    public static String idFromPage(String url) {try{return resolve(url).id;}catch(RuntimeException e){return "";}}
}
