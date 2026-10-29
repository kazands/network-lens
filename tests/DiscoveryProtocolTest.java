import dev.netlens.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Wire-format fixtures and malformed input for new discovery paths; no live network. */
public final class DiscoveryProtocolTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static byte[] response(int id)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(id);out.writeShort(0x8400);out.writeShort(0);out.writeShort(1);out.writeInt(0);
        byte[] question=NetBios.query(id);out.write(question,12,34);out.writeShort(0x21);out.writeShort(1);out.writeInt(0);out.writeShort(1+18+46);
        out.writeByte(1);byte[] name=new byte[15];Arrays.fill(name,(byte)' ');System.arraycopy("OFFICE-PC".getBytes(StandardCharsets.US_ASCII),0,name,0,9);out.write(name);out.writeByte(0);out.writeShort(0x0400);
        out.write(new byte[]{0x00,0x11,0x22,0x33,0x44,0x55});out.write(new byte[40]);return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        check(Address.mac("00-11-22-aa-bb-cc").equals("00:11:22:AA:BB:CC"),"Normalize actual hardware address");
        check(Address.mac("001122aabbcc").equals("00:11:22:AA:BB:CC"),"Normalize advertised compact hardware ID");
        for(String invalid:new String[]{"","00:00:00:00:00:00","02:00:00:00:00:00","FF:FF:FF:FF:FF:FF","01:00:5e:00:00:01","x;id","00:11:22:33:44"})check(Address.mac(invalid).isEmpty(),"Reject placeholder, group, or invalid MAC");
        check(Address.mac("02:11:22:33:44:55").equals("02:11:22:33:44:55"),"Allow real locally administered addresses");
        check(Address.inPrefix("192.168.1.8","192.168.1.10",24),"IPv4 prefix membership");
        check(!Address.inPrefix("192.168.2.8","192.168.1.10",24),"Exclude other IPv4 subnet");
        check(Address.inPrefix("fe80::1234%wlan0","fe80::5678%wlan0",64),"IPv6 link-local prefix and scope");
        check(!Address.inPrefix("fd00:2::8","fd00:1::10",64),"Exclude another IPv6 subnet");
        check(Address.bytes("example.com")==null&&Address.bytes("fe80::1;id")==null,"Numeric addresses only; no DNS/shell input");
        check(Address.compare("192.168.1.9","192.168.1.10")<0,"Numeric IPv4 sorting");
        check(Address.compare("fd00::9","fd00::10")<0,"Numeric IPv6 sorting");
        byte[] query=NetBios.query(0x1234);check(query.length==50&&(query[0]&255)==0x12&&(query[1]&255)==0x34&&query[47]==0x21,"RFC 1002 node-status question");
        byte[] packet=response(0x1234);NetBios.Result parsed=NetBios.parse(packet,packet.length,0x1234);
        check(parsed!=null&&parsed.name.equals("OFFICE-PC"),"Decode NetBIOS workstation name");
        check(parsed.mac.equals("00:11:22:33:44:55"),"Decode reported NetBIOS adapter address");
        check(NetBios.parse(packet,packet.length,0x9876)==null,"Reject mismatched transaction");
        for(int n=0;n<packet.length;n++)check(NetBios.parse(packet,n,0x1234)==null,"Reject truncated status response at "+n);
        byte[] bad=packet.clone();bad[2]=0;check(NetBios.parse(bad,bad.length,0x1234)==null,"Reject a query presented as a response");
        bad=packet.clone();bad[55]=(byte)255;check(NetBios.parse(bad,bad.length,0x1234)==null,"Reject oversized RDATA");
        bad=packet.clone();bad[56]=(byte)255;check(NetBios.parse(bad,bad.length,0x1234)==null,"Reject oversized name count");
        byte[] ssdp=("HTTP/1.1 200 OK\r\nSERVER: Linux / UPnP\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nLOCATION: http://169.254.169.254/private\r\nUSN: uuid:fixture\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        Map<String,String> headers=Ssdp.parse(ssdp,ssdp.length);check(headers.get("server").equals("Linux / UPnP")&&headers.get("usn").equals("uuid:fixture"),"Read whitelisted SSDP metadata");
        check(headers.containsKey("location")&&headers.size()==4,"Retain bounded raw location for separate same-responder policy");
        check(UpnpDescription.target(headers.get("location"),java.net.InetAddress.getByName("192.168.1.2"))==null,"Cross-device metadata URL rejected before networking");
        check(Ssdp.parse("NOTIFY * HTTP/1.1".getBytes(StandardCharsets.US_ASCII),17).isEmpty(),"Reject unrelated SSDP messages");
        Random random=new Random(1234);for(int n=0;n<10000;n++){byte[] fuzz=new byte[random.nextInt(4097)];random.nextBytes(fuzz);NetBios.parse(fuzz,fuzz.length,0x1234);Ssdp.parse(fuzz,fuzz.length);}check(true,"10,000 malformed packet pairs do not crash or loop");
        System.out.println("PASS · "+checks+" address and discovery-protocol checks (including 10,000 malformed packet pairs)");
    }
}
