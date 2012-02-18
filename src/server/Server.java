package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import net.MMOServerHandler;
import net.mina.MMOCodecFactory;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import constants.ServerConstants;

import tools.DatabaseConnection;
import tools.PrintError;

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
        
    }
        
    /**
     * Runs the server until termination.  Note that this method will not
     * return until the server terminates.
     */
    private void run() {
        try {
            
            try {
                Connection conn = DatabaseConnection.getConnection();
                // Log everyone out!  We're starting the server!
                PreparedStatement ps = conn.prepareStatement("UPDATE account SET account_loggedin = 0");
                ps.executeUpdate();
                ps.close();
            } catch (NullPointerException e) {
                PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
            }
            
            acceptor = new NioSocketAcceptor();
            acceptor.getFilterChain().addLast("codec", (IoFilter) new ProtocolCodecFilter(new MMOCodecFactory()));
            acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, ServerConstants.PACKET_TIMEOUT);
            acceptor.setHandler(new MMOServerHandler(PacketProcessor.getInstance()));
            try {
                acceptor.bind(new InetSocketAddress(ServerConstants.SERVER_PORT));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            // Disable Nagle's algorithm.  We want FAST speed... not efficient transfers
            ((SocketSessionConfig) acceptor.getSessionConfig()).setTcpNoDelay(true);
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
}
