import dev.netlens.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Regression fixtures for confirmed URL, completion, DNS and grouping defects. */
public final class ConfirmedFixesTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
    private static void wire(URI target,String expected)throws Exception{
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            final Throwable[] failure={null};Thread responder=new Thread(()->{try(Socket socket=server.accept()){
                socket.setSoTimeout(2000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String request=reader.readLine();if(!expected.equals(request))throw new AssertionError(request);while(!reader.readLine().isEmpty()){}
                String body="<root><device><friendlyName>Fixture PC</friendlyName><modelName>Fixture model</modelName></device></root>";socket.getOutputStream().write(ascii("HTTP/1.1 200 OK\r\nContent-Length: "+body.length()+"\r\n\r\n"+body));
            }catch(Throwable e){failure[0]=e;}},"local-test-server");responder.start();
            try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())){socket.setSoTimeout(2000);socket.getOutputStream().write(UpnpDescription.request(target));Map<String,String> fields=UpnpDescription.parse(UpnpDescription.readResponse(socket.getInputStream()));check("Fixture PC".equals(fields.get("friendlyName"))&&"Fixture model".equals(fields.get("modelName")),"Wire response retains friendly name and model");}
            responder.join(3000);check(!responder.isAlive()&&failure[0]==null,"Exact HTTP request target was received: "+expected);
        }
    }
    private static final class Endpoint implements Grouping.Endpoint {
        final String mac;final boolean self;final List<String> links;
        Endpoint(String mac,boolean self,String... links){this.mac=mac;this.self=self;this.links=Arrays.asList(links);}
        public String hardwareAddress(){return mac;}public boolean isSelf(){return self;}public Collection<String> links(){return links;}
    }
    public static void main(String[] args)throws Exception{
        InetAddress peer=InetAddress.getByName("192.0.2.5");
        URI root=UpnpDescription.target("http://192.0.2.5:8060/",peer);check(root!=null,"Allow advertised root description");wire(root,"GET / HTTP/1.1");
        String path="/upnphost/udhisapi.dll?content=uuid:12345678-1234-1234-1234-123456789abc";
        URI windows=UpnpDescription.target("http://192.0.2.5:2869"+path,peer);check(windows!=null,"Allow documented Windows description selector");wire(windows,"GET "+path+" HTTP/1.1");
        for(String suffix:new String[]{"/?reboot=1","/device.xml?content=uuid:12345678-1234-1234-1234-123456789abc","/upnphost/udhisapi.dll?reboot=1","/upnphost/udhisapi.dll?content=uuid:bad","/upnphost/udhisapi.dll?content=uuid:12345678-1234-1234-1234-123456789abc&reboot=1","/upnphost/udhisapi.dll?content=uuid%3A12345678-1234-1234-1234-123456789abc","/reboot"})check(UpnpDescription.target("http://192.0.2.5"+suffix,peer)==null,"Reject unrelated or ambiguous query/action path");
        check(UpnpDescription.target("http://192.0.2.6"+path,peer)==null,"Windows descriptor cannot redirect to another host");
        DiscoveryHealth health=new DiscoveryHealth();health.begin("Fixture source");health.finish(false);check(health.sources().get("Fixture source").startsWith("Truncated"),"Unconfirmed running source cannot become OK by cleanup");
        health=new DiscoveryHealth();health.starting("_ipp._tcp.");health.started("_ipp._tcp.");health.closeBrowseWindow();health.finish(false);check(health.summary().isEmpty(),"Explicitly closed empty browse is successful");
        health=new DiscoveryHealth();health.workStarted("Reverse DNS");health.workStarted("Reverse DNS");health.workFinished("Reverse DNS");health.finish(false);check(health.sources().get("Reverse DNS").startsWith("Truncated"),"One completed lookup cannot hide another pending lookup");
        health=new DiscoveryHealth();health.workStarted("Reverse DNS");health.fail("Reverse DNS","No valid reply");health.workFinished("Reverse DNS");health.finish(false);check(health.sources().get("Reverse DNS").startsWith("Failed"),"Completion cannot erase DNS failure");
        byte[] negative=ReverseDns.query("192.0.2.5",42);negative[2]=(byte)0x81;negative[3]=(byte)0x83;
        check(ReverseDns.response(negative,negative.length,42,"192.0.2.5").status==ReverseDns.Status.NEGATIVE,"Correlated NXDOMAIN is a valid no-name response");negative[3]=(byte)0x80;
        check(ReverseDns.response(negative,negative.length,42,"192.0.2.5").status==ReverseDns.Status.NEGATIVE,"Correlated NOERROR with no PTR is a valid no-name response");negative[3]=(byte)0x82;
        check(ReverseDns.response(negative,negative.length,42,"192.0.2.5").status==ReverseDns.Status.ERROR,"SERVFAIL is a server error, not a successful no-name response");
        check(ReverseDns.response(negative,negative.length,43,"192.0.2.5").status==ReverseDns.Status.INVALID,"Wrong transaction is never counted as a DNS answer");
        check(ReverseDns.response(negative,negative.length-1,42,"192.0.2.5").status==ReverseDns.Status.INVALID,"Malformed negative response is rejected");
        // Compare indexed grouping with the established pairwise-clique policy over adversarial fixtures.
        Random random=new Random(93);
        for(int run=0;run<100;run++){List<Endpoint> endpoints=new ArrayList<>();for(int n=0;n<60;n++){List<String> links=new ArrayList<>();for(int k=0;k<4;k++)if(random.nextBoolean())links.add("link:"+random.nextInt(15));endpoints.add(new Endpoint(random.nextBoolean()?"":"mac:"+random.nextInt(5),random.nextInt(20)==0,links.toArray(new String[0])));}List<List<Endpoint>> reference=new ArrayList<>();for(Endpoint e:endpoints){List<Endpoint> match=null;for(List<Endpoint> group:reference){boolean good=true;for(Endpoint member:group)if(e.self||member.self?e.self!=member.self:!e.mac.isEmpty()&&!member.mac.isEmpty()&&!e.mac.equals(member.mac)||Collections.disjoint(e.links,member.links)){good=false;break;}if(good){match=group;break;}}if(match==null){match=new ArrayList<>();reference.add(match);}match.add(e);}check(Grouping.groups(endpoints).equals(reference),"Indexed grouping preserves pairwise compatibility, conflicts and input ordering");}
        System.out.println("PASS · "+checks+" confirmed-fix regressions (local wire fixtures, source completion, DNS and grouping equivalence)");
    }
}
