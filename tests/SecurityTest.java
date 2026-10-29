import dev.netlens.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic deletion ordering and adversarial resource/input regressions. */
public final class SecurityTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        DataGuard data=new DataGuard();List<String> records=new ArrayList<>();long old=data.token();
        check(data.writeIfCurrent(old,()->records.add("scan")),"Fresh scan must persist");
        data.invalidateAndClear(records::clear);
        check(!data.writeIfCurrent(old,()->records.add("late scan")),"Late scan must not restore deleted inventory");
        check(!data.writeIfCurrent(old,()->records.add("late diagnostic")),"Late diagnostic must not restore history");
        check(records.isEmpty(),"Deletion must stay empty");
        check(data.writeIfCurrent(data.token(),()->records.add("new scan")),"New user work may persist");
        // Hold an old writer inside the shared lock while deletion is requested.
        CountDownLatch writing=new CountDownLatch(1),release=new CountDownLatch(1),deleting=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);long token=data.token();
        try{
            Future<?> writer=pool.submit(()->data.writeIfCurrent(token,()->{writing.countDown();await(release);records.add("in flight");}));
            check(writing.await(3,TimeUnit.SECONDS),"Writer entered critical section");
            Future<?> clear=pool.submit(()->{deleting.countDown();data.invalidateAndClear(records::clear);});
            check(deleting.await(3,TimeUnit.SECONDS),"Deletion requested during write");
            release.countDown();writer.get(3,TimeUnit.SECONDS);clear.get(3,TimeUnit.SECONDS);
            check(records.isEmpty(),"A write already in progress must be cleared before delete returns");
            check(!data.writeIfCurrent(token,()->records.add("queued old work")),"Queued old work must stay rejected");
        }finally{release.countDown();pool.shutdownNow();}
        ServiceAdmission services=new ServiceAdmission(100);
        ExecutorService advertisers=Executors.newFixedThreadPool(8);List<Future<?>> jobs=new ArrayList<>();
        try{
            for(int t=0;t<8;t++){final int n=t;jobs.add(advertisers.submit(()->{for(int i=0;i<2000;i++)services.reserve("device-"+n+"-"+i,"_http._tcp.");}));}
            for(Future<?> job:jobs)job.get(5,TimeUnit.SECONDS);
        }finally{advertisers.shutdownNow();}
        check(services.size()==100,"Concurrent advertisements must not retain more than 100 keys");
        check(!services.reserve("overflow","_http._tcp."),"Full admission must reject without retaining");
        ServiceAdmission small=new ServiceAdmission(3);
        check(small.reserve("printer","_ipp._tcp."),"Valid service admitted");
        check(!small.reserve("printer","_ipp._tcp.")&&small.size()==1,"Duplicates do not consume capacity");
        check(!small.reserve(null,"_http._tcp."),"Null service rejected");
        check(!small.reserve(repeat("x",129),"_http._tcp.")&&small.size()==1,"Oversized name rejected without retention");
        check(!small.reserve("name",repeat("x",129)),"Oversized type rejected");
        check(SafeText.label("Printer\n\t\u202eevil\u2066\u0000",128).equals("Printerevil"),"Strip control and bidi label spoofing characters");
        String unicode="\ud83d\udda8";
        check(SafeText.label(repeat(unicode,1000),128).codePointCount(0,256)==128,"Bound Unicode without splitting surrogate pairs");
        check(SafeText.label(repeat("\n",10000)+"hidden",128).isEmpty(),"Input processing must also be bounded");
        check(SafeText.label(null,128).isEmpty(),"Null label safe");
        check(SafeText.label(" <printer>& ",128).equals("<printer>&"),"Ordinary text preserved, not interpreted as markup");
        System.out.println("PASS · "+checks+" security regression checks");
    }
    private static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
    private static void await(CountDownLatch latch){try{if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("Timed out releasing writer");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
