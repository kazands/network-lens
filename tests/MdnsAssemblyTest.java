import dev.netlens.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Independent wire fixtures: split responses, isolation, lifetime and bounded questions. */
public final class MdnsAssemblyTest {
    private static int checks;
    private static final List<String> TYPE=Arrays.asList("_googlecast","_tcp","local");
    private static final List<String> INSTANCE=Arrays.asList("Living.room TV","_googlecast","_tcp","local");
    private static final List<String> HOST=Arrays.asList("cast-fixture","local");
    private static final InetAddress PEER=ip("192.0.2.20");
    private static InetAddress ip(String value){try{return InetAddress.getByName(value);}catch(Exception e){throw new AssertionError(e);}}
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static byte[] name(List<String> labels)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(String l:labels){byte[] v=l.getBytes(StandardCharsets.UTF_8);b.write(v.length);b.write(v);}b.write(0);return b.toByteArray();}
    private static byte[] txt(String... fields)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(String f:fields){byte[] v=f.getBytes(StandardCharsets.UTF_8);b.write(v.length);b.write(v);}return b.toByteArray();}
    private static byte[] srv(List<String> target)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(0);d.writeShort(8009);d.write(name(target));return b.toByteArray();}
    private static byte[] rr(List<String> owner,int kind,byte[] payload,long ttl,boolean flush)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.write(name(owner));d.writeShort(kind);d.writeShort(flush?0x8001:1);d.writeInt((int)ttl);d.writeShort(payload.length);d.write(payload);return b.toByteArray();}
    private static byte[] packet(byte[]... records)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeShort(0);d.writeShort(0x8400);d.writeShort(0);d.writeShort(records.length);d.writeInt(0);for(byte[] r:records)d.write(r);return b.toByteArray();}
    private static MdnsPacket.Result parse(byte[]... records)throws Exception{byte[] p=packet(records);return MdnsPacket.parse(p,p.length);}
    private static byte[][] parts(InetAddress address)throws Exception{return new byte[][]{rr(TYPE,12,name(INSTANCE),120,false),rr(INSTANCE,33,srv(HOST),120,true),rr(INSTANCE,16,txt("fn=Living room TV","md=Fixture television"),120,true),rr(HOST,address instanceof Inet4Address?1:28,address.getAddress(),120,true)};}
    private static List<MdnsPacket.Service> all(MdnsAssembly a,InetAddress peer,byte[][] p,long now)throws Exception{return a.accept(peer,parse(p),now);}
    public static void main(String[] args)throws Exception{
        byte[][] p=parts(PEER);long start=TimeUnit.SECONDS.toNanos(30);
        for(int i=0;i<4;i++)for(int j=0;j<4;j++)if(j!=i)for(int k=0;k<4;k++)if(k!=i&&k!=j)for(int l=0;l<4;l++)if(l!=i&&l!=j&&l!=k){
            MdnsAssembly a=new MdnsAssembly();int[] order={i,j,k,l};
            for(int n=0;n<4;n++){List<MdnsPacket.Service> out=a.accept(PEER,parse(p[order[n]]),start+n);check(out.size()==(n==3?1:0),"Only complete records resolve, in all 24 message orders");if(n==3){MdnsPacket.Service s=out.get(0);check(s.name.equals("Living.room TV")&&s.host.equals("cast-fixture.local")&&s.port==8009,"Literal dots stay inside the service instance");check(new String(s.attributes.get("md"),StandardCharsets.UTF_8).equals("Fixture television"),"Model comes from this peer's TXT record");}}
        }
        MdnsAssembly a=new MdnsAssembly();check(all(a,PEER,p,start).size()==1,"Same-packet general service resolves without Android NSD");
        check(all(new MdnsAssembly(),ip("192.0.2.1"),p,start).isEmpty(),"Proxy cannot borrow the advertised peer identity");
        a=new MdnsAssembly();a.accept(PEER,parse(p[0],p[1],p[3]),start);check(a.accept(ip("192.0.2.21"),parse(p[2]),start).isEmpty(),"TXT from another sender cannot join");
        check(a.accept(PEER,new MdnsPacket.Result(),start).isEmpty(),"Empty or cached objects never complete the assembly");
        a=new MdnsAssembly();a.accept(PEER,parse(p[0],p[1],p[2]),start);check(a.accept(PEER,parse(p[3]),start+TimeUnit.SECONDS.toNanos(10)).isEmpty(),"Ten-second assembly lifetime expires earlier records");
        a=new MdnsAssembly();a.accept(PEER,parse(rr(TYPE,12,name(INSTANCE),1,false),p[1],p[2]),start);check(a.accept(PEER,parse(p[3]),start+TimeUnit.SECONDS.toNanos(1)).isEmpty(),"Wire TTL shorter than safety window is honored");
        a=new MdnsAssembly();all(a,PEER,p,start);a.accept(PEER,parse(rr(TYPE,12,name(INSTANCE),0,false)),start+1);check(a.accept(PEER,parse(p[0]),start+2).isEmpty(),"Goodbye invalidates the instance details before it can return");
        for(int index=1;index<4;index++){
            a=new MdnsAssembly();all(a,PEER,p,start);byte[] gone=index==1?rr(INSTANCE,33,srv(HOST),0,true):index==2?rr(INSTANCE,16,txt("fn=Living room TV","md=Fixture television"),0,true):rr(HOST,1,PEER.getAddress(),0,true);
            check(a.accept(PEER,parse(gone),start+1).isEmpty(),"TTL-zero SRV/TXT/address prevents further association");
        }
        a=new MdnsAssembly();all(a,PEER,p,start);List<MdnsPacket.Service> refreshed=a.accept(PEER,parse(rr(INSTANCE,16,txt("fn=New name"),120,true)),start+TimeUnit.SECONDS.toNanos(2));
        check(refreshed.isEmpty(),"Conflicting TXT is withheld during cache-flush grace");
        refreshed=a.accept(PEER,parse(p[3]),start+TimeUnit.SECONDS.toNanos(3));
        check(refreshed.size()==1&&!refreshed.get(0).attributes.containsKey("md"),"Cache-flush TXT replaces the RRset; removed fields do not survive in the assembly");
        a=new MdnsAssembly();a.accept(PEER,parse(p[0],rr(INSTANCE,33,srv(HOST),120,false),p[2],p[3]),start);
        check(a.accept(PEER,parse(rr(INSTANCE,33,srv(Arrays.asList("other","local")),120,false)),start+1).isEmpty(),"Conflicting SRV records are not arbitrarily selected");
        a=new MdnsAssembly();a.accept(PEER,parse(p[0]),start);List<MdnsPacket.Question> q=a.due(false,start,4);
        check(q.size()==2&&q.stream().anyMatch(x->x.getKind()==33)&&q.stream().anyMatch(x->x.getKind()==16),"PTR triggers explicit missing SRV and TXT queries");
        check(q.get(0).getLabels().equals(INSTANCE),"Follow-up retains wire label boundaries");
        byte[] request=MdnsPacket.followUp(q);check((request[12]&255)=="Living.room TV".getBytes(StandardCharsets.UTF_8).length,"Query encodes dotted instance as one label");
        check(a.due(false,start+1,4).isEmpty()&&a.due(true,start+TimeUnit.SECONDS.toNanos(2),4).isEmpty(),"Retries are spaced and do not leak across IP families");
        check(a.due(false,start+TimeUnit.MILLISECONDS.toNanos(1200),4).size()==2,"One timed retry is available");
        check(a.due(false,start+TimeUnit.SECONDS.toNanos(3),4).isEmpty()&&!a.pending(start+TimeUnit.SECONDS.toNanos(3)),"Unanswered records cannot cause unbounded queries or wait");
        a.accept(PEER,parse(p[1],p[2]),start+TimeUnit.SECONDS.toNanos(3));q=a.due(false,start+TimeUnit.SECONDS.toNanos(3),4);check(q.size()==1&&q.get(0).getKind()==1&&q.get(0).getLabels().equals(HOST),"SRV yields a targeted missing A question");
        check(a.accept(PEER,parse(p[3]),start+TimeUnit.SECONDS.toNanos(4)).size()==1&&!a.pending(start+TimeUnit.SECONDS.toNanos(4)),"Complete service cancels unnecessary follow-ups");
        MdnsAssembly timed=new MdnsAssembly();all(timed,PEER,p,start);
        timed.accept(PEER,parse(rr(INSTANCE,16,txt("fn=Updated name"),120,true)),start+TimeUnit.SECONDS.toNanos(2));
        check(timed.pending(start+TimeUnit.MILLISECONDS.toNanos(2500)),"Pending grace prevents an early quiet-window exit");
        check(timed.ready(start+TimeUnit.MILLISECONDS.toNanos(2500)).isEmpty(),"Timer never selects conflicting TXT prematurely");
        Map<InetAddress,List<MdnsPacket.Service>> ready=timed.ready(start+TimeUnit.SECONDS.toNanos(3));
        check(ready.size()==1&&new String(ready.get(PEER).get(0).attributes.get("fn"),StandardCharsets.UTF_8).equals("Updated name"),"Expiry alone publishes the now-unambiguous service");
        check(timed.ready(start+TimeUnit.MILLISECONDS.toNanos(3100)).isEmpty()&&!timed.pending(start+TimeUnit.MILLISECONDS.toNanos(3100)),"Repeated timer ticks neither republish nor extend scan quiet time");
        timed.close();check(timed.ready(start+TimeUnit.SECONDS.toNanos(4)).isEmpty(),"Stop discards timer completions");
        InetAddress v6=ip("fd00::20");a=new MdnsAssembly();byte[][] six=parts(v6);a.accept(v6,parse(six[0],six[1],six[2]),start);q=a.due(true,start,4);check(q.size()==1&&q.get(0).getKind()==28,"IPv6 sender gets an AAAA follow-up");check(a.accept(v6,parse(six[3]),start+1).size()==1,"Same-sender IPv6 assembly works");
        MdnsAssembly burst=new MdnsAssembly();burst.accept(v6,parse(six[0],six[1],six[3]),start);
        byte[] alternate=rr(HOST,28,ip("fd00::21").getAddress(),120,true);
        burst.accept(v6,parse(alternate),start+TimeUnit.MILLISECONDS.toNanos(200));
        check(burst.accept(v6,parse(six[2]),start+TimeUnit.MILLISECONDS.toNanos(400)).size()==1,"Same-burst cache flush preserves an earlier matching AAAA member");
        burst.accept(v6,parse(alternate),start+TimeUnit.SECONDS.toNanos(2));
        check(burst.accept(v6,parse(six[2]),start+TimeUnit.MILLISECONDS.toNanos(2500)).size()==1,"Old RRset member remains only during one-second grace");
        check(burst.accept(v6,parse(six[2]),start+TimeUnit.SECONDS.toNanos(3)).isEmpty(),"Unrefreshed old address expires at the grace deadline");
        check(burst.due(true,start+TimeUnit.SECONDS.toNanos(3),4).stream().anyMatch(x->x.getKind()==28),"An alternate address cannot suppress a missing sender-address retry");
        check(burst.accept(v6,parse(six[3]),start+TimeUnit.MILLISECONDS.toNanos(3100)).size()==1,"A refreshed matching address repairs the association");
        MdnsAssembly refreshedBurst=new MdnsAssembly();all(refreshedBurst,v6,six,start);refreshedBurst.accept(v6,parse(alternate),start+TimeUnit.SECONDS.toNanos(2));refreshedBurst.accept(v6,parse(six[3]),start+TimeUnit.MILLISECONDS.toNanos(2400));
        check(refreshedBurst.accept(v6,parse(six[2]),start+TimeUnit.SECONDS.toNanos(4)).size()==1,"A matching member refreshed during the burst keeps its own TTL");
        List<String> dottedAlias=Arrays.asList("Living","room TV","_googlecast","_tcp","local");
        check(new MdnsAssembly().accept(PEER,parse(p[0],rr(dottedAlias,33,srv(HOST),120,true),p[2],p[3]),start).isEmpty(),"Different DNS label boundaries cannot borrow a matching dotted instance");
        List<String> unicode=Arrays.asList("Café TV","_http","_tcp","local");byte[] unicodeQuery=MdnsPacket.followUp(Arrays.asList(new MdnsPacket.Question(unicode,16)));check((unicodeQuery[12]&255)=="Café TV".getBytes(StandardCharsets.UTF_8).length,"Follow-up encoding preserves UTF-8 label bytes");
        a.close();check(a.accept(v6,parse(six),start+2).isEmpty()&&a.due(true,start+2,4).isEmpty()&&!a.pending(start+2),"Closed scan discards late replies and queued work");
        check(new MdnsAssembly().accept(PEER,parse(p[3]),start).isEmpty(),"A new scan cannot inherit prior service records");
        for(MdnsPacket.Question invalid:Arrays.asList(new MdnsPacket.Question(Arrays.asList("example","com"),1),new MdnsPacket.Question(INSTANCE,255),new MdnsPacket.Question(Arrays.asList("bad\nname","_http","_tcp","local"),16))){try{MdnsPacket.followUp(Arrays.asList(invalid));throw new AssertionError("Unsafe question");}catch(IllegalArgumentException expected){check(true,"Reject non-local, arbitrary-type and control-character queries");}}
        a=new MdnsAssembly();a.accept(PEER,parse(p[0]),-TimeUnit.SECONDS.toNanos(10));check(a.due(false,-TimeUnit.SECONDS.toNanos(10),4).size()==2,"Monotonic clocks need no positive epoch");
        byte[] full=packet(p);for(int n=0;n<full.length;n++)check(MdnsPacket.parse(full,n).records.isEmpty(),"Truncated packets do not leak partial wire records");
        a=new MdnsAssembly();for(int n=1;n<=129;n++)a.accept(ip("192.0.2."+n),parse(p[0]),start);check(a.limited,"Peer admission is bounded and surfaced");
        a=new MdnsAssembly();for(int n=0;n<150;n++){List<String> inst=Arrays.asList("Peer "+n,"_http","_tcp","local");a.accept(ip("198.51.100."+(n/30+1)),parse(rr(Arrays.asList("_http","_tcp","local"),12,name(inst),120,false)),start);a.due(false,start,4);}check(a.limited,"RRset/query budget pressure is surfaced");
        Random random=new Random(303);for(int n=0;n<10000;n++){byte[] data=new byte[random.nextInt(512)];random.nextBytes(data);MdnsPacket.Result result=MdnsPacket.parse(data,data.length);MdnsAssembly bounded=new MdnsAssembly();bounded.accept(PEER,result,start);for(MdnsPacket.Question question:bounded.due(false,start,4))MdnsPacket.followUp(Arrays.asList(question));}
        System.out.println("PASS · "+checks+" split mDNS, fresh association, TTL, query and bound checks; 10000 malformed packets");
    }
}
