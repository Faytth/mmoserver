package org.unallied.mmoserver.server.regions;

import org.unallied.mmocraft.BlockType;
import org.unallied.mmocraft.constants.WorldConstants;

/**
 * A very flat region.  See {@link http://en.wikipedia.org/wiki/Plain} for more
 * information.
 * @author Faythless
 *
 */
public class PlainsRegion extends Region {
    /** 
     * Higher values cause more "random" terrain. Lower values 
     * cause very straight, unchanging terrain.
     */
    private static final double frequency   = 0.00005;
    
    /** The lacunarity is the frequency multiplier between successive octaves. */
    private static final double lacunarity  = 2.0;
    
    /** 
     * Higher persistence causes the terrain to be more "rough." 
     * Values should be between 0 and 1.0 for best results.
     */
    private static final double persistence = 0.003;
    
    /** 
     * Lower values make the land change slowly. Higher values make the land 
     * change quickly (higher resolution).
     */
    private static final int octaveCount = 2;

    /**
     * 
     * @param x The world-based x coordinate of this region (in blocks)
     * @param y The world-based y coordinate of this region (in blocks)
     */
    public PlainsRegion(int x, int y) {
        super(x, y);
    }

    @Override
    protected double getFrequency() {
        return frequency;
    }

    @Override
    protected double getLacunarity() {
        return lacunarity;
    }

    @Override
    protected int getOctaveCount() {
        return octaveCount;
    }

    @Override
    protected double getPersistence() {
        return persistence;
    }

    @Override
    public byte getBlock(double value) {
        if (value < WorldConstants.WORLD_WEIGHT - 80.38) {
            return BlockType.AIR.getValue();
        } else if (value < WorldConstants.WORLD_WEIGHT - 80.00) {
            return BlockType.DIRT.getValue();
        } else {
            return BlockType.STONE.getValue();
        }
    }

}
