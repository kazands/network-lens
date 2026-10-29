import dev.netlens.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public final class RoutedIpv6Test {
 static int checks;
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static InetAddress ip(String s)throws Exception{return InetAddress.getByName(s);}
 static Ipv6Routes.Rule rule(String prefix,int length,String gateway,String iface,boolean unicast){return new Ipv6Routes.Rule(prefix,length,gateway,iface,unicast);}
 static Ipv6Routes routes(Ipv6Routes.Rule... rules){return new Ipv6Routes(Arrays.asList(rules),"wlan0");}
 static byte[] name(String... labels)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(String l:labels){byte[] v=l.getBytes("UTF-8");b.write(v.length);b.write(v);}b.write(0);return b.toByteArray();}
 static byte[] rr(byte[] owner,int kind,byte[] payload,long ttl)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.write(owner);d.writeShort(kind);d.writeShort(kind==12?1:0x8001);d.writeInt((int)ttl);d.writeShort(payload.length);d.write(payload);return b.toByteArray();}
 static MdnsPacket.Result packet(byte[]... records)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeShort(0);d.writeShort(0x8400);d.writeShort(0);d.writeShort(records.length);d.writeInt(0);for(byte[] r:records)d.write(r);byte[] p=b.toByteArray();return MdnsPacket.parse(p,p.length);}
 static byte[][] graph(String type,String protocol)throws Exception{
  byte[] service=name(type,protocol,"local"),instance=name("Sleepy.sensor",type,protocol,"local"),host=name("sensor","local");ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(0);d.writeShort(12345);d.write(host);
  byte[] txt="md=Fixture sensor".getBytes("UTF-8");byte[] field=new byte[txt.length+1];field[0]=(byte)txt.length;System.arraycopy(txt,0,field,1,txt.length);
  return new byte[][]{rr(service,12,instance,120),rr(instance,33,b.toByteArray(),120),rr(instance,16,field,120),rr(host,28,ip("fd70::20").getAddress(),120)};
 }
 static final class Clock implements IcmpEcho.Clock {long t;public long nanos(){return t;}public void sleep(int ms){t+=ms*1000000L;}}
 static final class Channel implements IcmpEcho.Channel {Clock clock;List<byte[]> sent=new ArrayList<>();boolean closed;int respondAfter;Channel(Clock c,int after){clock=c;respondAfter=after;}public int identifier(){return 32123;}public void send(byte[] p){sent.add(p.clone());}public byte[] receive(int ms){clock.t+=ms*1000000L;if(respondAfter<0||clock.t<respondAfter*1000000L)return null;byte[] p=sent.get(0).clone();p[0]=(byte)129;return p;}public void close(){closed=true;}}
 public static void main(String[] args)throws Exception{
  Ipv6Routes.Rule omr=rule("fd70::",64,"fe80::1","wlan0",true);Ipv6Routes valid=routes(omr);
  check(valid.gateway(ip("fd70::20")).equals("fe80::1"),"Explicit OMR /64 admits a routed ULA endpoint");
  for(String target:new String[]{"fd71::20","2001:4860:4860::8888","fe80::20","ff02::1","::1","::","192.0.2.1","64:ff9b::c000:201"})check(valid.gateway(ip(target))==null,"No off-route/special target "+target);
  for(Ipv6Routes.Rule bad:new Ipv6Routes.Rule[]{rule("::",0,"fe80::1","wlan0",true),rule("fd00::",8,"fe80::1","wlan0",true),rule("fd70::",64,"fe80::1","rmnet0",true),rule("fd70::",64,"::","wlan0",true),rule("fd70::",64,"fd00::1","wlan0",true),rule("fd70::",64,"fe80::1","wlan0",false)})check(routes(bad).gateway(ip("fd70::20"))==null,"Default, broad, wrong-interface, missing-hop and reject routes excluded");
  Ipv6Routes.Rule reject=rule("fd70::20",128,"::","wlan0",false);check(routes(omr,reject).gateway(ip("fd70::20"))==null,"More-specific reject wins over admitted /64");check(routes(omr,reject).gateway(ip("fd70::21"))!=null,"Reject does not hide unrelated addresses");
  check(valid.retains(routes(omr,rule("fd71::",64,"fe80::2","wlan0",true))),"An unrelated routed prefix can be added");check(!valid.retains(routes()),"Route withdrawal fences old work");check(!valid.retains(routes(omr,reject)),"Adding an overlapping reject fences old work");check(!valid.retains(routes(rule("fd70::",64,"fe80::2","wlan0",true))),"Next-hop replacement fences old work");check(routes(rule("2001:db8:70::",64,"fe80::1","wlan0",true)).gateway(ip("2001:db8:70::20"))!=null,"A specific GUA /64 is supported");
  Ipv6Routes.Rule connected=rule("fe80::",64,"::","wlan0",true),broad=rule("fd00::",8,"fe80::1","wlan0",true);
  check(routes(connected).same(routes()),"Irrelevant connected IPv6 route metadata is not routed discovery scope");check(valid.same(routes(omr,connected,broad))&&valid.retains(routes(omr,broad)),"Less-specific and unrelated route metadata cannot erase accepted scope");check(!valid.same(routes(omr,reject)),"A more-specific reject changes effective discovery scope");
  long now=TimeUnit.SECONDS.toNanos(20);byte[][] g=graph("_hap","_udp");InetAddress proxy=ip("192.0.2.1");
  for(int a=0;a<4;a++)for(int b=0;b<4;b++)if(a!=b)for(int c=0;c<4;c++)if(c!=a&&c!=b)for(int e=0;e<4;e++)if(e!=a&&e!=b&&e!=c){MdnsAssembly assembly=new MdnsAssembly();int[] order={a,b,c,e};for(int n=0;n<4;n++){check(assembly.accept(proxy,packet(g[order[n]]),now+n).isEmpty(),"Proxy never becomes direct endpoint identity");check(assembly.routedCandidates(now+n).size()==(n==3?1:0),"Complete proxied graph only, all packet orders");}}
  MdnsAssembly assembly=new MdnsAssembly();assembly.accept(proxy,packet(g),now);check(assembly.routedCandidates(now).get(0).getService().name.equals("Sleepy.sensor"),"Dotted instance label preserved");
  check(assembly.routedCandidates(now+TimeUnit.SECONDS.toNanos(10)).isEmpty(),"Proxy details expire within ten seconds");
  for(int part=0;part<4;part++){assembly=new MdnsAssembly();assembly.accept(proxy,packet(g),now);byte[] gone=g[part].clone();int ownerEnd=0;while(gone[ownerEnd]!=0)ownerEnd+=(gone[ownerEnd]&255)+1;ownerEnd++;for(int k=ownerEnd+4;k<ownerEnd+8;k++)gone[k]=0;assembly.accept(proxy,packet(gone),now+1);check(assembly.routedCandidates(now+2).isEmpty(),"Goodbye of each graph edge prevents new metadata association");}
  assembly=new MdnsAssembly();assembly.accept(proxy,packet(g[0],g[1],g[3]),now);assembly.accept(ip("192.0.2.2"),packet(g[2]),now);check(assembly.routedCandidates(now).isEmpty(),"Proxy senders cannot pool graph records");
  assembly=new MdnsAssembly();assembly.accept(proxy,packet(graph("_http","_tcp")),now);check(assembly.routedCandidates(now).isEmpty(),"Unrelated services cannot create routed targets");
  assembly=new MdnsAssembly();assembly.accept(proxy,packet(g),now);assembly.close();check(assembly.routedCandidates(now).isEmpty(),"Stop closes candidate cache");
  for(int delay:new int[]{3,1800,2600,3990}){Clock clock=new Clock();Channel channel=new Channel(clock,delay);IcmpEcho.Result result=IcmpEcho.probeV6(()->channel,()->false,2000,2,200,clock);check(result.status==IcmpEcho.Status.REPLIED&&channel.closed,"Delayed IPv6 echo confirms, then closes: "+delay);check(result.requests==(delay<=2000?1:2),"Retry follows real timeout");}
  Clock clock=new Clock();Channel channel=new Channel(clock,-1);IcmpEcho.Result result=IcmpEcho.probeV6(()->channel,()->false,2000,2,200,clock);check(result.status==IcmpEcho.Status.NO_REPLY&&result.requests==2&&clock.t==4200000000L&&channel.closed,"Unreachable sleepy accessory stays unconfirmed after bounded 4.2 seconds");
  Clock cancelClock=new Clock();Channel cancelled=new Channel(cancelClock,-1);result=IcmpEcho.probeV6(()->cancelled,()->cancelClock.t>=150000000L,2000,2,200,cancelClock);check(result.status==IcmpEcho.Status.CANCELLED&&cancelClock.t==150000000L&&cancelled.closed,"Cancellation is polled within 50 ms and closes the socket");
  byte[] nonce=new byte[16];Arrays.fill(nonce,(byte)11);byte[] request=IcmpEcho.requestV6(32123,1,nonce);check((request[0]&255)==128&&request[2]==0&&request[3]==0,"Kernel-checksummed IPv6 echo request");byte[] reply=request.clone();reply[0]=(byte)129;check(IcmpEcho.replySequenceV6(reply,32123,1,nonce)==1,"Exact peer response matches nonce/id/sequence");for(int index:new int[]{0,1,4,5,6,7,8,23}){byte[] bad=reply.clone();bad[index]^=1;check(IcmpEcho.replySequenceV6(bad,32123,1,nonce)==0,"Reject wrong IPv6 echo field "+index);}for(int n=0;n<24;n++)check(IcmpEcho.replySequenceV6(Arrays.copyOf(reply,n),32123,1,nonce)==0,"Reject truncated IPv6 datagram "+n);
  System.out.println("PASS · "+checks+" routed IPv6 checks");
 }
}
