import dev.netlens.LocalServices;
import dev.netlens.MdnsPacket;
import java.util.Locale;

/** Native callback representation must match current wire data without widening wire syntax. */
public final class LocalServicesTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(String type:LocalServices.INITIAL){
            String bare=type.substring(0,type.length()-1);
            for(String form:new String[]{type,bare,"."+bare,"."+type,type+"local",type+"local.","."+type+"local.",bare.toUpperCase(Locale.ROOT)}){
                check(LocalServices.canonicalType(form).equals(type),"Native forms identify the same service: "+form);
                check(LocalServices.canonicalType(LocalServices.canonicalType(form)).equals(type),"Canonicalization is stable");
                check(LocalServices.tcpConfirmation(form)==(type.endsWith("._tcp.")&&!type.equals("_matter._tcp.")),"Representation cannot turn UDP or Matter into a TCP probe");
            }
        }
        for(String invalid:new String[]{null,"",".",".._http._tcp","_http.._tcp","_http._tcp..","Printer._http._tcp","_sub._http._tcp","_http._tcp.example.com","_http._tcp.local.evil","_http._sctp","http._tcp","_http._tcp\u0000","_ht\ntp._tcp"," _http._tcp","_http._tcp ","_h\u202ettp._tcp","_"+"a".repeat(63)+"._tcp","_"+"a".repeat(128)+"._tcp"}){
            check(LocalServices.canonicalType(invalid).isEmpty(),"Malformed/foreign types stay invalid");
            if(invalid!=null)check(!LocalServices.tcpConfirmation(invalid),"Invalid native type cannot trigger TCP");
        }
        check(LocalServices.canonicalType("._"+"a".repeat(62)+"._tcp").equals("_"+"a".repeat(62)+"._tcp."),"Maximum supported service label remains valid");
        check(MdnsPacket.serviceType("._http._tcp.local").isEmpty(),"Native normalization does not relax the wire parser");
        check(MdnsPacket.serviceType("_http._tcp.local").equals("_http._tcp."),"Wire format remains unchanged");
        System.out.println("PASS · "+checks+" native service-type normalization and transport checks");
    }
}
