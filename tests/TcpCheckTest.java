import dev.netlens.TcpCheck;
import java.io.*;
import java.util.concurrent.atomic.*;

public final class TcpCheckTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static class Clock implements TcpCheck.Clock {long now;int sleeps;Runnable action=()->{};public long nanos(){return now;}public void sleep(int ms)throws InterruptedException{check(ms>0&&ms<=25,"Cancellation polling is bounded");now+=ms*1_000_000L;sleeps++;action.run();}}
 public static void main(String[] args){
  AtomicInteger calls=new AtomicInteger();Clock clock=new Clock();
  TcpCheck.Result r=TcpCheck.confirm(()->{calls.incrementAndGet();return 54;},()->false,clock);
  check(r.status==TcpCheck.Status.CONFIRMED&&r.connections==2&&r.latency==54&&calls.get()==2,"A stable peer requires two separately executed connections");check(clock.now>=200_000_000L,"Reconfirmation waits at least 200 ms");
  calls.set(0);r=TcpCheck.confirm(()->{if(calls.incrementAndGet()==2)throw new IOException("Peer left");return 54;},()->false,new Clock());
  check(r.status==TcpCheck.Status.UNCONFIRMED&&r.connections==1&&r.latency==-1&&r.failure instanceof IOException,"One accepted connection is insufficient when the peer disappears");
  calls.set(0);r=TcpCheck.confirm(()->{calls.incrementAndGet();throw new java.net.ConnectException("Connection refused");},()->false,new Clock());
  check(r.status==TcpCheck.Status.UNCONFIRMED&&r.connections==0&&calls.get()==1,"A refusal never counts as a successful presence check");
  calls.set(0);r=TcpCheck.confirm(()->{calls.incrementAndGet();return 1;},()->true,new Clock());check(r.status==TcpCheck.Status.CANCELLED&&calls.get()==0,"Stop before the first attempt opens no socket");
  AtomicBoolean stop=new AtomicBoolean();clock=new Clock();clock.action=()->stop.set(true);calls.set(0);r=TcpCheck.confirm(()->{calls.incrementAndGet();return 1;},stop::get,clock);check(r.status==TcpCheck.Status.CANCELLED&&calls.get()==1&&clock.now<=25_000_000L,"Stop during spacing prevents a second connection promptly");
  stop.set(false);calls.set(0);r=TcpCheck.confirm(()->{calls.incrementAndGet();stop.set(true);return 1;},stop::get,new Clock());check(r.status==TcpCheck.Status.CANCELLED&&calls.get()==1,"Stop during the first connection rejects the result");
  calls.set(0);r=TcpCheck.confirm(()->{if(calls.incrementAndGet()==2)throw new SecurityException("Denied");return 1;},()->false,new Clock());check(r.status==TcpCheck.Status.UNCONFIRMED&&r.connections==1&&r.failure instanceof SecurityException,"Permission failure remains available to source error reporting");
  r=TcpCheck.confirm(()->-1,()->false,new Clock());check(r.status==TcpCheck.Status.UNCONFIRMED&&r.connections==0,"Invalid transport timing cannot confirm a peer");
  TcpCheck.Clock interrupted=new TcpCheck.Clock(){public long nanos(){return 0;}public void sleep(int ms)throws InterruptedException{throw new InterruptedException();}};
  r=TcpCheck.confirm(()->1,()->false,interrupted);check(r.status==TcpCheck.Status.CANCELLED&&Thread.currentThread().isInterrupted(),"Interrupted wait cancels and preserves the interrupt flag");Thread.interrupted();
  System.out.println("PASS · "+checks+" TCP presence, spacing, cancellation and error assertions");
 }
}
