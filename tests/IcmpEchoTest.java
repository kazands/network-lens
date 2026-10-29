import dev.netlens.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class IcmpEchoTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static final class Clock implements IcmpEcho.Clock {
        long time;boolean interrupt;public long nanos(){return time;}public void sleep(int ms)throws InterruptedException{if(interrupt)throw new InterruptedException();time+=ms*1000000L;}
        void advance(int ms){time+=ms*1000000L;}
    }
    interface Read {byte[] read(Fake channel,int wait)throws IOException;}
    static final class Fake implements IcmpEcho.Channel {
        final Clock clock;final List<byte[]> sent=new ArrayList<>();final List<Long> times=new ArrayList<>();boolean closed,closeFails,sendFails;int reads;Read reader;
        Fake(Clock c){clock=c;reader=(f,w)->{clock.advance(w);return null;};}
        public int identifier(){return 12345;}
        public void send(byte[] bytes)throws IOException{if(sendFails)throw new IOException("send fixture");sent.add(bytes.clone());times.add(clock.time);}
        public byte[] receive(int wait)throws IOException{reads++;return reader.read(this,wait);}
        public void close()throws IOException{closed=true;if(closeFails)throw new IOException("close fixture");}
    }
    static byte[] reply(byte[] request){byte[] p=request.clone();p[0]=0;fix(p);return p;}
    static void fix(byte[] p){p[2]=p[3]=0;int sum=IcmpEcho.checksum(p);p[2]=(byte)(sum>>>8);p[3]=(byte)sum;}
    static IcmpEcho.Result run(Fake f){return IcmpEcho.probe(()->f,()->false,200,2,120,f.clock);}
    public static void main(String[] args)throws Exception{
        Clock clock=new Clock();Fake f=new Fake(clock);f.reader=(ch,w)->{ch.clock.advance(3);return reply(ch.sent.get(0));};IcmpEcho.Result r=run(f);
        check(r.status==IcmpEcho.Status.REPLIED&&r.requests==1&&r.latencyMillis==3,"First echo reply ends the probe");check(f.closed&&f.sent.size()==1,"Successful socket closed without redundant retry");
        clock=new Clock();f=new Fake(clock);r=run(f);check(r.status==IcmpEcho.Status.NO_REPLY&&r.problem==null&&r.requests==2,"Silence is not an execution error");check(f.times.equals(Arrays.asList(0L,320000000L))&&clock.time==520000000L,"Two attempts have bounded waits and a real retry gap");check(f.closed,"Silent socket closed");
        clock=new Clock();f=new Fake(clock);f.reader=(ch,w)->{if(ch.sent.size()==1){ch.clock.advance(w);return null;}ch.clock.advance(4);return reply(ch.sent.get(1));};r=run(f);check(r.status==IcmpEcho.Status.REPLIED&&r.requests==2&&r.latencyMillis==4,"A dropped first request is recovered on retry");
        clock=new Clock();f=new Fake(clock);f.reader=(ch,w)->{if(ch.sent.size()==1){ch.clock.advance(w);return null;}ch.clock.advance(5);return reply(ch.sent.get(0));};r=run(f);check(r.status==IcmpEcho.Status.REPLIED&&r.requests==2&&r.latencyMillis==325,"Late matching first reply remains valid after retry");
        byte[] nonce=new byte[16];Arrays.fill(nonce,(byte)37);byte[] request=IcmpEcho.request(12345,1,nonce),good=reply(request);
        check(request.length==24&&request[0]==8&&request[1]==0&&IcmpEcho.checksum(request)==0,"Request carries a valid IPv4 ICMP checksum");
        check(IcmpEcho.replySequence(good,12345,1,nonce)==1,"Reply identifier, sequence, nonce and checksum match");
        for(int index:new int[]{0,1,2,4,5,6,7,8,23}){byte[] bad=good.clone();bad[index]^=1;if(index!=2)fix(bad);check(IcmpEcho.replySequence(bad,12345,1,nonce)==0,"Reject altered echo field "+index);}
        check(IcmpEcho.replySequence(reply(IcmpEcho.request(12345,2,nonce)),12345,1,nonce)==0,"Future unsent sequence is rejected");
        for(int length=0;length<24;length++)check(IcmpEcho.replySequence(Arrays.copyOf(good,length),12345,1,nonce)==0,"Truncated reply rejected "+length);
        check(IcmpEcho.replySequence(Arrays.copyOf(good,25),12345,1,nonce)==0,"Oversized reply rejected");check(IcmpEcho.replySequence(null,12345,1,nonce)==0,"Null reply rejected");
        Random random=new Random(391);for(int n=0;n<10000;n++){byte[] bad=new byte[random.nextInt(64)];random.nextBytes(bad);if(IcmpEcho.replySequence(bad,12345,2,nonce)!=0)throw new AssertionError("Uncorrelated fuzz reply accepted");}check(true,"10000 malformed or uncorrelated replies do not match");
        clock=new Clock();f=new Fake(clock);f.reader=(ch,w)->new byte[24];r=run(f);check(r.status==IcmpEcho.Status.ERROR&&r.problem==IcmpEcho.Problem.REPLY_LIMIT&&f.reads==64&&f.closed,"Reply flood is bounded and reported");
        AtomicInteger opens=new AtomicInteger();r=IcmpEcho.probe(()->{opens.incrementAndGet();throw new IOException();},()->true,200,2,120);check(r.status==IcmpEcho.Status.CANCELLED&&opens.get()==0,"Already cancelled probe opens no socket");
        AtomicBoolean cancel=new AtomicBoolean();clock=new Clock();f=new Fake(clock);f.reader=(ch,w)->{cancel.set(true);return reply(ch.sent.get(0));};Fake receiving=f;r=IcmpEcho.probe(()->receiving,cancel::get,200,2,120,clock);check(r.status==IcmpEcho.Status.CANCELLED&&f.closed,"Cancellation during receive rejects even a matching reply");
        clock=new Clock();f=new Fake(clock);Clock timing=clock;Fake delayed=f;r=IcmpEcho.probe(()->delayed,()->timing.time>=220000000L,200,2,120,clock);check(r.status==IcmpEcho.Status.CANCELLED&&f.sent.size()==1&&f.closed,"Cancellation interrupts retry gap before sending again");
        for(IcmpEcho.Problem kind:new IcmpEcho.Problem[]{IcmpEcho.Problem.PERMISSION,IcmpEcho.Problem.UNSUPPORTED,IcmpEcho.Problem.SETUP,IcmpEcho.Problem.ROUTE,IcmpEcho.Problem.IO}){r=IcmpEcho.probe(()->{throw new IcmpEcho.Failure(kind,"private fixture");},()->false,200,2,120);check(r.status==IcmpEcho.Status.ERROR&&r.problem==kind&&r.requests==0&&!r.reason().isEmpty(),"Execution failure retained: "+kind);check(!r.reason().contains("private"),"Raw diagnostic text is not exposed: "+kind);}
        r=IcmpEcho.probe(()->{throw new IcmpEcho.Failure(IcmpEcho.Problem.PEER_UNREACHABLE,"peer negative");},()->false,200,2,120);check(r.status==IcmpEcho.Status.NO_REPLY&&r.problem==null,"Ordinary peer-unreachable result is not an execution failure");
        clock=new Clock();f=new Fake(clock);f.sendFails=true;r=run(f);check(r.status==IcmpEcho.Status.ERROR&&r.problem==IcmpEcho.Problem.IO&&f.closed&&r.requests==0,"Send failure reported and socket closed");
        clock=new Clock();f=new Fake(clock);f.reader=(ch,w)->{throw new IOException("receive fixture");};r=run(f);check(r.status==IcmpEcho.Status.ERROR&&r.requests==1&&f.closed,"Receive failure reported and socket closed");
        clock=new Clock();f=new Fake(clock);f.closeFails=true;f.reader=(ch,w)->reply(ch.sent.get(0));r=run(f);check(r.status==IcmpEcho.Status.REPLIED&&r.problem==IcmpEcho.Problem.IO&&f.closed,"Cleanup error is reported without discarding a proven reply");
        clock=new Clock();f=new Fake(clock);f.closeFails=true;r=run(f);check(r.status==IcmpEcho.Status.ERROR&&r.problem==IcmpEcho.Problem.IO,"Cleanup failure is not silently successful");
        clock=new Clock();f=new Fake(clock);clock.interrupt=true;r=run(f);check(r.status==IcmpEcho.Status.CANCELLED&&f.closed&&Thread.interrupted(),"Interrupted retry closes socket and preserves interrupt");
        clock=new Clock();f=new Fake(clock);f.reader=(ch,w)->{throw new IllegalStateException("unexpected fixture");};boolean threw=false;try{run(f);}catch(IllegalStateException expected){threw=true;}check(threw&&f.closed,"Unexpected failure still closes socket");
        for(int[] b:new int[][]{{0,2,120},{1001,2,120},{200,0,120},{200,3,120},{200,2,-1},{200,2,501}}){boolean invalid=false;try{IcmpEcho.probe(()->{throw new AssertionError("opened");},()->false,b[0],b[1],b[2]);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Unsafe echo budget rejected");}
        DiscoveryHealth health=new DiscoveryHealth();health.begin("ICMP echo");health.fail("ICMP echo","ICMP socket unavailable");health.ok("ICMP echo");check(health.sources().get("ICMP echo").startsWith("Failed")&&!health.summary().isEmpty(),"TCP success cannot hide ICMP execution failure");
        health=new DiscoveryHealth();health.begin("ICMP echo");health.count("ICMP echo","no echo reply");health.ok("ICMP echo");check(health.summary().isEmpty()&&health.sources().get("ICMP echo").startsWith("OK"),"Completed unanswered checks can finish without false errors");
        System.out.println("PASS · "+checks+" ICMP policy, retry, correlation, cancellation and error checks; 10000 malformed inputs");
    }
}
