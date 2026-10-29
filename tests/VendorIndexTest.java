import dev.netlens.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class VendorIndexTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static byte[] data()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);String[] names={"Large vendor","Medium vendor","Small vendor","PRIVATE"};out.writeInt(0x444c5631);out.writeLong(1);out.writeInt(names.length);out.writeInt(4);
        for(String name:names){byte[] value=name.getBytes(StandardCharsets.UTF_8);out.writeShort(value.length);out.write(value);}
        String[] prefixes={"001122","0011223","001122334","001122335"};for(int n=0;n<4;n++){out.writeLong((Long.parseLong(prefixes[n],16)<<4)|prefixes[n].length());out.writeInt(n);}return bytes.toByteArray();
    }
    static boolean rejected(byte[] bytes){try{VendorIndex.read(new ByteArrayInputStream(bytes));return false;}catch(IOException e){return true;}}
    public static void main(String[] args)throws Exception{
        byte[] bytes=data();VendorIndex index=VendorIndex.read(new ByteArrayInputStream(bytes));
        check(index.lookup("00:11:22:33:44:55").equals("Small vendor"),"36-bit registry wins over broader prefixes");
        check(index.lookup("00:11:22:3F:00:00").equals("Medium vendor"),"28-bit registry wins over 24 bits");
        check(index.lookup("00-11-22-90-00-00").equals("Large vendor")&&index.lookup("001122900000").equals("Large vendor"),"Normalize valid reported MAC formats");
        check(index.lookup("00:11:22:33:50:00").isEmpty(),"A private assignment does not inherit the parent vendor");
        for(String value:new String[]{"02:11:22:33:44:55","01:11:22:33:44:55","FF:FF:FF:FF:FF:FF","00:00:00:00:00:00","02:00:00:00:00:00","invalid","192.168.2.60","00:11:22:33:44:55:66:77","","null"})check(index.lookup(value).isEmpty(),"Never infer vendors from local, multicast, invalid or unknown identifiers");
        check(index.lookup(null).isEmpty()&&VendorIndex.EMPTY.lookup("00:11:22:33:44:55").isEmpty(),"Missing registry/address is a normal unknown");
        for(int n=0;n<bytes.length;n++)check(rejected(Arrays.copyOf(bytes,n)),"Reject truncated index");
        byte[] trailing=Arrays.copyOf(bytes,bytes.length+1);check(rejected(trailing),"Reject trailing data");byte[] bad=bytes.clone();bad[0]=0;check(rejected(bad),"Reject unknown format");bad=bytes.clone();Arrays.fill(bad,12,16,(byte)127);check(rejected(bad),"Reject excessive organization count");bad=bytes.clone();bad[20]=127;bad[21]=127;check(rejected(bad),"Reject oversized organization strings");
        Random random=new Random(217);for(int n=0;n<10000;n++){byte[] input=new byte[random.nextInt(800)];random.nextBytes(input);check(rejected(input),"Malformed input is bounded");}
        try(InputStream input=Files.newInputStream(Paths.get("android/app/src/main/res/raw/ieee_mac_vendors.bin"))){VendorIndex real=VendorIndex.read(input);check(real.snapshot>0,"Bundled data snapshot is readable");check(real.lookup("30:05:5C:00:00:01").toLowerCase(Locale.ROOT).contains("brother"),"IEEE prefix fixture resolves to Brother");check(real.lookup("C0:51:7E:00:00:01").toLowerCase(Locale.ROOT).contains("hikvision"),"IEEE prefix fixture resolves to Hikvision");}
        System.out.println("PASS · "+checks+" vendor-index checks (including 10000 malformed inputs)");
    }
}
