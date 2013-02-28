package org.unallied.mmoserver.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.unallied.mmocraft.net.Packet;
import org.unallied.mmoserver.client.Client;


/**
 * Contains all available players on the server.
 * @author Faythless
 *
 */
public class ServerPlayerPool {
    private final ReentrantReadWriteLock locks = new ReentrantReadWriteLock();
    private final Lock readLock = locks.readLock();
    private final Lock writeLock = locks.writeLock();
    
    /** 
     * Contains all players.
     */
    private final Map<Integer, ServerPlayer> pool = new HashMap<Integer, ServerPlayer>();
    
    /**
     * Adds a player to the player pool
     * @param player The player to add to the pool
     */
    public void addPlayer(ServerPlayer player) {
        writeLock.lock();
        try {
            pool.put(player.getId(), player);
            World.getInstance().addPlayer(player);
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Remove player identified by <code>id</code>.
     * @param id The unique playerId for this Player.
     * @return The player that was removed
     */
    public ServerPlayer removePlayer(int id) {
        writeLock.lock();
        try {
            if (pool.containsKey(id)) {
                World.getInstance().removePlayer(pool.get(id));
            }
            return pool.remove(id);
        } finally {
            writeLock.unlock();
        }
    }
    
	/**
	 * Broadcasts the packet to all players on the server.
	 * @param packet the packet to broadcast
	 */
    public void globalBroadcast(Packet packet) {
    	if (packet != null) {
	    	readLock.lock();
	    	try {
	    		for (ServerPlayer player : pool.values()) {
	    			if (player != null) {
	    				Client c = player.getClient();
	    				if (c != null) {
	    					c.announce(packet);
	    				}
	    			}
	    		}
	    	} finally {
	    		readLock.unlock();
	    	}
    	}
    }
    
    /**
     * Retrieves a player with the given id, or null if no player is found.
     * @param playerId The id of the player to retrieve.
     * @return serverPlayer or null if not found.
     */
    public ServerPlayer getPlayer(Integer playerId) {
        if (playerId == null) {
            return null;
        }
        ServerPlayer result;
        readLock.lock();
        try {
            result = pool.get(playerId);
        } finally {
            readLock.unlock();
        }
        return result;
    }
    
    /**
     * Locks the read lock for the ServerPlayerPool.  You MUST call read unlock when
     * you're done using this.
     */
    public void readLock() {
        readLock.lock();
    }
    
    /**
     * Unlocks the read lock for the ServerPlayerPool.
     */
    public void readUnlock() {
        readLock.unlock();
    }
    
    /**
     * Returns the pool of players.  It is unsafe to modify the returned
     * value unless you call a locking function first.  You should call
     * {@link #readLock()} before reading values unless you don't care about
     * consistency.
     * @return players
     */
    public Map<Integer, ServerPlayer> getPlayers() {
        return pool;
    }
    
    public void update(long delta) {
        readLock.lock();
        try {
            for (ServerPlayer sp : pool.values()) {
                sp.updateLogic(delta);
            }
        } finally {
            readLock.unlock();
        }
    }
}
