package fsm.errors.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import liquidjava.specification.ExternalRefinementsFor;
import liquidjava.specification.Refinement;
import liquidjava.specification.RefinementAlias;
import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@ExternalRefinementsFor("java.net.DatagramSocket")
@RefinementAlias("Port(int x) { 0 <= x && x <= 65355}")
@RefinementAlias("Nat(int x) {x >= 0}")
@RefinementAlias("Pos(int x) {x > 0}")
@RefinementAlias("TrafficClass(int tc) {0 <= tc && tc <= 255}")
@StateSet({ "unbound", "bound", "connected", "closed" })
public interface DatagramSocketRefinementsExpert {

	@StateRefinement(to = "bound(this)")
	void DatagramSocket();

	@StateRefinement(to = "bound(this)")
	void DatagramSocket(@Refinement("Port(_)") int port);

	@StateRefinement(to = "bound(this)")
	void DatagramSocket(@Refinement("Port(_)") int port, InetAddress laddr);

	@StateRefinement(to = "bound(this)")
	void DatagramSocket(SocketAddress bindaddr);

	@StateRefinement(from = "unbound(this)", to = "bound(this)")
	void bind(SocketAddress addr);

	@StateRefinement(from = "!closed(this)", to = "closed(this)")
	void close();

	@StateRefinement(from = "!closed(this)", to = "connected(this)")
	void connect(InetAddress address, @Refinement("Port(_)") int port);

	@StateRefinement(from = "!closed(this)", to = "connected(this)")
	void connect(SocketAddress addr);

	@StateRefinement(from = "connected(this)", to = "bound(this)")
	void disconnect();

	@StateRefinement(from = "connected(this)")
	@StateRefinement(from = "closed(this)")
	InetAddress getInetAddress();

	@StateRefinement(from = "connected(this)")
	@StateRefinement(from = "closed(this)")
	@Refinement("Port(_) || _ == -1")
	int getPort();

	@StateRefinement(from = "connected(this)")
	@StateRefinement(from = "closed(this)")
	SocketAddress getRemoteSocketAddress();

	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	SocketAddress getLocalSocketAddress();

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void send(DatagramPacket p);

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void receive(DatagramPacket p);

	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	InetAddress getLocalAddress();

	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	@Refinement("Port(_) || _ == -1")
	int getLocalPort();

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void setSoTimeout(@Refinement("Nat(_)") int timeout);

	@Refinement("Nat(_)")
	int getSoTimeout();

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void setSendBufferSize(@Refinement("Pos(_)") int size);

	@Refinement("Pos(_)")
	int getSendBufferSize();

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void setReceiveBufferSize(@Refinement("Pos(_)") int size);

	@Refinement("Pos(_)")
	int getReceiveBufferSize();

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void setReuseAddress(boolean on);


	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	public void setBroadcast(boolean on);

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	void setTrafficClass(@Refinement("TrafficClass(_)") int tc);

	@Refinement("TrafficClass(_)")
	int getTrafficClass();

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	<T> DatagramSocket setOption(SocketOption<T> name, T value);

	@StateRefinement(from = "unbound(this)")
	@StateRefinement(from = "bound(this)")
	@StateRefinement(from = "connected(this)")
	<T> T getOption(SocketOption<T> name);
}