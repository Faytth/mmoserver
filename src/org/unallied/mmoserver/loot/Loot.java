package org.unallied.mmoserver.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.unallied.mmocraft.LootIntf;
import org.unallied.mmocraft.items.Item;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

/**
 * A class containing all of the loot functionality for something that is lootable.
 * This contains groups of items with different quantities and probabilities.
 * TODO:  Convert drop chance from a double to either an int or a long.  This
 * way we can avoid rounding errors if done properly.
 * @author Alexandria
 *
 */
public class Loot implements LootIntf {
    private Random random = new Random();
    /**
     * LootGroups contain LootNodes, and a loot node contains the information
     * for a particular item.
     * @author Alexandria
     *
     */
    private class LootNode {
        private int itemId;
        private int quantity;
        private double dropChance;
        
        public LootNode(int itemId, int quantity, double dropChance) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.dropChance = dropChance;
        }
    }
    
    /**
     * A loot group contains all of the loot nodes in that group.  These are
     * the items that can be selected from when determining loot.  At most, only 
     * one loot node is selected per group.
     * @author Alexandria
     *
     */
    private class LootGroup {
        private List<LootNode> nodes = new ArrayList<LootNode>();
        
        /**
         * Returns the item which should be awarded.  This involves random
         * numbers and drop chance calculations.
         * @param lootMultiplier The multiplier to use for loot.  Examples are as follows:<br />
         * Multiplier of 1:  Standard loot drop chance
         * Multiplier of 2 when loot nodes add up to 50% chance:  (100% - 50%) ^ 2 = 25% chance to not receive loot.
         * @return loot or null if no loot should be given.
         */
        public Item getLoot(double lootMultiplier) {
            if (lootMultiplier <= 0 || nodes.size() == 0) {
                return null;
            }
            double lootChance = 0;
            for (LootNode node : nodes) {
                lootChance += node.dropChance;
            }
            double missChance = 100.0 - lootChance;
            missChance = missChance < 0 ? 0 : missChance;
            missChance /= 100.0;
            missChance = Math.pow(missChance, lootMultiplier);
            double totalDropChance = 0;
            double randVal = random.nextDouble();
            if (randVal < missChance) {
                return null;
            }

            randVal = random.nextDouble() * lootChance;
            for (LootNode node : nodes) {
                totalDropChance += node.dropChance;
                if (randVal < totalDropChance) {
                    return new Item(node.itemId, node.quantity);
                }
            }
            
            return null;
        }
        
        /**
         * Adds a loot node to the group.
         * @param node The loot node to add.  If null, does nothing.
         */
        public void addNode(LootNode node) {
            if (node != null) {
                nodes.add(node);
            }
        }
    }
    
    private Map<Short, LootGroup> loot = new HashMap<Short, LootGroup>();

    public List<Item> getLoot(double lootMultiplier) {
        List<Item> result = new ArrayList<Item>();
        
        // Go through each group, adding an item if it should be added
        for (LootGroup group : loot.values()) {
            Item item = group.getLoot(lootMultiplier);
            if (item != null) {
                result.add(item);
            }
        }
        
        return result;
    }
    
    public void addItem(SeekableLittleEndianAccessor slea) {
        try {
            int itemId = slea.readInt();
            short groupId = slea.readShort();
            int quantity = slea.readInt();
            double dropChance = slea.readDouble();
            
            if (itemId >= 0 && groupId >= 0 && quantity > 0 && dropChance > 0) {
                LootGroup lootGroup = loot.get(groupId);
                if (lootGroup == null) {
                    lootGroup = new LootGroup();
                    loot.put(groupId, lootGroup);
                }
                lootGroup.addNode(new LootNode(itemId, quantity, dropChance));
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
