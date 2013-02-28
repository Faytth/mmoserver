package org.unallied.mmoserver.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.items.ItemManager;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.constants.DatabaseConstants;
import org.unallied.mmoserver.constants.ServerConstants;
import org.unallied.mmoserver.database.DatabaseAccessor;
import org.unallied.mmoserver.database.MySQLDatabase;
import org.unallied.mmoserver.monsters.MonsterSpawner;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.monsters.ServerMonsterManager;
import org.unallied.mmoserver.net.MMOServerHandler;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.net.PacketProcessor;
import org.unallied.mmoserver.net.mina.MMOCodecFactory;



/**
 * The main server.  A server controls all players, monsters, etc. inside of
 * it.  This is a singleton class.  If multiple servers are needed, then this
 * program should be run multiple times.  Eventually we need to separate the
 * login server from this type of server.
 * @author Faythless
 *
 */
public class Server {
    
    private ServerPlayerPool players = new ServerPlayerPool();
    private ServerMonsterPool monsters = new ServerMonsterPool();
    private DatabaseAccessor database = new MySQLDatabase();
    
    private IoAcceptor acceptor;
    
    /** True if the server is online.  False if the server should stop running. */
    private boolean online;
    
    /**
     * Private constructor for Singleton pattern
     */
    private Server() {
        online = true;
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
        
        // Load variables from server config file if applicable.
        loadVariables();
        
        MonsterSpawner.getInstance().setPlayers(players);
        MonsterSpawner.getInstance().setMonsters(monsters);
        
        ItemManager.load(ClientConstants.ITEM_PACK_LOCATION);
        ServerMonsterManager.getInstance().load(ClientConstants.MONSTER_PACK_LOCATION);
        World.getInstance().generateWorld();
        (new Thread(new ServerUpdater())).start();
    }
    
    /**
     * Loads the server's variables, such as database information, from a
     * property configuration file.
     */
    private void loadVariables() {
        try {
            Properties prop = new Properties();
            
            prop.load(new FileInputStream(ServerConstants.SERVER_CONF_FILE));
            DatabaseConstants.DB_URL = prop.getProperty(ServerConstants.CONF_DB_URL, DatabaseConstants.DB_URL);
            DatabaseConstants.DB_USER = prop.getProperty(ServerConstants.CONF_DB_USER, DatabaseConstants.DB_USER);
            DatabaseConstants.DB_PASS = prop.getProperty(ServerConstants.CONF_DB_PASS, DatabaseConstants.DB_PASS);
            
            System.out.println("Successfully loaded server property file.");
        } catch (Throwable t) {
            System.err.println("Unable to load server property file: " + t.getMessage());
        }
    }
        
    /**
     * Starts the server.
     */
    private void run() {
        // Add shutdown hook to save everything once the server exits.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                Server.getInstance().saveCharacters();
                System.out.println("Characters saved.");
            }
        });
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
                
                // Save the character's data.
                ServerPlayer otherPlayer = other.getPlayer();
                if (otherPlayer != null) {
                    getDatabase().savePlayer(other.getPlayer());
                    client.setPlayer(other.getPlayer()); // The client's player is outdated, so load the latest one.
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
        
        online = false;
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
        return online;
    }
    
    /**
     * Saves all players.
     */
    public void saveCharacters() {
        players.readLock();
        try {
            for (ServerPlayer player : players.getPlayers().values()) {
                getDatabase().savePlayer(player);
            }
        } finally {
            players.readUnlock();
        }
    }
    
    public static void main(String args[]) {
        try {
            Server.getInstance().run();
        } catch (Throwable t) {
            System.out.println("Server has stopped running.");
            System.out.println(t.getMessage());
            t.printStackTrace();
        }
        // If we want to have console commands, then add support for it here.
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
     * Broadcasts to all players near this location.
     * @param location The location to center the broadcast around.
     * @param packet the packet to broadcast
     */
    public void localBroadcast(BoundLocation location, Packet packet) {
        /*
         *  For all chunks in the drawn radius (see constants) of the player's
         *  chunk, send a packet to the players in the chunk.
         */
        List<ServerPlayer> players = World.getInstance().getNearbyPlayers(location);
        Iterator<ServerPlayer> iter = players.iterator();
        while (iter.hasNext()) {
            ServerPlayer p = iter.next();
            try {
                p.getClient().announce(packet);
            } catch (NullPointerException e) {
                if (p != null && p.getClient() != null) {
                    logout(p.getClient());
                } else {
                    players.remove(p.getId());
                }
            }
        }
    }
	
	/**
	 * Broadcasts the packet to all players on the server.
	 * @param packet the packet to broadcast
	 */
	public void globalBroadcast(Packet packet) {
		players.globalBroadcast(packet);
	}

	/**
	 * Sends a player's info to the client if they're online.  Otherwise returns
	 * null.
	 * @param client The client requesting the information.
	 * @param playerId The id of the player whose information is being requested.
	 */
    public void getPlayerInfo(Client client, int playerId) {
        ServerPlayer player = players.getPlayer(playerId);
        if (player != null) {
            client.announce(PacketCreator.getPlayerInfo(player));
        }
    }
    
    /**
     * Sends a monster's info to the client if they're close enough to the client.
     * Otherwise returns null.
     * @param client The client requesting the information.
     * @param monsterId The id of the monster whose information is being requested.
     */
    public void getMonsterInfo(Client client, int monsterId) {
        ServerMonster monster = monsters.getMonster(monsterId);
        if (monster != null && 
                monster.getLocation().getDistance(client.getPlayer().getLocation()) < ServerConstants.OBJECT_DESPAWN_DISTANCE) {
            client.announce(PacketCreator.getMonsterInfo(monster));
        }
    }
    
    /**
     * Retrieves a player from the server if they're online.  Otherwise returns
     * null.
     * @param playerId The id of the player whose information is being requested.
     * @return player
     */
    public ServerPlayer getPlayer(int playerId) {
        return players.getPlayer(playerId);
    }
    
    /**
     * Retrieves the monster pool.
     * @return monsters
     */
    public ServerMonsterPool getServerMonsterPool() {
        return monsters;
    }
    
    /**
     * Retrieves the player pool.
     * @return players
     */
    public ServerPlayerPool getServerPlayerPool() {
        return players;
    }
}
