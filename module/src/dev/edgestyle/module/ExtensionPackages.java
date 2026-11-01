package dev.edgestyle.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.zip.*;

final class ExtensionPackages {
    static final int MAX_FILE=128*1024*1024;
    static final class Prepared {
        File file;JSONObject manifest;String id="",source="";
        String title(){return manifest.optString("name","扩展")+"  "+manifest.optString("version","");}
        String permissions(){
            List<String> values=new ArrayList<>();
            for(String key:new String[]{"permissions","host_permissions"}){JSONArray array=manifest.optJSONArray(key);if(array!=null)for(int i=0;i<array.length();i++)values.add(array.optString(i));}
            return values.isEmpty()?"原生安装器将显示扩展权限":android.text.TextUtils.join("\n",values);
        }
    }
    static byte[] readLimited(InputStream in,int limit)throws IOException {
        if(in==null)throw new IOException("文件读取失败");
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[32768];int count,total=0;
            while((count=input.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("扩展包超过大小限制");out.write(buffer,0,count);}
            return out.toByteArray();
        }
    }
    static Prepared local(Context context,Uri uri)throws Exception {
        return prepare(context,readLimited(context.getContentResolver().openInputStream(uri),MAX_FILE),"本地扩展","");
    }
    static Prepared remote(Context context,String input)throws Exception {
        StoreSource source=StoreSource.resolve(input);
        URL url=new URL(source.downloadUrl);
        byte[] data=null;
        for(int redirects=0;redirects<8;redirects++) {
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36");
            try {
                int code=connection.getResponseCode();
                if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("下载重定向缺少地址");url=new URL(url,location);boolean microsoftDelivery=source.name.equals("Edge Add-ons")&&url.getHost().toLowerCase(Locale.ROOT).endsWith(".dl.delivery.mp.microsoft.com");if("http".equals(url.getProtocol())&&!microsoftDelivery)url=new URL("https",url.getHost(),url.getPort()==80?-1:url.getPort(),url.getFile());if(!"https".equals(url.getProtocol())&&!("http".equals(url.getProtocol())&&microsoftDelivery))throw new IOException("扩展下载协议无效");continue;}
                if(code!=200)throw new IOException(source.name+" 下载返回 HTTP "+code);
                data=readLimited(connection.getInputStream(),MAX_FILE);break;
            }finally{connection.disconnect();}
        }
        if(data==null)throw new IOException("下载重定向次数过多");
        return prepare(context,data,source.name,source.id);
    }
    static Prepared prepare(Context context,byte[] data,String source,String expectedId)throws Exception {
        Prepared result=new Prepared();result.source=source;
        byte[] archive;
        if(data.length>=12&&data[0]=='C'&&data[1]=='r'&&data[2]=='2'&&data[3]=='4') {
            ByteBuffer header=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int version=header.getInt(4),offset;
            if(version==3){int size=header.getInt(8);if(size<0||size>1024*1024||12L+size>data.length)throw new IOException("CRX3 头部无效");offset=12+size;byte[] signed=field(Arrays.copyOfRange(data,12,offset),10000);if(signed!=null){byte[] id=field(signed,1);if(id!=null&&id.length==16)result.id=extensionId(id);}}
            else if(version==2){if(data.length<16)throw new IOException("CRX2 头部无效");int a=header.getInt(8),b=header.getInt(12);if(a<0||b<0||16L+a+b>data.length)throw new IOException("CRX2 头部无效");offset=16+a+b;result.id=extensionId(Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(data,16,16+a)),16));}
            else throw new IOException("扩展包 CRX 版本无效");
            if(!expectedId.isEmpty()&&!expectedId.equals(result.id))throw new IOException("下载包的扩展 ID 与商店详情不一致");
            archive=Arrays.copyOfRange(data,offset,data.length);
        } else {
            archive=normalizeArchive(data);
            JSONObject manifest=manifest(archive);
            String name=manifest.optString("name","");
            String keyName="key_"+hex(MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8)));
            SharedPreferences keys=context.getSharedPreferences("edgebar_dev_keys",Context.MODE_PRIVATE);
            KeyPair pair;
            String privateValue=keys.getString(keyName+"_private",null),publicValue=keys.getString(keyName+"_public",null);
            if(privateValue==null||publicValue==null){KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);pair=generator.generateKeyPair();keys.edit().putString(keyName+"_private",Base64.encodeToString(pair.getPrivate().getEncoded(),Base64.NO_WRAP)).putString(keyName+"_public",Base64.encodeToString(pair.getPublic().getEncoded(),Base64.NO_WRAP)).commit();}
            else {KeyFactory factory=KeyFactory.getInstance("RSA");pair=new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.decode(publicValue,Base64.DEFAULT))),factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privateValue,Base64.DEFAULT))));}
            manifest.put("key",Base64.encodeToString(pair.getPublic().getEncoded(),Base64.NO_WRAP));
            archive=replaceManifest(archive,manifest);
            byte[] id=Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded()),16);
            result.id=extensionId(id);data=sign(archive,pair,id);
        }
        result.manifest=manifest(archive);
        localizeTitle(result.manifest,archive);
        if(result.manifest.optString("version","").isEmpty())throw new IOException("manifest.json 缺少版本");
        File folder=new File(context.getCacheDir(),"edgebar/extensions");if(!folder.mkdirs()&&!folder.isDirectory())throw new IOException("扩展缓存目录创建失败");
        result.file=new File(folder,UUID.randomUUID()+".crx");try(FileOutputStream out=new FileOutputStream(result.file)){out.write(data);}
        return result;
    }
    static void localizeTitle(JSONObject manifest,byte[] archive)throws Exception {
        String name=manifest.optString("name","");if(!name.startsWith("__MSG_")||!name.endsWith("__"))return;
        String key=name.substring(6,name.length()-2);String locale=Locale.getDefault().toString();
        for(String candidate:new String[]{locale,Locale.getDefault().getLanguage(),manifest.optString("default_locale","en"),"en"}){
            String path="_locales/"+candidate+"/messages.json";try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry entry;while((entry=in.getNextEntry())!=null)if(path.equals(entry.getName())){JSONObject messages=new JSONObject(new String(readEntry(in,4*1024*1024),StandardCharsets.UTF_8));for(java.util.Iterator<String> it=messages.keys();it.hasNext();){String id=it.next();if(id.equalsIgnoreCase(key)){String title=messages.getJSONObject(id).optString("message","");if(!title.isEmpty()){manifest.put("name",title);return;}}}}}
        }
    }
    static JSONObject manifest(byte[] archive)throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)if("manifest.json".equals(entry.getName()))return new JSONObject(new String(readEntry(zip,4*1024*1024),StandardCharsets.UTF_8));}
        throw new IOException("扩展包需要根目录 manifest.json");
    }
    static byte[] readEntry(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count,total=0;while((count=in.read(buffer))!=-1){total+=count;if(total>limit)throw new IOException("扩展文件超过大小限制");out.write(buffer,0,count);}return out.toByteArray();
    }
    static byte[] normalizeArchive(byte[] source)throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();int total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(source))){ZipEntry e;while((e=zip.getNextEntry())!=null){if(e.isDirectory())continue;String name=e.getName().replace('\\','/');if(name.startsWith("/")||Arrays.asList(name.split("/")).contains(".."))throw new IOException("ZIP 文件路径无效");byte[] bytes=readEntry(zip,MAX_FILE);total+=bytes.length;if(total>MAX_FILE)throw new IOException("解压后扩展超过大小限制");if(entries.put(name,bytes)!=null)throw new IOException("ZIP 存在重复文件");}}
        String prefix="";
        if(!entries.containsKey("manifest.json")){List<String> manifests=new ArrayList<>();for(String name:entries.keySet())if(name.endsWith("/manifest.json"))manifests.add(name);if(manifests.size()!=1)throw new IOException("请选择包含单个扩展的 ZIP / 源码目录");String path=manifests.get(0);prefix=path.substring(0,path.length()-"manifest.json".length());}
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:entries.entrySet())if(e.getKey().startsWith(prefix)){String name=e.getKey().substring(prefix.length());zip.putNextEntry(new ZipEntry(name));zip.write(e.getValue());zip.closeEntry();}}
        return out.toByteArray();
    }
    static byte[] replaceManifest(byte[] source,JSONObject manifest)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(source));ZipOutputStream zip=new ZipOutputStream(out)){ZipEntry entry;while((entry=in.getNextEntry())!=null){zip.putNextEntry(new ZipEntry(entry.getName()));zip.write(entry.getName().equals("manifest.json")?manifest.toString().getBytes(StandardCharsets.UTF_8):readEntry(in,MAX_FILE));zip.closeEntry();}}
        return out.toByteArray();
    }
    static byte[] sign(byte[] zip,KeyPair pair,byte[] id)throws Exception {
        byte[] signed=bytesField(1,id);ByteArrayOutputStream payload=new ByteArrayOutputStream();payload.write("CRX3 SignedData\0".getBytes(StandardCharsets.UTF_8));payload.write(le(signed.length));payload.write(signed);payload.write(zip);
        Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(payload.toByteArray());byte[] signature=signer.sign();
        ByteArrayOutputStream proof=new ByteArrayOutputStream();proof.write(bytesField(1,pair.getPublic().getEncoded()));proof.write(bytesField(2,signature));
        ByteArrayOutputStream header=new ByteArrayOutputStream();header.write(bytesField(2,proof.toByteArray()));header.write(bytesField(10000,signed));
        ByteArrayOutputStream result=new ByteArrayOutputStream();result.write(new byte[]{'C','r','2','4'});result.write(le(3));result.write(le(header.size()));header.writeTo(result);result.write(zip);return result.toByteArray();
    }
    static byte[] le(int value){return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();}
    static void varint(ByteArrayOutputStream out,int value){while((value&~127)!=0){out.write((value&127)|128);value>>>=7;}out.write(value);}
    static byte[] bytesField(int number,byte[] value)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();varint(out,number*8+2);varint(out,value.length);out.write(value);return out.toByteArray();}
    static byte[] field(byte[] data,int wanted)throws IOException {
        int[] p={0};while(p[0]<data.length){int tag=readVarint(data,p),kind=tag&7;if(kind!=2)throw new IOException("CRX 消息格式无效");int length=readVarint(data,p);if(length<0||p[0]+(long)length>data.length)throw new IOException("CRX 消息长度无效");if(tag>>>3==wanted)return Arrays.copyOfRange(data,p[0],p[0]+length);p[0]+=length;}return null;
    }
    static int readVarint(byte[] data,int[] p)throws IOException{int result=0;for(int shift=0;shift<35;shift+=7){if(p[0]>=data.length)throw new IOException("CRX 消息截断");int b=data[p[0]++]&255;result|=(b&127)<<shift;if((b&128)==0)return result;}throw new IOException("CRX 整数格式无效");}
    static String extensionId(byte[] hash){StringBuilder s=new StringBuilder();for(byte b:hash){s.append((char)('a'+((b&255)>>>4))).append((char)('a'+(b&15)));}return s.toString();}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
