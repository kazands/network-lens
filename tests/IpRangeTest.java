import dev.netlens.IpRange;

public final class IpRangeTest {
    static int checks;
    static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    static void rejects(Runnable r){checks++;try{r.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        IpRange r=new IpRange("192.168.1.50",24);eq(r.size(),254L);eq(IpRange.address(r.first),"192.168.1.1");eq(IpRange.address(r.last),"192.168.1.254");eq(r.contains("192.168.2.1"),false);eq(r.hosts(1024).size(),254);
        eq(new IpRange("10.2.3.4",16).size(),65534L);rejects(()->new IpRange("10.2.3.4",16).hosts(1024));
        eq(new IpRange("192.168.1.11",31).hosts(2).toString(),"[192.168.1.10, 192.168.1.11]");eq(new IpRange("192.168.1.11",32).hosts(1).toString(),"[192.168.1.11]");
        eq(new IpRange("255.255.255.255",0).size(),4294967294L);eq(IpRange.number("255.255.255.255"),4294967295L);eq(IpRange.address(4294967295L),"255.255.255.255");
        for(String s:new String[]{"256.0.0.1","1.2.3","1..2.3","1.2.3.4.5","-1.2.3.4","1.2.3.x"})rejects(()->IpRange.number(s));
        rejects(()->new IpRange("1.2.3.4",33));rejects(()->new IpRange("1.2.3.4",-1));
        eq(IpRange.validHost("router.local"),"router.local");eq(IpRange.validHost("::1"),"::1");
        for(String s:new String[]{"-c 50","1.2.3.4;id","$(id)","https://example.com","", "-host"})rejects(()->IpRange.validHost(s));
        for(int prefix=0;prefix<=32;prefix++){IpRange v=new IpRange("10.0.0.1",prefix);eq(v.first<=IpRange.number("10.0.0.1")&&v.last>=IpRange.number("10.0.0.1"),true);}
        System.out.println("PASS · "+checks+" CIDR, boundary, range-limit, and input checks");
    }
}
