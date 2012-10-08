package org.unallied.mmoserver.server.regions;

import org.unallied.mmocraft.BlockType;
import org.unallied.mmocraft.constants.WorldConstants;

public class ShatteredRegion extends Region {
    private static final double frequency   = 0.00050;
    private static final double lacunarity  = 0.30;
    private static final double persistence = 0.95;
    private static final int octaveCount    = 6;

    /**
     * 
     * @param x The world-based x coordinate of this region (in blocks)
     * @param y The world-based y coordinate of this region (in blocks)
     */
    public ShatteredRegion(int x, int y) {
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
        if (value < WorldConstants.WORLD_WEIGHT - 80.50) {
            return BlockType.AIR.getValue();
        } else if (value < WorldConstants.WORLD_WEIGHT - 79.8) {
            return BlockType.DIRT.getValue();
        } else {
            return BlockType.STONE.getValue();
        }
    }

}
