package dev.edgestyle.module;

final class NativeRoleProbe {
    public static void main(String[] args)throws Exception{
        long start=System.nanoTime();NativeDex dex=new NativeDex(args);NativeRoles roles=new NativeRoles(dex);
        roles.methods.forEach((role,method)->System.out.println(role+" = "+method));
        roles.fields.forEach((role,field)->System.out.println(role+" = "+field.owner+"->"+field.name+":"+field.type));
        System.out.println("Resolved "+roles.methods.size()+" methods, "+roles.fields.size()+" fields in "+((System.nanoTime()-start)/1000000)+" ms");
    }
}
