import dev.netlens.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sanitized router-reply reproductions: wire validity is separate from useful identity. */
public final class ResolverTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static byte[] encoded(byte[]...labels)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] label:labels){out.write(label.length);out.write(label);}out.write(0);return out.toByteArray();
    }
    private static byte[] name(String value)throws Exception{List<byte[]> labels=new ArrayList<>();for(String part:value.split("\\."))labels.add(part.getBytes(StandardCharsets.US_ASCII));return encoded(labels.toArray(new byte[0][]));}
    private static byte[] reply(byte[]...values)throws Exception{
        byte[] question=ReverseDns.query("192.0.2.20",0x216);question[2]=(byte)0x85;question[3]=(byte)0x80;question[7]=(byte)values.length;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(question);DataOutputStream out=new DataOutputStream(bytes);
        for(byte[] value:values){out.writeShort(0xc00c);out.writeShort(12);out.writeShort(1);out.writeInt(120);out.writeShort(value.length);out.write(value);}return bytes.toByteArray();
    }
    private static ReverseDns.Result parse(byte[] b){return ReverseDns.response(b,b.length,0x216,"192.0.2.20");}
    public static void main(String[] args)throws Exception{
        for(String value:new String[]{"02:11:22:33:44:55","02-11-22-33-44-55","021122334455","00:00:00:00:00:00","FF:FF:FF:FF:FF:FF","192.0.2.20"}){
            ReverseDns.Result r=parse(reply(name(value)));check(r.status==ReverseDns.Status.POSITIVE,"A matching PTR identifier is valid DNS data");check(r.name.isEmpty(),"A MAC-shaped or address-only identifier is not promoted to a hostname");check(r.identifier.equals(value),"Retain the reported identifier without asserting a hardware address");
        }
        byte[] host=name("fixture.home"),mac=name("02:11:22:33:44:55");
        for(byte[][] order:new byte[][][]{{host,mac},{mac,host}}){ReverseDns.Result r=parse(reply(order));check(r.name.equals("fixture.home")&&r.identifier.equals("02:11:22:33:44:55"),"A useful hostname wins in either PTR arrival order and the identifier remains separate");}
        for(byte[][] order:new byte[][][]{{name("zeta.home"),name("Alpha.home")},{name("Alpha.home"),name("zeta.home")}})check(parse(reply(order)).name.equals("Alpha.home"),"Deterministic selection of multiple PTR names");
        for(byte[][] labels:new byte[][][]{{new byte[]{'a','.','b'}},{new byte[]{'a','\\','b'}},{new byte[]{'a',0,'b'}},{new byte[]{'a',10,'b'}},{new byte[]{(byte)0xc3,(byte)0xa9}},{new byte[]{(byte)0xff}}}){
            ReverseDns.Result r=parse(reply(encoded(labels)));check(r.status==ReverseDns.Status.POSITIVE&&r.name.isEmpty(),"Valid binary DNS labels remain metadata rather than malformed packets or hostnames");check(r.identifier.indexOf('\\')>=0&&!r.identifier.contains("\n")&&!r.identifier.contains("\0"),"Escape non-host bytes without introducing controls or ambiguous label separators");
        }
        check(parse(reply(encoded(new byte[]{'a','.','b'}))).identifier.equals("a\\046b"),"A dot inside one wire label is distinct from a label separator");
        check(parse(reply(name("a.b"))).name.equals("a.b"),"Actual label separators still form a hostname");
        byte[] good=reply(mac);for(int i=0;i<good.length;i++)check(ReverseDns.response(good,i,0x216,"192.0.2.20").status==ReverseDns.Status.INVALID,"Every truncated router-reply prefix is rejected");
        check(ReverseDns.response(good,good.length,0x217,"192.0.2.20").status==ReverseDns.Status.INVALID,"Transaction ID correlation remains required");
        check(ReverseDns.response(good,good.length,0x216,"192.0.2.21").status==ReverseDns.Status.INVALID,"Question-to-address correlation remains required");
        byte[] bad=good.clone();bad[3]=(byte)0x83;check(parse(bad).status==ReverseDns.Status.INVALID,"NXDOMAIN plus an asserted PTR answer is contradictory");
        bad=good.clone();bad[2]|=2;check(parse(bad).status==ReverseDns.Status.INVALID,"UDP truncation is not accepted as a complete answer");
        byte[] error=ReverseDns.query("192.0.2.20",0x216);error[2]=(byte)0x85;error[3]=(byte)0x82;check(parse(error).status==ReverseDns.Status.ERROR&&parse(error).reason.contains("2"),"A correlated server error is distinguished from malformed DNS");
        byte[] negative=ReverseDns.query("192.0.2.20",0x216);negative[2]=(byte)0x85;negative[3]=(byte)0x83;check(parse(negative).status==ReverseDns.Status.NEGATIVE,"A well-formed no-name response is normal completion");
        for(boolean reverse:new boolean[]{false,true}){
            IdentityHints hints=new IdentityHints();if(reverse)hints.offer("Name","hubv2-fixture","DNS hostname",40);hints.offer("Name","0123456789ABCDEF-FEDCBA9876543210","mDNS instance",65);check(hints.offer("Name","hubv2-fixture","DNS hostname",40).value.equals("hubv2-fixture"),"Useful DNS hostname beats an opaque service identifier in either order");
            check(hints.offer("Name","Kitchen hub","Friendly TXT",90).value.equals("Kitchen hub"),"A useful friendly name retains priority");
        }
        check(IdentityHints.specificName("Cafe-Beef")&&!IdentityHints.specificName("01234567-89ab-cdef-0123-456789abcdef"),"Short human labels are not discarded as opaque IDs");
        DiscoveryHealth health=new DiscoveryHealth();health.resolving("fixture","_http._tcp.");health.resolveFailed("fixture","No usable data after 4001 ms (registered); first: Android registration error 4");String status=health.sources().get("mDNS _http._tcp.");check(status.startsWith("Failed")&&status.contains("registered")&&!status.contains("Service resolution failed"),"Preserve the specific final outcome instead of replacing it with a generic label");
        Random random=new Random(216);for(int i=0;i<20000;i++){byte[] b=new byte[random.nextInt(4200)];random.nextBytes(b);ReverseDns.response(b,b.length,0x216,"192.0.2.20");}check(true,"Malformed-reply decoder samples terminate");
        System.out.println("PASS · "+checks+" resolver assertions and 20,000 malformed-input runs");
    }
}
