package io.github.kazands.networklens.test

import android.app.Instrumentation
import android.system.*
import io.github.kazands.networklens.*
import java.io.FileDescriptor
import java.net.*
import java.security.SecureRandom

/** Feasibility experiment only: no production scan, inventory or identity inference. */
internal object Ipv6PublicTrialChecks {
    private fun matches(bytes:ByteArray,n:Int,nonce:ByteArray,address:InetAddress?,owns:(InetAddress)->Boolean):Boolean =
        n==24 && bytes[0]==129.toByte() && bytes[1]==0.toByte() && bytes[6]==0.toByte() && bytes[7]==1.toByte() &&
        bytes.copyOfRange(8,24).contentEquals(nonce) && address is Inet6Address && address.isLinkLocalAddress && !owns(address)
    fun run(test:Instrumentation,check:(Boolean,String)->Unit) {
        fun status(value:String) {test.sendStatus(0,android.os.Bundle().apply {putString("stream",value+"\n")})}
        val process=Runtime.getRuntime().exec(arrayOf("/system/bin/getprop","ro.kernel.qemu"))
        check(process.inputStream.bufferedReader().use {it.readText().trim()}=="1","Multicast experiment rejects physical devices before opening sockets")
        val fixture=ByteArray(24).apply {this[0]=129.toByte();this[7]=1}
        val own=InetAddress.getByName("fe80::1");val peer=InetAddress.getByName("fe80::2")
        check(matches(fixture,24,ByteArray(16),peer){it==own},"Multicast trial accepts a correlated other peer on the same link")
        check(!matches(fixture,24,ByteArray(16),own){it==own},"Multicast trial excludes own addresses rather than the whole local prefix")
        check(!matches(fixture,23,ByteArray(16),peer){false} && !matches(fixture,24,ByteArray(16){1},peer){false},"Truncated or uncorrelated multicast replies are excluded")
        val net=NetworkState.current(test.targetContext)
        val network=net.network ?: throw AssertionError("Selected emulator Wi-Fi missing")
        val iface=NetworkInterface.getByName(net.iface) ?: throw AssertionError("Selected interface missing")
        val local=iface.inetAddresses.toList().filterIsInstance<Inet6Address>().firstOrNull {it.isLinkLocalAddress}
        if(local==null) {status("IPV6_TRIAL: no link-local source; multicast feasibility remains unverified");return}
        val destination=Inet6Address.getByAddress(null,InetAddress.getByName("ff02::1").address,iface)
        val nonce=ByteArray(16).also {SecureRandom().nextBytes(it)}
        val packet=ByteArray(24).apply {this[0]=128.toByte();this[7]=1;System.arraycopy(nonce,0,this,8,16)}
        var fd:FileDescriptor?=null;var received=0;val peers=hashSetOf<String>();var stage="socket"
        try {
            fd=Os.socket(OsConstants.AF_INET6,OsConstants.SOCK_DGRAM,OsConstants.IPPROTO_ICMPV6)
            stage="binding";network.bindSocket(fd);Os.bind(fd,local,0)
            Os.setsockoptInt(fd,OsConstants.IPPROTO_IPV6,OsConstants.IPV6_MULTICAST_HOPS,1)
            Os.setsockoptInt(fd,OsConstants.IPPROTO_IPV6,OsConstants.IPV6_MULTICAST_IF,iface.index)
            net.check(test.targetContext);stage="send";Os.sendto(fd,packet,0,packet.size,0,destination,0)
            val deadline=System.nanoTime()+1_000_000_000L
            val poll=StructPollfd().apply {this.fd=fd;events=OsConstants.POLLIN.toShort()}
            val bytes=ByteArray(512)
            stage="receive"
            while(System.nanoTime()<deadline && received<64 && peers.size<32) {
                net.check(test.targetContext)
                if(Os.poll(arrayOf(poll),minOf(100,maxOf(1,(deadline-System.nanoTime())/1_000_000).toInt()))==0) continue
                val sender=InetSocketAddress(0);val n=Os.recvfrom(fd,bytes,0,bytes.size,0,sender);received++
                val address=sender.address
                if(matches(bytes,n,nonce,address) {net.owns(net.scoped(it))}) peers.add(address.hostAddress!!)
            }
            net.check(test.targetContext)
            check(received<=64 && peers.size<=32,"IPv6 trial bounds receive work and correlates nonce before counting peers")
            status("IPV6_TRIAL: selected-Wi-Fi datagram multicast sent; correlated non-self peers=${peers.size}; received=$received; emulator result does not establish physical-network recall")
        } catch(e:Exception) {
            if(e !is ErrnoException && e !is SecurityException && e !is java.io.IOException) throw e
            status("IPV6_TRIAL: platform could not complete $stage (${if(e is ErrnoException) e.errno else e.javaClass.simpleName}); no production discovery enabled")
        } finally {fd?.let {try {Os.close(it)} catch(_:ErrnoException) {}}}
    }
}
