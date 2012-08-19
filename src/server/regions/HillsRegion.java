package server.regions;

import org.unallied.mmocraft.BlockType;
import org.unallied.mmocraft.constants.WorldConstants;

/**
 * A very flat region.  See {@link http://en.wikipedia.org/wiki/Plain} for more
 * information.
 * @author Faythless
 *
 */
public class HillsRegion extends Region {
    private static final double frequency   = 0.0001;
    private static final double lacunarity  = 2.0;
    private static final double persistence = 0.01;
    private static final int octaveCount    = 2;

    /**
     * 
     * @param x The world-based x coordinate of this region (in blocks)
     * @param y The world-based y coordinate of this region (in blocks)
     */
    public HillsRegion(int x, int y) {
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
