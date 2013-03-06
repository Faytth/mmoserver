package org.unallied.mmoserver.monsters;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmoserver.constants.ServerConstants;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.Server;
import org.unallied.mmoserver.server.ServerMonsterPool;
import org.unallied.mmoserver.server.ServerPlayer;
import org.unallied.mmoserver.server.ServerPlayerPool;
import org.unallied.mmoserver.server.World;

/**
 * Used to spawn and keep track of all monsters in the game.  Monsters spawn
 * around players.
 * @author Alexandria
 *
 */
public class MonsterSpawner {
    
    private Random random = new Random();
    
    /** Monsters can only spawn every SPAWN_RATE milliseconds. */
    private static final long SPAWN_RATE = 100;
    
    /** 
     * The maximum number of monsters that are allowed to spawn near a player. 
     * Testing on an i7-2600k computer has shown that this can go to about 400
     * monsters on screen at the same time before the client starts to see a 
     * drop in FPS.
     */
    private static final int NEARBY_MONSTER_MAX = 30;
    
    /** 
     * The id of the last monster that was created.  This should increase by
     * one each time a new monster is created and loop back to 1 when needed.
     */
    private static Integer lastMonsterId = 0;
    
    /** 
     * The milliseconds towards the next spawn.  Monsters only have a chance 
     * of spawning every SPAWN_RATE milliseconds.
     */
    private long lastSpawnTime = 0;
    
    /** Contains all players that are currently logged in. */
    private ServerPlayerPool players;
    
    /** 
     * Keeps track of all monsters that are spawned.  When a monster is spawned,
     * it is initially aggroed onto a player.  If no player on the monster's aggro
     * list is close enough, the monster will despawn on the next update.
     */
    private ServerMonsterPool monsters;
    // TODO:  Make a MonsterPool?
    
    private MonsterSpawner() {
    }
    
    private static class MonsterSpawnerHolder {
        private static final MonsterSpawner instance = new MonsterSpawner();
    }
    
    /**
     * Retrieves the singleton instance of MonsterSpawner.
     * @return
     */
    public static MonsterSpawner getInstance() {
        return MonsterSpawnerHolder.instance;
    }
    
    /**
     * Assigns the player pool to use when performing player logic.  This must
     * be set before calling update.
     * @param players The player pool to use when performing player logic.
     */
    public void setPlayers(ServerPlayerPool players) {
        this.players = players;
    }
    
    /**
     * Assigns the monster pool to use when performing monster logic.  This must
     * be set before calling update.
     * @param monsters The monster pool to use when performing monster logic.
     */
    public void setMonsters(ServerMonsterPool monsters) {
        this.monsters = monsters;
    }
    
    /**
     * Retrieves a new monster ID.  This should be called whenever a new monster
     * is spawned to retrieve the id.  You should NEVER access lastMonsterId
     * in any other way.
     * @return
     */
    public int getNewMonsterId() {
        synchronized (lastMonsterId) {
            if (lastMonsterId == Integer.MAX_VALUE) {
                lastMonsterId = 0;
            }
            return ++lastMonsterId;
        }
    }
    
    /**
     * Spawns a monster near <code>player</code>.  The monster will be roughly
     * around the level of the region the player is in.
     * @param player The player to spawn a monster around.
     */
    public void spawnMonster(ServerPlayer player) {
        if (player == null) { // Guard
            return;
        }
        
        try {
            BoundLocation location = new BoundLocation(player.getLocation());
            float rand = random.nextFloat();
            if (rand < 0.5f) { // Go to the left
                location.moveLeft(ServerConstants.MONSTER_SPAWNER_MIN_DISTANCE + 
                        ServerConstants.MONSTER_SPAWNER_DISTANCE * (rand*2));
            } else { // Go to the right
                location.moveRight(ServerConstants.MONSTER_SPAWNER_MIN_DISTANCE + 
                        ServerConstants.MONSTER_SPAWNER_DISTANCE * ((rand-0.5f) * 2));
            }
            ServerMonster newMonster = new ServerMonster(
                    World.getInstance().getMonster(location),
                    getNewMonsterId(), location);
            newMonster.setAggro(player); // Give player initial aggro.
            monsters.addMonster(newMonster);
            // Tell nearby players about monster
            Server.getInstance().localBroadcast(location, PacketCreator.getMonsterInfo(newMonster));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    /**
     * Attempts to spawn a monster for the given player.  This takes into
     * account the player's current region.
     * @param player The player to possibly spawn a monster around.
     */
    private void attemptToSpawnMonster(ServerPlayer player) {
        if (player == null) { // Guard
            return;
        }
        
        float spawnChance = World.getInstance().getSpawnChance(player.getLocation());
        
        // Modify spawn chance based on the number of nearby monsters
        int nearbyMonsters = World.getInstance().getNearbyMonsters(player.getLocation()).size();
        
        spawnChance *= (2 *  (1f - (1f * nearbyMonsters / NEARBY_MONSTER_MAX)));
        // Further chance when really low on nearby monsters
        if (nearbyMonsters < 5) {
            spawnChance *= 3;
        }
        spawnChance /= 4; // Spawn chance is too high, so let's lower it.  A lot.
        
        if (random.nextFloat() < spawnChance && nearbyMonsters < NEARBY_MONSTER_MAX) {
            // We should spawn a monster.
            spawnMonster(player);
        }
    }
    
    /**
     * Updates the logic for the monster spawner, iterating over the players
     * in the game and determining which players need to have monsters spawned.
     * @param delta
     */
    public void update(long delta) {
        if (players == null) { // Guard
            return;
        }
        
        // Try to spawn monsters
        try {
            players.update(delta);
            
            lastSpawnTime += delta;
            Map<Integer, ServerPlayer> pool = null;
            players.readLock();
            try {
                pool = new HashMap<Integer, ServerPlayer>(players.getPlayers());
            } finally {
                players.readUnlock();
            }

            while (lastSpawnTime >= SPAWN_RATE) {
                /*
                 *  Now pool contains all of the players.  We're outside of the lock
                 *  because this next part is very intensive.
                 */
                for (ServerPlayer player : pool.values()) {
                    attemptToSpawnMonster(player);
                }
                
                lastSpawnTime -= SPAWN_RATE;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        
        // Update the monsters
        monsters.update(delta);
    }
}
