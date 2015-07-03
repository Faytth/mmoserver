package org.unallied.mmoserver.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;

import org.unallied.mmocraft.RawPoint;
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.skills.SkillType;
import org.unallied.mmocraft.tools.CollectionTools;

/**
 * Keeps track of all block damage in the game.
 * @author Alexandria
 *
 */
public class BlockDamage {
    
    /**
     * A single node for block damage.  One node is used for each block.  The
     * node contains the amount of HP remaining in the block as well as the
     * damage dealt by players.
     * @author Alexandria
     *
     */
    protected class BlockDamageNode {
        /** 
         * The amount of time in milliseconds that it takes for a block to
         * restore all of its HP.
         */
        public static final int HP_RESTORE_RATE = 20000;
        
        protected Block block;
        
        /** The amount of HP remaining in the block. */
        protected long hpRemaining;
        
        /** 
         * The amount of time that has elapsed which was unaccounted for.
         * This builds up slowly over time until the smallest unit of HP
         * can be restored, at which point it is reduced by the appropriate
         * amount.
         */
        protected long elapsedTime = 0;
        
        /** A map containing all of the player IDs to the amount of damage they've dealt. */
        protected Map<Integer, Long> playerDamageMap = 
                Collections.synchronizedMap(new HashMap<Integer, Long>());
        
        /**
         * Creates a block damage node and deals player damage to the block.
         * @param block The block receiving the damage.
         * @param playerId The id of the player doing the damage.
         * @param damage The amount of damage dealt.
         */
        BlockDamageNode(Block block, int playerId, long damage) {
            this.block = block;
            hpRemaining = this.block.getMaximumHealth() - damage;
            playerDamageMap.put(playerId, damage);
        }
        
        /**
         * Deals damage to the block.
         * @param playerId The player ID dealing the damage.
         * @param damage The amount of damage dealt.
         */
        public void doDamage(int playerId, long damage) {
            Long currentDamage = playerDamageMap.get(playerId);
            Long newDamage = currentDamage == null ? damage : currentDamage + damage;
            
            playerDamageMap.put(playerId, newDamage);
            hpRemaining -= damage;
        }
        
        /**
         * Updates the HP of the block, healing it if necessary.
         * @param delta The amount of time in milliseconds that has passed.
         */
        public void update(long delta) {
            elapsedTime += delta;
            // TODO:  Add support for blocks to have different restore rates.
            int hpToRestore = (int) (elapsedTime * block.getMaximumHealth() / HP_RESTORE_RATE);
            elapsedTime -= hpToRestore * HP_RESTORE_RATE / block.getMaximumHealth();
            hpRemaining += hpToRestore;
            // Cap the HP if it's too high
            hpRemaining = hpRemaining > block.getMaximumHealth() ? block.getMaximumHealth() : hpRemaining;
        }
        
        /**
         * Retrieves the block damage map containing a mapping of all player 
         * IDs to the damage they've dealt.
         * @return playerDamageMap
         */
        public Map<Integer, Long> getPlayerDamageMap() {
            return playerDamageMap;
        }
    }
    
    /**
     * Keeps track of damaged blocks.  The block damage map maps block locations 
     * to its block.
     */
    private Map<RawPoint, BlockDamageNode> blockDamageMap = 
            Collections.synchronizedMap(new HashMap<RawPoint, BlockDamageNode>());
    
    /**
     * Creates a BlockDamage class, which contains all of the block damage for
     * all of the players in the entire world.
     */
    public BlockDamage() {
    }
    
    /**
     * Clears all damage from the block at <code>point</code>.
     * 
     * @param point The x,y coordinate of the block.  Each block is 1 unit.
     *              Top-left corner is (0, 0).
     */
    public void clearDamage(RawPoint point) {
        if (point == null) { // Guard
            return;
        }
        blockDamageMap.remove(point);
    }
    
    /**
     * Deals damage to a block at <code>point</code>.
     * @param point The x,y coordinate of the block.  Each block is 1 unit.
     *              Top-left corner is (0, 0).
     * @param playerId The id of the player doing the damage.
     * @param damage The amount of damage dealt.
     * @param block A copy of the block receiving the damage.
     * @return True if the block has been broken, else false.
     */
    public boolean doDamage(RawPoint point, int playerId, long damage, Block block) {
        boolean result = false;
        if (blockDamageMap.containsKey(point)) {
            synchronized (blockDamageMap) {
                blockDamageMap.get(point).doDamage(playerId, damage);
            }
        } else { // No key, so go ahead and add it
            blockDamageMap.put(point, new BlockDamageNode(block, playerId, damage));
        }
        synchronized (blockDamageMap) {
            BlockDamageNode node = blockDamageMap.get(point);
            // Block was destroyed.
            if (node != null && node.hpRemaining <= 0) {
                // Sort the players by the amount of damage they dealt to the blocks.
                SortedSet<Map.Entry<Integer, Long>> playerDamageMap = 
                        CollectionTools.entriesSortedByValues(node.getPlayerDamageMap());
                // Go through the players and select the person with the highest damage who's online.
                for (Map.Entry<Integer, Long> entry : playerDamageMap) {
                    ServerPlayer player = Server.getInstance().getPlayer(entry.getKey());
                    if (player != null) { // Player is online and is the highest damager.
                        try {
                            long playerDamage = entry.getValue();
                            playerDamage = playerDamage > node.block.getMaximumHealth() ? 
                                    node.block.getMaximumHealth() : playerDamage;
                            playerDamage = playerDamage < 0 ? 0 : playerDamage;
                            player.addExperience(SkillType.MINING, playerDamage / 9);
                            player.addItem(node.block.getItem().getId(), 1);
                            break;
                        } catch (Throwable t) {
                            // An error occurred selecting this winner.  Did they log out?
                            t.printStackTrace();
                        }
                    }
                }
                blockDamageMap.remove(point);
                result = true;
            }
        }
        
        return result;
    }
    
    /**
     * Updates all of the blocks, restoring their HP and removing them if 
     * needed.
     * @param delta The amount of time that has passed in milliseconds.
     */
    public void update(long delta) {
        synchronized (blockDamageMap) {
            // Iterate over the block damage map, removing nodes if they're at full HP.
            Iterator<Map.Entry<RawPoint, BlockDamageNode>> iter = blockDamageMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<RawPoint, BlockDamageNode> entry = iter.next();
                BlockDamageNode node = entry.getValue();
                node.update(delta);
                if (node.hpRemaining >= node.block.getMaximumHealth()) {
                    iter.remove();
                }
            }
        }
    }
}
