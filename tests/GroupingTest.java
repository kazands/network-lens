import dev.netlens.Grouping;
import java.util.*;

public final class GroupingTest {
    private static int checks;
    private static final class Endpoint implements Grouping.Endpoint {
        boolean self;String mac="";List<String> keys=new ArrayList<>();
        Endpoint(boolean self,String mac,String...keys){this.self=self;this.mac=mac;this.keys.addAll(Arrays.asList(keys));}
        public boolean isSelf(){return self;}
        public String hardwareAddress(){return mac;}
        public Collection<String> links(){return keys;}
    }
    private static void expect(int count,Endpoint...endpoints){checks++;if(Grouping.groups(Arrays.asList(endpoints)).size()!=count)throw new AssertionError("Grouping case "+checks);}
    public static void main(String[] args){
        expect(0);
        expect(1,new Endpoint(true,""),new Endpoint(true,""),new Endpoint(true,""));
        expect(2,new Endpoint(true,"","host:x"),new Endpoint(false,"","host:x"));
        expect(2,new Endpoint(false,""),new Endpoint(false,"")); // Same names alone have no key.
        expect(1,new Endpoint(false,"","host:x"),new Endpoint(false,"","host:x"));
        expect(1,new Endpoint(false,"","id:x"),new Endpoint(false,"","id:x"));
        expect(2,new Endpoint(false,"","id:x"),new Endpoint(false,"","id:y"));
        expect(2,new Endpoint(false,"00:11:22:33:44:55","id:x"),new Endpoint(false,"00:11:22:33:44:66","id:x"));
        expect(1,new Endpoint(false,"00:11:22:33:44:55","id:x"),new Endpoint(false,"","id:x"));
        expect(2,new Endpoint(false,"","id:a"),new Endpoint(false,"","id:a","id:b"),new Endpoint(false,"","id:b")); // No weak transitive bridge.
        expect(2,new Endpoint(false,"",""),new Endpoint(false,"",""));
        List<Endpoint> maximum=new ArrayList<>();for(int i=0;i<1200;i++)maximum.add(new Endpoint(false,"","id:"+i));
        checks++;if(Grouping.groups(maximum).size()!=1200)throw new AssertionError("Maximum inventory retains unrelated endpoints");
        System.out.println("PASS · "+checks+" grouping assertions");
    }
}
