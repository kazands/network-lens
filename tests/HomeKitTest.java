import dev.netlens.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** HomeKit facts and current-packet association at the untrusted DNS boundary. */
public final class HomeKitTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] ascii(String s){return s.getBytes(StandardCharsets.US_ASCII);}
    private static Map<String,byte[]> attrs(String... values){
        Map<String,byte[]> out=new LinkedHashMap<>();
        for(int n=0;n<values.length;n+=2)out.put(values[n],ascii(values[n+1]));
        return out;
    }
    private static void name(DataOutputStream out,String value)throws Exception{
        for(String label:value.split("\\.")){byte[] b=label.getBytes(StandardCharsets.UTF_8);out.writeByte(b.length);out.write(b);}out.writeByte(0);
    }
    private static byte[] dnsName(String value)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();name(new DataOutputStream(b),value);return b.toByteArray();}
    private static byte[] rr(String owner,int kind,byte[] value,int ttl)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
        name(out,owner);out.writeShort(kind);out.writeShort(0x8001);out.writeInt(ttl);out.writeShort(value.length);out.write(value);return b.toByteArray();
    }
    private static byte[] srv(String host)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
        out.writeShort(0);out.writeShort(0);out.writeShort(51827);name(out,host);return b.toByteArray();
    }
    private static byte[] txt(String... fields)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();for(String field:fields){byte[] value=ascii(field);b.write(value.length);b.write(value);}return b.toByteArray();
    }
    private static byte[] packet(List<byte[]> records)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
        out.writeShort(0);out.writeShort(0x8400);out.writeShort(0);out.writeShort(records.size());out.writeInt(0);
        for(byte[] record:records)out.write(record);return b.toByteArray();
    }
    private static List<byte[]> records(String ip,String host)throws Exception{
        String type="_hap._tcp.local",instance="Hall light."+type;InetAddress address=InetAddress.getByName(ip);
        return new ArrayList<>(Arrays.asList(rr(type,12,dnsName(instance),120),rr(instance,33,srv("lamp.local"),120),
            rr(instance,16,txt("md=Lamp model","ci=5","sf=5","id=AA:BB:CC:DD:EE:FF"),120),
            rr(host,address instanceof Inet4Address?1:28,address.getAddress(),120)));
    }
    private static MdnsPacket.Result parse(List<byte[]> records)throws Exception{byte[] p=packet(records);return MdnsPacket.parse(p,p.length);}
    public static void main(String[] args)throws Exception{
        HomeKitInfo.Report light=HomeKitInfo.parse("_hap._tcp.",attrs("ci","5","sf","5","id","AA:BB:CC:DD:EE:FF"));
        check(light.kind.equals("HomeKit light")&&light.setup.equals("Not paired"),"Explicit category and unpaired bit are decoded");
        check(HomeKitInfo.parse("_hap._udp.",attrs("CI","6","SF","4")).kind.equals("HomeKit lock"),"UDP HomeKit and ASCII key case work");
        for(int flags=0;flags<256;flags++)check(HomeKitInfo.parse("_hap._tcp.",attrs("sf",String.valueOf(flags))).setup.equals((flags&1)==1?"Not paired":"Paired"),"sf is a bitmask, not a two-value enum");
        for(String invalid:new String[]{"","-1","+1","1 "," 1","1\n","0x1","256","999","1000","1.0","\u0000","١"}){
            HomeKitInfo.Report unknown=HomeKitInfo.parse("_hap._tcp.",attrs("ci",invalid,"sf",invalid));
            check(unknown.kind.equals("HomeKit accessory")&&unknown.setup.isEmpty(),"Malformed bytes do not invent category or paired state");
        }
        check(HomeKitInfo.parse("_hap._tcp.",Collections.emptyMap()).setup.isEmpty(),"Absent status remains absent");
        check(HomeKitInfo.parse("_hap._tcp.",attrs("ci","255")).kind.equals("HomeKit accessory"),"Unknown category remains generic");
        check(HomeKitInfo.parse("_http._tcp.",attrs("ci","5","sf","1")).kind.isEmpty(),"HomeKit keys are not applied to unrelated services");
        check(HomeKitInfo.parse("_hap._tcp.",attrs("ci","5","CI","6")).kind.equals("HomeKit accessory"),"Conflicting case variants remain unknown");
        check(HomeKitInfo.parse("_hap._tcp.",attrs("ci","5","CI","5")).kind.equals("HomeKit light"),"Identical duplicate reports agree");
        Map<String,byte[]> large=attrs("sf","0");for(int n=0;n<128;n++)large.put("field"+n,ascii("x"));
        check(HomeKitInfo.parse("_hap._tcp.",large).setup.isEmpty(),"Bound untrusted attribute maps");
        Map<String,byte[]> oversize=attrs("sf","0");oversize.put("sf",new byte[10000]);
        check(HomeKitInfo.parse("_hap._tcp.",oversize).setup.isEmpty(),"Oversized flags are not parsed");
        check(HomeKitInfo.category(17).equals("HomeKit camera")&&HomeKitInfo.category(18).equals("HomeKit video doorbell"),"Camera and doorbell category numbers are distinct");
        check(new HashSet<>(LocalServices.INITIAL).size()==LocalServices.INITIAL.size(),"No duplicate explicit browse types");
        for(String type:new String[]{"_hap._tcp.","_hap._udp.","_matter._tcp.","_matterc._udp.","_meshcop._udp."})check(LocalServices.INITIAL.contains(type),"Smart-home types have explicit bounded queries");
        try{LocalServices.INITIAL.add("_unbounded._tcp.");throw new AssertionError("Mutable service list");}catch(UnsupportedOperationException expected){check(true,"Initial list cannot be expanded by a caller");}
        for(String type:new String[]{"_hap._udp.","_matterc._udp.","_meshcop._udp.","_matter._tcp.","_http._udp."})check(!LocalServices.tcpConfirmation(type),"Do not confirm UDP services or Matter's _tcp convention with a TCP connection");
        check(LocalServices.tcpConfirmation("_hap._tcp.")&&LocalServices.tcpConfirmation("_http._tcp."),"Real TCP service confirmation is retained");
        InetAddress peer=InetAddress.getByName("192.168.1.20");
        List<byte[]> base=records(peer.getHostAddress(),"lamp.local");MdnsPacket.Result current=parse(base);
        check(current.directServices(peer).size()==1,"One complete current packet associates its SRV target with its sender");
        MdnsPacket.Service service=current.directServices(peer).get(0);
        check(service.name.equals("Hall light")&&service.port==51827&&service.txtReceived&&service.ptrReceived,"Complete instance, port and TXT/PTR evidence are retained");
        check(HomeKitInfo.parse(service.type,service.attributes).kind.equals("HomeKit light"),"Wire category reaches the mapper");
        check(service.copy().ptrReceived&&service.copy().txtReceived,"A service copy preserves packet evidence flags");
        check(current.directServices(InetAddress.getByName("192.168.1.21")).isEmpty(),"A different responder cannot borrow this advertised address");
        check(current.directServices(InetAddress.getByName("192.168.1.1")).isEmpty(),"A proxy reply is not direct accessory evidence");
        List<byte[]> proxy=new ArrayList<>(base);proxy.add(rr("router.local",1,InetAddress.getByName("192.168.1.1").getAddress(),120));
        check(parse(proxy).directServices(InetAddress.getByName("192.168.1.1")).isEmpty(),"A proxy's unrelated A record cannot satisfy the SRV target");
        check(parse(records(peer.getHostAddress(),"other.local")).directServices(peer).isEmpty(),"Address owner must match the exact service host");
        for(int missing=0;missing<4;missing++){
            List<byte[]> incomplete=new ArrayList<>(base);incomplete.remove(missing);
            check(parse(incomplete).directServices(peer).isEmpty(),"Missing PTR, SRV, TXT or A cannot complete the fallback");
        }
        String type="_hap._tcp.local",instance="Hall light."+type;
        for(int expired=0;expired<4;expired++){
            List<byte[]> goodbye=new ArrayList<>(base);
            byte[] replacement=expired==0?rr(type,12,dnsName(instance),0):expired==1?rr(instance,33,srv("lamp.local"),0):expired==2?rr(instance,16,txt("ci=5"),0):rr("lamp.local",1,peer.getAddress(),0);
            goodbye.set(expired,replacement);
            check(parse(goodbye).directServices(peer).isEmpty(),"TTL-zero records cannot complete current evidence");
        }
        List<byte[]> wrong=new ArrayList<>(base);wrong.set(2,rr("Other."+type,16,txt("ci=5"),120));
        check(parse(wrong).directServices(peer).isEmpty(),"TXT for a different instance cannot be combined with the SRV");
        MdnsPacket.Result standalone=new MdnsPacket.Result();standalone.services.put("old",service.copy());
        check(standalone.directServices(peer).isEmpty(),"A cached service object alone is not a current packet");
        InetAddress v6=InetAddress.getByName("fd00::20");
        check(parse(records(v6.getHostAddress(),"lamp.local")).directServices(v6).size()==1,"Same-sender AAAA association works independently of IPv6 routing policy");
        for(int a=0;a<4;a++)for(int b=0;b<4;b++)if(a!=b)for(int c=0;c<4;c++)if(c!=a&&c!=b)for(int d=0;d<4;d++)if(d!=a&&d!=b&&d!=c)
            check(parse(Arrays.asList(base.get(a),base.get(b),base.get(c),base.get(d))).directServices(peer).size()==1,"All 24 record orders preserve the same direct association");
        byte[] full=packet(base);
        for(int n=0;n<full.length;n++)check(MdnsPacket.parse(full,n).directServices(peer).isEmpty(),"Truncated packet never returns partial identity");
        check(MdnsPacket.parse(new byte[8193],8193).addresses.isEmpty(),"Packet-size limit also bounds retained addresses");
        Random random=new Random(301);
        for(int n=0;n<10000;n++){byte[] fuzz=new byte[random.nextInt(1024)];random.nextBytes(fuzz);MdnsPacket.parse(fuzz,fuzz.length).directServices(peer);HomeKitInfo.parse("_hap._tcp.",Collections.singletonMap("sf",fuzz));}
        check(true,"Malformed DNS and TXT data cannot crash or hang the mapper");
        System.out.println("PASS · "+checks+" HomeKit assertions plus 10,000 malformed DNS/TXT inputs");
    }
}
