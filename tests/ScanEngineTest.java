import dev.netlens.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class ScanEngineTest {
    private static int checks;
    private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static byte[] utf(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static void name(DataOutputStream out,String name)throws Exception{for(String label:name.split("\\.")){byte[] b=utf(label);out.writeByte(b.length);out.write(b);}out.writeByte(0);}
    private static byte[] response(String owner,int kind,int ttl,byte[] payload)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.writeShort(0);out.writeShort(0x8400);out.writeShort(0);out.writeShort(1);out.writeInt(0);name(out,owner);out.writeShort(kind);out.writeShort(0x8001);out.writeInt(ttl);out.writeShort(payload.length);out.write(payload);return b.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        DiscoveryWork work=new DiscoveryWork();CountDownLatch namesStarted=new CountDownLatch(4),hold=new CountDownLatch(1),description=new CountDownLatch(1),confirmation=new CountDownLatch(1);
        try{
            for(int i=0;i<4;i++)check(work.submit(DiscoveryWork.Lane.NAMES,()->{namesStarted.countDown();try{hold.await();}catch(InterruptedException stopped){Thread.currentThread().interrupt();}}),"Start a blocking DNS task");
            check(namesStarted.await(2,TimeUnit.SECONDS),"All name workers occupied");
            for(int i=0;i<144;i++)check(work.submit(DiscoveryWork.Lane.NAMES,()->{}),"Bounded DNS queue accepts its capacity");
            check(!work.submit(DiscoveryWork.Lane.NAMES,()->{}),"DNS overflow is rejected, never moved to another lane");
            check(work.submit(DiscoveryWork.Lane.DESCRIPTIONS,description::countDown)&&description.await(1,TimeUnit.SECONDS),"A useful description runs while every DNS worker and queue slot is blocked");
            check(work.submit(DiscoveryWork.Lane.CONFIRM,confirmation::countDown)&&confirmation.await(1,TimeUnit.SECONDS),"Fresh service confirmation has independent capacity");
        }finally{hold.countDown();work.shutdownNow();}
        check(work.awaitTermination(2,TimeUnit.SECONDS)&&work.isTerminated(),"Stop drains all worker lanes within one shared deadline");check(!work.submit(DiscoveryWork.Lane.CAST,()->{}),"No late Cast work after shutdown");
        check(CastInfo.name(utf("{\"name\":\"Living room TV\",\"location\":{\"latitude\":123},\"ssid\":\"private\",\"mac_address\":\"00:00:00:00:00:00\"}")).equals("Living room TV"),"Only a useful reported name survives unrelated sensitive fields");
        check(CastInfo.name(utf("{\"name\":\"TV \\uD83D\\uDCFA\"}")).equals("TV 📺"),"Valid Unicode pairs are preserved");
        for(String bad:new String[]{"{}","[]","null","{\"name\":\"Android\"}","{\"name\":\"A\",\"name\":\"B\"}","{\"n\\u0061me\":\"A\",\"name\":\"B\"}","{\"name\":\"A\"} trailing","{\"nested\":{\"name\":\"Unrelated name\"}}","{\"name\":42}","{\"name\":\"\\uD800\"}","{\"name\":\"\\q\"}","{\"name\":\"A\",\"other\":NaN}","{\"name\":\"A\",\"other\":01}"})check(CastInfo.name(utf(bad)).isEmpty(),"Reject malformed, ambiguous or non-useful Cast name: "+bad);
        check(CastInfo.name(new byte[]{(byte)0xc3,40}).isEmpty(),"Malformed UTF-8 is rejected");check(CastInfo.name(new byte[CastInfo.MAX_BODY+1]).isEmpty(),"Cast body size is bounded");
        String nested="0";for(int i=0;i<32;i++)nested="["+nested+"]";check(CastInfo.name(utf("{\"name\":\"Name\",\"deep\":"+nested+"}")).isEmpty(),"Nested irrelevant fields cannot exhaust the Java stack");
        check(CastInfo.needsName("Chromecast-abcdef12345")&&!CastInfo.needsName("Kitchen speaker"),"Avoid requesting names already supplied by better sources");
        for(String generic:new String[]{"Chromecast","Android TV"})for(boolean reverse:new boolean[]{false,true}){
            IdentityHints hints=new IdentityHints();int priority=generic.equals("Chromecast")?90:85;
            if(reverse)hints.offer("Name","Living room TV","Cast device reported name",70);
            hints.offer("Name",generic,"mDNS advertised name",priority);
            IdentityHints.Hint chosen=hints.offer("Name","Living room TV","Cast device reported name",70);
            check(chosen.value.equals("Living room TV"),"Useful fallback beats a stronger generic name in either arrival order");
            check(hints.observations().size()==2,"Generic advertised name remains available as provenance");
            check(hints.offer("Name","Useful TXT name","mDNS friendly name",90).value.equals("Useful TXT name"),"Useful stronger advertised name still wins over fallback");
        }
        IdentityHints model=new IdentityHints();model.offer("Model","Chromecast","Explicit model",90);check(model.offer("Model","Living room TV","Weak inference",50).value.equals("Chromecast"),"Name placeholder rules do not demote valid model names");
        IdentityHints user=new IdentityHints();user.offer("Name","Android TV","User name",100);check(user.offer("Name","Living room TV","Cast device reported name",70).value.equals("Android TV"),"Explicit user name remains authoritative");
        String request=new String(CastInfo.request(InetAddress.getByName("fd00::20")),StandardCharsets.US_ASCII);check(request.contains("Host: [fd00:0:0:0:0:0:0:20]:8008")&&request.startsWith("GET /setup/eureka_info?options=detail HTTP/1.1"),"Cast request has a fixed local path and valid IPv6 Host header");
        byte[] a=response("fixture.local",1,120,new byte[]{(byte)192,0,2,20});check(MdnsPacket.parse(a,a.length).freshResponse,"Fresh A reply admits its actual sender without trusting the advertised address");
        byte[] aaaa=response("fixture.local",28,120,new byte[16]);check(MdnsPacket.parse(aaaa,aaaa.length).freshResponse,"Fresh AAAA reply is recognized");
        byte[] txt=response("Living room._googlecast._tcp.local",16,120,new byte[]{7,'f','n','=','N','a','m','e'});MdnsPacket.Result parsed=MdnsPacket.parse(txt,txt.length);check(parsed.freshResponse&&parsed.serviceTypes.contains("_googlecast._tcp."),"Fresh TXT with a spaced instance name also reveals its service type");
        MdnsPacket.Service current=parsed.services.get("living room|_googlecast._tcp.");check(current!=null&&current.name.equals("Living room")&&new String(current.attributes.get("fn"),StandardCharsets.UTF_8).equals("Name"),"Current wire TXT retains its exact service and reported name");
        ByteArrayOutputStream srvBytes=new ByteArrayOutputStream();DataOutputStream srv=new DataOutputStream(srvBytes);srv.writeInt(0);srv.writeShort(8008);name(srv,"fixture.local");byte[] packet=response("Room._googlecast._tcp.local",33,120,srvBytes.toByteArray());check(MdnsPacket.parse(packet,packet.length).freshResponse,"A validated SRV response is fresh evidence");MdnsPacket.Service location=MdnsPacket.parse(packet,packet.length).services.get("room|_googlecast._tcp.");check(location!=null&&location.host.equals("fixture.local")&&location.port==8008,"Current wire SRV supplies its own hostname and port");
        for(byte[] bad:new byte[][]{response("fixture.local",1,0,new byte[4]),response("fixture.example",1,120,new byte[4]),response("fixture.local",1,120,new byte[3]),response("Room._http._tcp.local",16,120,new byte[]{5,1})})check(!MdnsPacket.parse(bad,bad.length).freshResponse,"No fresh evidence from goodbye, outside or malformed records");
        for(int i=0;i<txt.length;i++)check(!MdnsPacket.parse(txt,i).freshResponse,"Truncated response never confirms presence");
        byte[] q=MdnsPacket.query(Arrays.asList("_googlecast._tcp.local",ReverseDns.question("fd00::20")));check(q[5]==2&&q.length<1200,"Bounded dual-stack reverse and service questions");
        Random random=new Random(215);for(int i=0;i<20000;i++){byte[] fuzz=new byte[random.nextInt(9000)];random.nextBytes(fuzz);MdnsPacket.parse(fuzz,fuzz.length);CastInfo.name(fuzz);}check(true,"20,000 untrusted packet/JSON samples terminate safely");
        System.out.println("PASS · "+checks+" scan-engine assertions and 40,000 malformed-input decoder runs");
    }
}
