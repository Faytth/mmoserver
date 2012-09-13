package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import net.MMOServerHandler;
import net.PacketProcessor;
import net.mina.MMOCodecFactory;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.net.Packet;

import client.Client;

import database.DatabaseAccessor;
import database.DummyDatabase;

/**
 * The main server.  A server controls all players, monsters, etc. inside of
 * it.  This is a singleton class.  If multiple servers are needed, then this
 * program should be run multiple times.  Eventually we need to separate the
 * login server from this type of server.
 * @author Faythless
 *
 */
public class Server {
    
    private PlayerPool players = new PlayerPool();
    private DatabaseAccessor database = new DummyDatabase();
    
    private IoAcceptor acceptor;
        
    /**
     * Private constructor for Singleton pattern
     */
    private Server() {
        init();
    }
    
    /**
     * A holder for the singleton pattern.  Employs thread-safe lazy loading.
     * @author Faythless
     *
     */
    private static class ServerHolder {
        public static final Server instance = new Server();
    }
    
    /**
     * Initializes the server for execution.
     */
    private void init() {
        World.getInstance().generateWorld();
    }
        
    /**
     * Runs the server until termination.  Note that this method will not
     * return until the server terminates.
     */
    private void run() {
        database.globalLogout();

        acceptor = new NioSocketAcceptor();
        acceptor.getFilterChain().addLast("codec", (IoFilter) new ProtocolCodecFilter(new MMOCodecFactory()));
        acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, ClientConstants.PACKET_TIMEOUT);
        acceptor.setHandler(new MMOServerHandler(PacketProcessor.getInstance()));
        try {
            acceptor.bind(new InetSocketAddress(ClientConstants.SERVER_PORT));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        // Disable Nagle's algorithm.  We want FAST speed... not efficient transfers
        ((SocketSessionConfig) acceptor.getSessionConfig()).setTcpNoDelay(true);
        System.out.println("Server started.");       
    }
    
    /**
     * Log out all clients with the given account id.
     * @param client The client that should NOT be logged out.
     * All clients sharing this account id will be logged out.
     */
    public void logout(Client client) {
        // Get all sessions
        Map<Long, IoSession> sessions = acceptor.getManagedSessions();
        
        // Search for a session with the given account ID
        for(Map.Entry<Long, IoSession> entry : sessions.entrySet()) {
            Client other = (Client) entry.getValue().getAttribute(Client.CLIENT_KEY);
            
            // This client has the same id!  Log it out!
            if (!other.equals(client) && client.getAccountId() == other.getAccountId()) {
                // Remove player from the player pool
                ServerPlayer player = client.getPlayer();
                if (player != null) {
                    players.removePlayer(player.getId());
                }
                other.disconnect();
                System.out.println("Client has same ID.  Closing connection for: " + entry.getValue().getRemoteAddress());
                entry.getValue().close(true); // Close the client's session
                // Note:  We don't break, because there could be more than one.
            }
        }
    }
    
    /**
     * Shuts down the server
     */
    public void shutdown() {
        acceptor.unbind();
    }
    
    /**
     * Returns the server
     * @return the server
     */
    public static Server getInstance() {
        return ServerHolder.instance;
    }
    
    /**
     * Returns true if the server is still running
     * @return
     */
    public boolean isOnline() {
        return true;
    }
    
    public static void main(String args[]) {
        Server.getInstance().run();
    }

    /**
     * Adds a player to the player pool
     * @param player the player to add to the pool
     */
    public void addPlayer(ServerPlayer player) {
        if (player != null) {
            players.addPlayer(player);
        }
    }

    /**
     * Returns the database that this server is using, such as MySQL.
     * @return database
     */
	public DatabaseAccessor getDatabase() {
		return database;
	}

	/**
	 * Broadcasts the packet to all players on the server.
	 * @param packet the packet to broadcast
	 */
	public void globalBroadcast(Packet packet) {
		players.globalBroadcast(packet);
	}
}
