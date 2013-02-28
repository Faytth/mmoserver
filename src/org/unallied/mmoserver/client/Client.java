package org.unallied.mmoserver.client;

import java.util.Iterator;
import java.util.List;

import org.apache.mina.core.session.IoSession;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.net.sessions.LoginSession;
import org.unallied.mmoserver.server.Server;
import org.unallied.mmoserver.server.ServerPlayer;
import org.unallied.mmoserver.server.World;


public class Client {
    public static final String CLIENT_KEY = "CLIENT";
    
    private IoSession session;
    private ServerPlayer player;
    private int accountId;
    private boolean loggedIn = false;
    //private long lastPong;
    
    // Used during the login process.  Stores important info, like server/client nonce
    public LoginSession loginSession = new LoginSession();
    
    public Client(IoSession session) {
        this.session = session;
    }
    
    /**
     * Return the IoSession associated with this connection
     * @return the IoSession for this connection
     */
    public synchronized IoSession getSession() {
        return session;
    }
    
    /**
     * Returns the player associated with this account
     * @return the player
     */
    public ServerPlayer getPlayer() {
        return player;
    }
    
    public void setPlayer(ServerPlayer player) {
        player.setClient(this);
        this.player = player;
    }
    
    public void loadPlayer() {
        Server.getInstance().getDatabase().getPlayer(this, this.getAccountName());
    }
    
    public boolean isLoggedIn() {
        return loggedIn;
    }
    
    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }
    
    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }
    
    public int getAccountId() {
        return accountId;
    }
    
    /**
     * Gets the account name for the client.  This is the same account name
     * that's used by the login session.
     * @return accountName
     */
    public String getAccountName() {
        return loginSession.getUsername();
    }
    
    /**
     * Sets the account name for this client.  This is the same account name
     * that's used by the login session.
     * @param accountName the new account name
     */
    public void setAccountName(String accountName) {
        loginSession.setUsername(accountName);
    }
    
    /**
     * Remove a player from the server
     */
    private void removePlayer() {
        Server.getInstance().getServerPlayerPool().removePlayer(player.getId());
    }
    
    /**
     * Disconnects a player from the server
     */
    public final void disconnect() {
        try {
            if (player != null && isLoggedIn()) {
                selectiveBroadcast(player, PacketCreator.getPlayerDisconnect(player));
                removePlayer();
                // Revive dead players
                if (!player.isAlive()) {
                    player.revive();
                }
                Server.getInstance().getDatabase().savePlayer(player);
            }
        } finally {
            player = null;
            System.out.println("Player was disconnected.  Closing connection for: " + session.getRemoteAddress());
            session.close(false);
        }
    }
    
    /**
     * Empties out the connection
     */
    public final void empty() {
        player = null;
        session = null;
    }
    
    /**
     * Update the time that the last pong was received to the current time
     */
    public void pongReceived() {
        //lastPong = System.currentTimeMillis();
    }
    
    /**
     * Send a ping to the client to make sure it's still alive
     */
    public void sendPing() {
        //final long pingSent = System.currentTimeMillis();
        //announce(PacketCreator.getPing());
        // FIXME:  Not finished!!!
    }
    
    /**
     * Sends a packet to the client.  Use this for all packets.
     * @param packet The packet to send to the client.
     */
    public void announce(Packet packet) {
    	try {
    		session.write(packet);
    	} catch (Throwable t) {}
    }

    /**
     * Broadcasts to all players near this player, but NOT to this player.
     * @param player the player to center the broadcast around.
     * @param packet the packet to broadcast
     */
    public void selectiveBroadcast(ServerPlayer player, Packet packet) {
        /*
         *  For all chunks in the drawn radius (see constants) of the player's
         *  chunk, send a packet to the players in the chunk.
         */
        List<ServerPlayer> players = World.getInstance().getNearbyPlayers(player.getLocation());
        Iterator<ServerPlayer> iter = players.iterator();
        while (iter.hasNext()) {
            ServerPlayer p = iter.next();
            if (p.getId() != player.getId()) {
                try {
                    p.getClient().announce(packet);
                } catch (NullPointerException e) {
                    if (p.getClient() != null) {
                        Server.getInstance().logout(p.getClient());
                    } else {
                        Server.getInstance().getServerPlayerPool().removePlayer(p.getId());
                    }
                }
            }
        }
    }

    /**
     * Broadcasts packet to all players centered around <code>player</code> INCLUDING the
     * player.
     * @param player the player to center the broadcast around.
     * @param packet the packet to broadcast
     */
	public void broadcast(ServerPlayer player, Packet packet) {
		selectiveBroadcast(player, packet);
		try {
			player.getClient().announce(packet);
		} catch (NullPointerException e) {
		}
	}
	
	/**
	 * Broadcasts the packet to all players on the server.
	 * @param packet the packet to broadcast
	 */
	public void globalBroadcast(Packet packet) {
		Server.getInstance().globalBroadcast(packet);	
	}

	/**
	 * Retrieves nearby players, monsters, and other important information.
	 */
    public void selectiveConvergecast() {
        /*
         *  For all chunks in the drawn radius (see constants) of the player's
         *  chunk, send a packet about them to the player
         */
        List<ServerPlayer> players = World.getInstance().getNearbyPlayers(player.getLocation());
        List<ServerMonster> monsters = World.getInstance().getNearbyMonsters(player.getLocation());
        for (ServerPlayer p : players) {
            if (p.getId() != player.getId()) {
                announce(PacketCreator.getPlayerMovement(p));
            }
        }
        for (ServerMonster monster : monsters) {
            announce(PacketCreator.getMonsterMovement(monster));
        }
    }
}
