package org.unallied.mmoserver.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.unallied.mmoserver.monsters.ServerMonster;

public class ServerMonsterPool {
    private final ReentrantReadWriteLock locks = new ReentrantReadWriteLock();
    private final Lock readLock = locks.readLock();
    private final Lock writeLock = locks.writeLock();
    
    /** Contains all of the monsters that are currently spawned. */
    private final Map<Integer, ServerMonster> pool = new HashMap<Integer, ServerMonster>();
    
    /**
     * Adds a monster to the monster pool.
     * @param monster The monster to add to the pool.
     */
    public void addMonster(ServerMonster monster) {
        writeLock.lock();
        try {
            pool.put(monster.getId(), monster);
            World.getInstance().addMonster(monster);
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Remove a monster identified by <code>id</code>.
     * @param id The unique monsterId for this Monster.
     * @return The monster that was removed
     */
    public ServerMonster removeMonster(int id) {
        writeLock.lock();
        try {
            if (pool.containsKey(id)) {
                World.getInstance().removeMonster(pool.get(id));
            }
            return pool.remove(id);
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Retrieves a monster with the given id, or null if no monster is found.
     * @param monsterId The id of the monster to retrieve.
     * @return serverMonster or null if not found.
     */
    public ServerMonster getMonster(Integer monsterId) {
        if (monsterId == null) {
            return null;
        }
        ServerMonster result;
        readLock.lock();
        try {
            result = pool.get(monsterId);
        } finally {
            readLock.unlock();
        }
        return result;
    }
    
    /**
     * Locks the read lock for the ServerMonsterPool.
     */
    public void readLock() {
        readLock.lock();
    }
    
    /**
     * Unlocks the read lock for the ServerMonsterPool.
     */
    public void readUnlock() {
        readLock.unlock();
    }
    
    /**
     * Returns the pool of monsters.  It is unsafe to modify the returned
     * value unless you call a locking function first.  You should call
     * {@link #readLock()} before reading values unless you don't care about
     * consistency.
     * @return monsters
     */
    public Map<Integer, ServerMonster> getMonsters() {
        return pool;
    }
    
    /**
     * Goes through all monsters, updating them.
     * @param delta The length of time in milliseconds since update was last called.
     */
    public void update(long delta) {
        readLock.lock();
        try {
            for (ServerMonster sm : pool.values()) {
                sm.update(delta);
            }
            // Remove monsters that don't have anyone nearby
            try {
                Iterator<ServerMonster> iter = pool.values().iterator();
                while (iter.hasNext()) {
                    ServerMonster monster = iter.next();
                    if (!monster.hasNearbyTarget()) {
                        iter.remove();
                        World.getInstance().removeMonster(monster);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

        } finally {
            readLock.unlock();
        }
    }
}
