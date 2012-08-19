package server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contains all available players on the server.
 * @author Faythless
 *
 */
public class PlayerPool {
    private final ReentrantReadWriteLock locks = new ReentrantReadWriteLock();
//    private final Lock readLock = locks.readLock();
    private final Lock writeLock = locks.writeLock();
    
    /* Iteration is probably important, and we might add/remove players inside
     * of a loop.  If this is a normal HashMap, this will undoubtedly cause
     * problems.  We might be able to get away with changing this later on
     * if our code is well thought out to avoid this issue.
     */
    private final Map<Integer, ServerPlayer> pool = new LinkedHashMap<Integer, ServerPlayer>();
        
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
     * Remove player identified by <code>key</code>.
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
    
    
}
