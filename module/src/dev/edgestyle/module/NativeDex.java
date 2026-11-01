package dev.edgestyle.module;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.*;

/** Reads the installed APK's type signatures and call sites for semantic lookup. */
final class NativeDex {
    final Map<String,Type> types=new LinkedHashMap<>();
    private Dex loaded;
    private byte[] data;

    NativeDex(String... apks)throws IOException {
        for(String apk:apks)try(ZipFile zip=new ZipFile(apk)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();
                if(entry.getName().matches("classes\\d*\\.dex"))new Dex(apk,entry.getName(),zip.getInputStream(entry).readAllBytes());
            }
        }
        loaded=null;data=null;
    }
    Type type(String name){return types.get(name);}
    Type findType(String role,Predicate<Type> test){return one(types.values(),test,role);}
    MethodRef definition(MethodRef reference){
        for(Type type=type(reference.owner);type!=null;type=type(type.parent))
            for(MethodRef method:type.methods)if(method.name.equals(reference.name)&&method.signature.equals(reference.signature))return method;
        return reference;
    }
    static <T>T one(Collection<T> values,Predicate<T> test,String role){
        T result=null;
        for(T value:values)if(test.test(value)){
            if(result!=null)throw new IllegalStateException("Multiple native matches: "+role);
            result=value;
        }
        if(result==null)throw new IllegalStateException("Native match required: "+role);
        return result;
    }
    boolean extendsType(String name,String parent){
        for(Type type=type(name);type!=null;type=type(type.parent))if(type.name.equals(parent))return true;
        return false;
    }
    final class Type {
        final String name,parent;
        final List<FieldRef> fields=new ArrayList<>();
        final List<MethodRef> methods=new ArrayList<>();
        Type(String name,String parent){this.name=name;this.parent=parent;}
        boolean hasField(String type){return fields.stream().anyMatch(f->f.type.equals(type));}
        MethodRef method(String role,Predicate<MethodRef> test){return one(methods,test,role);}
        FieldRef field(String type){return one(fields,f->f.type.equals(type)&&!Modifier.isStatic(f.flags),name+" field "+type);}
        Class<?> load(ClassLoader loader)throws ClassNotFoundException{return classFor(name,loader);}
    }
    static final class FieldRef {
        final String owner,name,type;
        int flags;
        FieldRef(String owner,String name,String type){this.owner=owner;this.name=name;this.type=type;}
        Field reflect(ClassLoader loader)throws ReflectiveOperationException{
            Field field=classFor(owner,loader).getDeclaredField(name);field.setAccessible(true);return field;
        }
    }
    final class MethodRef {
        final Dex dex;
        final String owner,name,signature,result;
        final List<String> params;
        int flags,code;
        private List<Use> uses;
        MethodRef(Dex dex,String owner,String name,String signature){
            this.dex=dex;this.owner=owner;this.name=name;this.signature=signature;
            int end=signature.indexOf(')');result=signature.substring(end+1);params=descriptors(signature.substring(1,end));
        }
        boolean shape(String result,int parameters){return this.result.equals(result)&&params.size()==parameters&&!name.startsWith("<");}
        boolean signature(String signature){return this.signature.equals(signature);}
        List<Use> uses(){
            if(uses!=null)return uses;
            uses=new ArrayList<>();if(code==0)return uses;
            byte[] bytes=dex.bytes();int end=code+16+2*i32(bytes,code+12);
            for(int at=code+16;at<end;){
                int unit=u16(bytes,at),op=unit&255,width=WIDTH[op];
                if(op==0&&unit!=0){
                    int count=u16(bytes,at+2);
                    width=unit==0x100?4+count*2:unit==0x200?2+count*4:4+(u16(bytes,at+2)*i32(bytes,at+4)+1)/2;
                }
                if(op>=0x52&&op<=0x6d)uses.add(new Use(op,dex.fields[u16(bytes,at+2)]));
                else if(op>=0x6e&&op<=0x72||op>=0x74&&op<=0x78)uses.add(new Use(op,dex.methods[u16(bytes,at+2)]));
                else if(op==0x22)uses.add(new Use(op,dex.names[u16(bytes,at+2)]));
                at+=2*width;
            }
            return uses;
        }
        List<MethodRef> calls(){
            List<MethodRef> result=new ArrayList<>();
            for(Use use:uses())if(use.value instanceof NativeDex.MethodRef&&!result.contains(use.value))result.add((MethodRef)use.value);
            return result;
        }
        boolean calls(String owner,String name){return calls().stream().anyMatch(m->m.owner.equals(owner)&&m.name.equals(name));}
        boolean calls(MethodRef method){return calls().stream().anyMatch(m->{MethodRef target=definition(m);return target.owner.equals(method.owner)&&target.name.equals(method.name)&&target.signature.equals(method.signature);});}
        boolean resource(String name){return uses().stream().anyMatch(u->u.value instanceof FieldRef&&((FieldRef)u.value).name.equals(name));}
        boolean reads(String type){return uses().stream().anyMatch(u->u.op>=0x52&&u.op<=0x58&&u.value instanceof FieldRef&&((FieldRef)u.value).type.equals(type));}
        boolean writes(String owner,String type){return uses().stream().anyMatch(u->u.op>=0x59&&u.op<=0x5f&&u.value instanceof FieldRef&&((FieldRef)u.value).owner.equals(owner)&&((FieldRef)u.value).type.equals(type));}
        Method reflect(ClassLoader loader)throws ReflectiveOperationException{
            Class<?>[] parameters=new Class<?>[params.size()];for(int i=0;i<parameters.length;i++)parameters[i]=classFor(params.get(i),loader);
            Method method=classFor(owner,loader).getDeclaredMethod(name,parameters);method.setAccessible(true);return method;
        }
        Constructor<?> constructor(ClassLoader loader)throws ReflectiveOperationException{
            Class<?>[] parameters=new Class<?>[params.size()];for(int i=0;i<parameters.length;i++)parameters[i]=classFor(params.get(i),loader);
            Constructor<?> constructor=classFor(owner,loader).getDeclaredConstructor(parameters);constructor.setAccessible(true);return constructor;
        }
        @Override public String toString(){return owner+"->"+name+signature;}
    }
    static final class Use {
        final int op;
        final Object value;
        Use(int op,Object value){this.op=op;this.value=value;}
    }
    private final class Dex {
        final String apk,entry;
        final String[] names;
        final FieldRef[] fields;
        final MethodRef[] methods;
        Dex(String apk,String entry,byte[] bytes){
            this.apk=apk;this.entry=entry;loaded=this;data=bytes;
            String[] strings=new String[i32(bytes,0x38)];int start=i32(bytes,0x3c);
            for(int i=0;i<strings.length;i++){
                int[] at={i32(bytes,start+4*i)};uleb(bytes,at);int end=at[0];while(bytes[end]!=0)end++;
                strings[i]=new String(bytes,at[0],end-at[0],StandardCharsets.UTF_8);
            }
            names=new String[i32(bytes,0x40)];start=i32(bytes,0x44);
            for(int i=0;i<names.length;i++)names[i]=strings[i32(bytes,start+4*i)];
            String[] protos=new String[i32(bytes,0x48)];start=i32(bytes,0x4c);
            for(int i=0;i<protos.length;i++){
                int at=start+12*i,args=i32(bytes,at+8);StringBuilder proto=new StringBuilder("(");
                if(args!=0)for(int j=0;j<i32(bytes,args);j++)proto.append(names[u16(bytes,args+4+2*j)]);
                protos[i]=proto.append(')').append(names[i32(bytes,at+4)]).toString();
            }
            fields=new FieldRef[i32(bytes,0x50)];start=i32(bytes,0x54);
            for(int i=0;i<fields.length;i++){int at=start+8*i;fields[i]=new FieldRef(names[u16(bytes,at)],strings[i32(bytes,at+4)],names[u16(bytes,at+2)]);}
            methods=new MethodRef[i32(bytes,0x58)];start=i32(bytes,0x5c);
            for(int i=0;i<methods.length;i++){int at=start+8*i;methods[i]=new MethodRef(this,names[u16(bytes,at)],strings[i32(bytes,at+4)],protos[u16(bytes,at+2)]);}
            int count=i32(bytes,0x60);start=i32(bytes,0x64);
            for(int i=0;i<count;i++){
                int at=start+32*i;Type type=new Type(names[i32(bytes,at)],i32(bytes,at+8)==-1?null:names[i32(bytes,at+8)]);types.put(type.name,type);
                int offset=i32(bytes,at+24);if(offset==0)continue;
                int[] position={offset},sizes=new int[4];for(int j=0;j<4;j++)sizes[j]=uleb(bytes,position);
                for(int j=0;j<2;j++){int index=0;for(int k=0;k<sizes[j];k++){index+=uleb(bytes,position);FieldRef field=fields[index];field.flags=uleb(bytes,position);type.fields.add(field);}}
                for(int j=2;j<4;j++){int index=0;for(int k=0;k<sizes[j];k++){index+=uleb(bytes,position);MethodRef method=methods[index];method.flags=uleb(bytes,position);method.code=uleb(bytes,position);type.methods.add(method);}}
            }
        }
        byte[] bytes(){
            if(loaded!=this)try(ZipFile zip=new ZipFile(apk);InputStream stream=zip.getInputStream(zip.getEntry(entry))){data=stream.readAllBytes();loaded=this;}
            catch(IOException error){throw new UncheckedIOException(error);}
            return data;
        }
    }
    static Class<?> classFor(String descriptor,ClassLoader loader)throws ClassNotFoundException{
        switch(descriptor){
            case "V":return void.class;case "Z":return boolean.class;case "B":return byte.class;case "S":return short.class;
            case "C":return char.class;case "I":return int.class;case "J":return long.class;case "F":return float.class;case "D":return double.class;
            default:return Class.forName(descriptor.startsWith("[")?descriptor.replace('/','.'):descriptor.substring(1,descriptor.length()-1).replace('/','.'),false,loader);
        }
    }
    static List<String> descriptors(String sequence){
        List<String> result=new ArrayList<>();for(int i=0;i<sequence.length();){int start=i;while(sequence.charAt(i)=='[')i++;i=sequence.charAt(i)=='L'?sequence.indexOf(';',i)+1:i+1;result.add(sequence.substring(start,i));}return result;
    }
    private static int u16(byte[] data,int at){return (data[at]&255)|((data[at+1]&255)<<8);}
    private static int i32(byte[] data,int at){return u16(data,at)|(u16(data,at+2)<<16);}
    private static int uleb(byte[] data,int[] position){int result=0,shift=0,b;do{b=data[position[0]++]&255;result|=(b&127)<<shift;shift+=7;}while(b>=128);return result;}
    private static final int[] WIDTH={1,1,2,3,1,2,3,1,2,3,1,1,1,1,1,1,1,1,1,2,3,2,2,3,5,2,2,3,2,1,1,2,2,1,2,2,3,3,3,1,1,2,3,3,3,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,3,3,3,3,3,1,3,3,3,3,3,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,4,4,3,3,2,2};
}
