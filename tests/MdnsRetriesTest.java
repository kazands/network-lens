import dev.netlens.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class MdnsRetriesTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        MdnsRetries r=new MdnsRetries();long start=-5_000_000_000L,gap=TimeUnit.MILLISECONDS.toNanos(1200);
        check(r.due(false,start,8).isEmpty(),"No unsolicited retries");
        check(!r.request(null)&&!r.request("_http._tcp.evil.")&&!r.request("_http._tcp.local.")&&!r.request("HTTP"),"Only canonical local service types accepted");
        check(r.request("_http._tcp.")&&r.request("_hap._tcp."),"Valid admitted types queued");
        for(int i=0;i<100;i++)check(r.request("_http._tcp."),"Duplicate instances coalesce");
        List<String> first=r.due(false,start,8);check(first.equals(Arrays.asList("_hap._tcp.","_http._tcp.")),"Fresh PTRs are deterministic and shared by instances");
        check(MdnsPacket.query(Arrays.asList(first.get(0)+"local",first.get(1)+"local")).length<512,"Retry uses bounded existing query encoder");
        check(r.due(false,start+gap-1,8).isEmpty(),"Retry spacing is enforced");
        check(r.due(true,start,8).equals(first),"IPv6 has its own finite sends");
        check(r.due(false,start+gap,1).size()==1&&r.due(false,start+gap,1).size()==1,"Packet capacity does not starve later types");
        check(r.due(false,start+gap*20,8).isEmpty(),"At most two follow-up questions per type per family");
        check(r.due(true,start+gap,8).equals(first)&&r.due(true,start+gap*2,8).isEmpty(),"IPv6 also stops after two questions");
        MdnsRetries bounded=new MdnsRetries();for(int i=0;i<48;i++)check(bounded.request("_fixture"+i+"._tcp."),"48-type admission bound");
        check(!bounded.request("_extra._tcp."),"New types cannot exceed the cap");
        check(bounded.due(false,0,0).isEmpty(),"Zero packet capacity consumes no attempts");int count=0;for(int i=0;i<6;i++)count+=bounded.due(false,0,8).size();check(count==48&&bounded.due(false,0,8).isEmpty(),"Each packet has at most eight questions");
        boolean invalid=false;try{bounded.due(false,0,9);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"Oversized packet request rejected");
        System.out.println("PASS · "+checks+" mDNS retry checks");
    }
}
