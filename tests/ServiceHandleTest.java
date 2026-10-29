import dev.netlens.ServiceHandle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ServiceHandleTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        ServiceHandle<Object> handle=new ServiceHandle<>();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();Object service=new Object();
        Callable<Object> stuck=()->{calls.incrementAndGet();entered.countDown();release.await();return service;};
        long start=System.nanoTime();
        try{handle.acquire(stuck,()->false,50);throw new AssertionError("Blocked constructor returned");}catch(TimeoutException expected){checks++;}
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500,"A blocked service constructor cannot block its caller indefinitely");
        check(entered.await(1,TimeUnit.SECONDS),"Constructor runs in its isolated worker");
        try{handle.acquire(stuck,()->false,50);throw new AssertionError("Second caller waited without a deadline");}catch(TimeoutException expected){checks++;}
        check(calls.get()==1,"Repeated scans share one pending constructor instead of leaking workers");
        release.countDown();check(handle.acquire(stuck,()->false,1000)==service,"An eventually ready service is available to a later scan");
        check(handle.acquire(()->new Object(),()->false,100)==service&&calls.get()==1,"A ready OS handle is reused without another initialization");
        AtomicBoolean cancel=new AtomicBoolean(true);AtomicInteger starts=new AtomicInteger();ServiceHandle<Object> cancelled=new ServiceHandle<>();
        try{cancelled.acquire(()->{starts.incrementAndGet();return service;},cancel::get,100);throw new AssertionError("Cancelled caller started acquisition");}catch(InterruptedException expected){checks++;}
        check(starts.get()==0,"A cancelled scan creates no new service worker");
        ServiceHandle<Object> pending=new ServiceHandle<>();CountDownLatch waiting=new CountDownLatch(1),finish=new CountDownLatch(1),stopped=new CountDownLatch(1);cancel.set(false);AtomicReference<Throwable> error=new AtomicReference<>();
        Thread caller=new Thread(()->{try{pending.acquire(()->{waiting.countDown();finish.await();return service;},cancel::get,4000);error.set(new AssertionError("Cancelled waiter returned a service"));}catch(InterruptedException expected){}catch(Throwable problem){error.set(problem);}finally{stopped.countDown();}});
        caller.start();check(waiting.await(1,TimeUnit.SECONDS),"Cancellation fixture entered the platform constructor");cancel.set(true);
        check(stopped.await(1,TimeUnit.SECONDS)&&error.get()==null,"Cancellation releases the caller while the platform remains blocked");finish.countDown();caller.join(1000);
        ServiceHandle<String> failed=new ServiceHandle<>();
        try{failed.acquire(()->{throw new IllegalStateException("fixture");},()->false,1000);throw new AssertionError("Service error hidden");}catch(ExecutionException expected){check(expected.getCause() instanceof IllegalStateException,"Initialization failure preserves its cause");}
        check("ready".equals(failed.acquire(()->"ready",()->false,1000)),"A failed constructor can be retried by a later scan");
        ServiceHandle<String> absent=new ServiceHandle<>();check(absent.acquire(()->null,()->false,1000)==null,"A missing OS service is returned without inventing a handle");
        check("ready".equals(absent.acquire(()->"ready",()->false,1000)),"A later available service can replace a missing result");
        try{absent.acquire(()->"unused",()->false,0);throw new AssertionError("Invalid budget accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS · "+checks+" OS-service initialization deadline, sharing, cancellation and recovery checks");
    }
}
