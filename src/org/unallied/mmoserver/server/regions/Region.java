package org.unallied.mmoserver.server.regions;

import java.util.Collection;
import java.util.Random;

import libnoiseforjava.exception.ExceptionInvalidParam;
import libnoiseforjava.module.Perlin;

import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmoserver.monsters.ServerMonsterData;
import org.unallied.mmoserver.monsters.ServerMonsterManager;

/**
 * Regions are "groups" of chunks that behave in a particular manner.
 * Regions have their own biome, name, lighting, monsters, and so on.
 * A Region is responsible for telling the World how the blocks inside
 * of the region should be generated.  This means that a Region can
 * determine its geometric structure as well as the blocks that are
 * contained within that region.
 * @author Faythless
 *
 */
public abstract class Region {
    
    private static Random random = new Random();
    
    /**
     * The world-based x coordinate for this region in blocks
     */
    private int x;
    
    /**
     * The world-based y coordinate for this region in blocks
     */
    private int y;
    
    /**
     * The region directly to the left of this region
     */
    protected Region leftRegion = null;
    
    /**
     * The region directly above this region
     */
    protected Region topRegion = null;
    
    /**
     * The region directly to the right of this region
     */
    protected Region rightRegion = null;
    
    /**
     * The region directly below this region
     */
    protected Region bottomRegion = null;
    
    /**
     * 
     * @param x The world-based x coordinate of this region (in blocks)
     * @param y The world-based y coordinate of this region (in blocks)
     */
    public Region(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Gets the frequency.  Higher values cause more "random" terrain.
     * Lower values cause very straight, unchanging terrain.
     * @return frequency
     */
    protected abstract double getFrequency();
    
    /**
     * The lacunarity is the frequency multiplier between successive octaves.
     * @return lacunarity
     */
    protected abstract double getLacunarity();
    
    /**
     * Gets the number of octaves.  Lower values make the land change slowly.
     * Smaller values make the land change quickly (higher resolution).
     * @return octaveCount
     */
    protected abstract int getOctaveCount();
    
    /**
     * Gets the persistence.  Higher persistence causes the terrain to be more
     * "rough."  Values should be between 0 and 1.0 for best results.
     * @return persistence
     */
    protected abstract double getPersistence();
    
    /**
     * Sets the surrounding regions.  If no region is at this location, use
     * null.
     * @param topRegion region directly above this region
     * @param rightRegion region directly to the right of this region
     * @param bottomRegion region directly below this region
     * @param leftRegion region to the left of this region
     */
    public void setRegions(Region topRegion, Region rightRegion, 
            Region bottomRegion, Region leftRegion) {
        this.topRegion    = topRegion;
        this.rightRegion  = rightRegion;
        this.bottomRegion = bottomRegion;
        this.leftRegion   = leftRegion;
    }
    
    /**
     * Returns a perlin value (-1 to 1) for a specified coordinate.  It is
     * possible to obtain values outside of this range.
     * 
     * Before the perlin value is retrieved, an average is performed between
     * the top, right, bottom, and left regions to this region.
     * A future update might want to also include the top-right, bottom-right,
     * bottom-left and top-left regions in this calculation.
     * 
     * This, of course, is only a problem if our height contains multiple
     * regions.  Otherwise the problem is reduced to left/right.
     * 
     * @param perlin The perlin object that's associated with this world
     * @param x The world-based x coordinate, where 0 is the far left side of
     * the world.
     * @param y The world-based y coordinate, where 0 is the top of the world
     * @return value between -1 and 1 (usually).
     */
    public double getValue(Perlin perlin, int x, int y) {
        double rw = WorldConstants.WORLD_REGION_WIDTH;
        double rh = WorldConstants.WORLD_REGION_HEIGHT;
        
        if (rw == 0 || rh == 0) {
            return 0.0;
        }
        
        // Get the frequency, lacunarity, persistence, and octaveCount averages
        double freq     = 0.0;
        double lac      = 0.0;
        double persist  = 0.0;
        double octCount = 0.0;

        freq     += getFrequency() ;//* wc;
        lac      += getLacunarity() ;//* wc;
        persist  += getPersistence() ;//* wc;
        octCount += getOctaveCount() ;//* wc;
        
        // We now have weights for everything, so let's set the frequency and such
        perlin.setFrequency(freq);
        perlin.setLacunarity(lac);
        try {
            perlin.setOctaveCount((int) (octCount));
        } catch (ExceptionInvalidParam e) {
            try { // Well, badness happened, so let's try the default
                perlin.setOctaveCount(getOctaveCount());
            } catch (ExceptionInvalidParam e1) {
                // Uh oh!
            }
        }
        perlin.setPersistence(persist);
        
        // We can now query the value for this coordinate
        return perlin.getValue(WorldConstants.WORLD_STEP*x, 
                WorldConstants.WORLD_STEP*y);
    }
    
    /**
     * Returns a block type's value based on the <code>value</code> provided.
     * @param value The "weight" value for this block.  Lower values imply
     * "dense" regions, and high values imply "airy" regions
     * @return blockType value
     */
    public abstract byte getBlock(double value);

    /**
     * Retrieves the x coordinate of this region.
     * @return x
     */
    public int getX() {
    	return x;
    }
    
    /**
     * Retrieves the y coordinate of this region.
     * @return y
     */
    public int getY() {
    	return y;
    }
    
    /**
     * Retrieves the spawn chance for this region.  Spawn chance should be
     * between 0 and 1 inclusive.  A higher spawn chance results in a higher
     * probability of monsters spawning.
     * 
     * Unless the SPAWN_RATE is changed, this is the probability that a
     * monster will spawn every 100 milliseconds.  In other words, spawn
     * chance * SPAWN_RATE determines the average rate of monster spawns.
     * @return spawnChance
     */
    public float getSpawnChance() {
        return 0.02f;
    }

    /**
     * Retrieves a random monster from this region given the region difficulty.
     * TODO:  Finish implementation.
     * @param monsterDifficulty
     * @return monsterData or null if no monsters in list.
     */
    public ServerMonsterData getMonster(int monsterDifficulty) {
        Collection<ServerMonsterData> monsters = ServerMonsterManager.getInstance().getAllServerMonsterData();

        if (monsters.size() > 0) {
            int rand = random.nextInt(monsters.size());
            int i = 0;
            for (ServerMonsterData monster : monsters) {
                if (i++ == rand) {
                    return monster;
                }
            }
        }
        
        return null;
    }
}
