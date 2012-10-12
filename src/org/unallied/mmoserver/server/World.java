package org.unallied.mmoserver.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.unallied.mmocraft.BlockType;
import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.Location;
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.net.sessions.TerrainSession;
import org.unallied.mmoserver.server.regions.*;


import libnoiseforjava.exception.ExceptionInvalidParam;
import libnoiseforjava.module.Perlin;

/**
 * Contains methods needed to access any chunk in the world.
 * 
 * @author Faythless
 *
 */
public class World {

    private final ReentrantReadWriteLock locks = new ReentrantReadWriteLock();
    private final Lock readLock = locks.readLock();
    private final Lock writeLock = locks.writeLock();
    
    private static final double HEAT_FREQUENCY = 1.80;
    private static final double HEAT_LACUNARITY = 0.30;
    private static final double HEAT_PERSISTENCE = 0.95;
    private static final int HEAT_OCTAVE_COUNT = 6;
    
    private static final double HUMIDITY_FREQUENCY = 1.70;
    private static final double HUMIDITY_LACUNARITY = 0.30;
    private static final double HUMIDITY_PERSISTENCE = 0.95;
    private static final int HUMIDITY_OCTAVE_COUNT = 6;
    
    /**
     *  All of the blocks in the world.
     */
    private byte[][] blocks = new byte[(int)WorldConstants.WORLD_WIDTH][(int)WorldConstants.WORLD_HEIGHT];
    
    /**
     *  We will use regions to modify Perlin noise variables for world generation
     */
    private Region[][] regions = new Region[(int)WorldConstants.WORLD_REGIONS_WIDE][(int)WorldConstants.WORLD_REGIONS_TALL];
    
    /**
     * A map of all players by chunk id.  When a player moves from one chunk to another,
     * they need to be removed from their old chunk and placed in their new chunk.
     * TODO: Combine this with the PlayerPool somehow
     */
    private HashMap<Long, HashMap<Integer, ServerPlayer>> players = new HashMap<Long, HashMap<Integer, ServerPlayer>>();
        
    private World() {
    }
    
    private static class WorldHolder {
        private static final World instance = new World();
    }
    
    /**
     * Clears the block at (<code>x</code>,<code>y</code>), replacing it with
     * air.
     * @param x The x block coordinate of the world
     * @param y The y block coordinate of the world
     */
    private void clearBlock(int x, int y) {
        if (x >= 0 && y >= 0 && x < WorldConstants.WORLD_WIDTH && y < WorldConstants.WORLD_HEIGHT) {
            blocks[x][y] = BlockType.AIR.getValue();
        }
    }
    
    /**
     * Creates a circle of air around this location with the given radius.
     * @param x0 The center x block coordinate of the world
     * @param y0 The center y block coordinate of the world
     * @param radius The circle radius in blocks to draw around (x,y)
     */
    private void rasterCircle(int x0, int y0, int radius) {
        int f = 1 - radius;
        int ddF_x = 1;
        int ddF_y = -2 * radius;
        int x = 0;
        int y = radius;
        
        clearBlock(x0, y0);
        for (int i=1; i <= radius; ++i) {
            clearBlock(x0, y0 + i);
            clearBlock(x0, y0 - i);
            clearBlock(x0 + i, y0);
            clearBlock(x0 - i, y0);
        }
        
        while (x < y) {
            if (f >= 0) {
                --y;
                ddF_y += 2;
                f += ddF_y;
            }
            ++x;
            ddF_x += 2;
            f += ddF_x;
            for (int i=0; i <= x; ++i) {
                for (int j=0; j <= y; ++j) {
                    clearBlock(x0 + i, y0 + j);
                    clearBlock(x0 - i, y0 + j);
                    clearBlock(x0 + i, y0 - j);
                    clearBlock(x0 - i, y0 - j);
                    clearBlock(x0 + j, y0 + i);
                    clearBlock(x0 - j, y0 + i);
                    clearBlock(x0 + j, y0 - i);
                    clearBlock(x0 - j, y0 - i);
                }
            }
        }
    }
        
    /**
     * This should be called after the world is generated with Perlin noise.
     * This function makes tunnels throughout the entire world, leading to a
     * more interesting and diverse game world.
     * @param wormCount  Number of worms to spawn uniformly across the world
     * @param wormRadius  Average radius of the worms
     * @param wormRadiusChangeRate Rate at which the worms change their radius.
     * Higher values cause it to change more frequently.  Should be between
     * 1 and 99.
     * @param wormDirectionChangeRate  Average rate at which the worm changes
     * its direction.  Higher values cause it to change more frequently.
     * Should be between 1 and 99.
     * @param wormLength  Average length of the worm.  Higher values cause the
     * worm to be longer.  Should be between 1 and 99999.
     */
    private void worms(long wormCount, int wormRadius, int wormRadiusChangeRate, int wormDirectionChangeRate, long wormLength) {
        Random random = new Random();
        
        // Create WORM_COUNT worms
        for (long i=0; i < wormCount; ++i) {
            // Each worm should start at a uniformly distributed random location (x,y)
            long x = (long) (WorldConstants.WORLD_WIDTH * random.nextDouble());
            long y = (long) (WorldConstants.WORLD_HEIGHT * random.nextDouble());
            int directionX = random.nextInt(3) - 1;
            int directionY = random.nextInt(3) - 1;
            int radius = (int) (random.nextGaussian()*2+wormRadius);
            radius = radius < 1 ? 1 : radius;
            
            // While this worm needs to keep going
            do {
                
                // Perform midpoint circle algorithm to remove surroundings
                rasterCircle((int)x, (int)y, radius);
                
                // See if radius changed
                if (random.nextInt(100) < wormRadiusChangeRate) {
                    // Get new radius
                    radius += (int) (random.nextGaussian()*2+wormRadius) > radius ? 1 : -1;
                    radius = radius < 1 ? 1 : radius;
                }
                
                x += directionX;
                y += directionY;
                
                // See if direction changed
                if (random.nextInt(100) < wormDirectionChangeRate) {
                    // Get new direction
                    directionX = random.nextInt(3) - 1;
                    directionY = random.nextInt(3) - 1;
                }
            }
            while (random.nextInt(100000) < wormLength);
        }
    }
    
    /**
     * Creates a region at (x,y) where each unit is a region.
     * Only the x coordinate is used to determine heat and rainfall.
     * This is because the world is 2D, so the heatmap and rainfall map
     * are just a horizontal line.
     * @param heat A Perlin noise generator for how hot a region is.
     * @param humidity A Perlin noise generator for the amount of rainfall
     * received by a region.
     * @param x The x coordinate of the region.  Starting at 0, each unit is
     * 1 region.
     * @param y The y coordinate of the region.  Starting at 0, each unit is
     * 1 region.
     * @return region at position (x, y).  Will return a region even if it is
     * outside of the world's region boundaries.
     */
    public Region generateRegion(Perlin heat, Perlin humidity, int x, int y) {
        
        // Get heat and rainfall in %'s from roughly 0 ~ 100%
        double iheat    = (heat.getValue(x, 0.5)+1) * 50;
        double rainfall = (humidity.getValue(x, 0.5)+1) * 50;
        
        //http://www.minecraftwiki.net/wiki/File:BiomesGraph.png <-- use this to help
        System.out.print("Heat: " + iheat + "  | Rain: " + rainfall + " | ");

        if (rainfall < 25) {
            if (iheat < 25) {
                return new PlainsRegion(x, y);
            } else {
                return new DesertRegion(x, y);
            }
        } else if (rainfall < 50) {
            return new HillsRegion(x, y);
        } else if (rainfall < 75) {
            return new HillsRegion(x, y);
        } else {
            return new ShatteredRegion(x, y);
        }
    }
    
    /**
     * Generate the entire world.  This should only be called one time,
     * and only when initializing the world for the first time.
     */
    public void generateWorld() {
        Random random = new Random();
        Perlin heat     = new Perlin(random.nextInt());
        heat.setFrequency(HEAT_FREQUENCY);
        heat.setLacunarity(HEAT_LACUNARITY);
        heat.setPersistence(HEAT_PERSISTENCE);
        try {
            heat.setOctaveCount(HEAT_OCTAVE_COUNT);
        } catch (ExceptionInvalidParam e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        Perlin humidity = new Perlin(random.nextInt());
        humidity.setFrequency(HUMIDITY_FREQUENCY);
        humidity.setLacunarity(HUMIDITY_LACUNARITY);
        humidity.setPersistence(HUMIDITY_PERSISTENCE);
        try {
            humidity.setOctaveCount(HUMIDITY_OCTAVE_COUNT);
        } catch (ExceptionInvalidParam e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        Perlin perlin   = new Perlin(random.nextInt());
        
        // Create the regions
        for (int i=0; i < WorldConstants.WORLD_REGIONS_WIDE; ++i) {
            for (int j=0; j < WorldConstants.WORLD_REGIONS_TALL; ++j) {
                regions[i][j] = generateRegion(heat, humidity, i, j);
                System.out.print(regions[i][j].toString().split("@")[0] + "\n");
            }
        }
        
        // Set surrounding region information
        for (int i=0; i < WorldConstants.WORLD_REGIONS_WIDE; ++i) {
            for (int j=0; j < WorldConstants.WORLD_REGIONS_TALL; ++j) {
                int top   = (j-1) % (int)WorldConstants.WORLD_REGIONS_TALL;
                int bot   = (j+1) % (int)WorldConstants.WORLD_REGIONS_TALL;
                int left  = (i-1) % (int)WorldConstants.WORLD_REGIONS_WIDE;
                int right = (i+1) % (int)WorldConstants.WORLD_REGIONS_WIDE;
                top   = top   < 0 ? (int)WorldConstants.WORLD_REGIONS_TALL+top   : top;
                bot   = bot   < 0 ? (int)WorldConstants.WORLD_REGIONS_TALL+bot   : bot;
                left  = left  < 0 ? (int)WorldConstants.WORLD_REGIONS_WIDE+left  : left;
                right = right < 0 ? (int)WorldConstants.WORLD_REGIONS_WIDE+right : right;
                regions[i][j].setRegions(regions[i][top], regions[right][j],
                        regions[i][bot], regions[left][j]);
            }
        }
        
        // Create the blocks
        for (int i=0; i < WorldConstants.WORLD_WIDTH; ++i) {
            for (int j=0; j < WorldConstants.WORLD_HEIGHT; ++j) {
                Region region = regions[i/(int)WorldConstants.WORLD_REGION_WIDTH][j/(int)WorldConstants.WORLD_REGION_HEIGHT];
                double val = region.getValue(perlin, i, j);
                // Universal weighting based on height
                val += WorldConstants.WORLD_WEIGHT * j / WorldConstants.WORLD_HEIGHT;
                blocks[i][j] = region.getBlock(val);
            }
            if ((i+1) % 1000 == 0) {
                System.out.println(i+1 + " / " + WorldConstants.WORLD_WIDTH + " block columns loaded.");
            }
        }
        System.out.println("Finished loading all block columns.");
        
        // make the land more interesting by carving worm-like tunnels
        
        // make "dungeons" (really long tunnels)
        worms(WorldConstants.WORM_COUNT, 3, WorldConstants.WORM_RADIUS_CHANGE_RATE,
                WorldConstants.WORM_DIRECTION_CHANGE_RATE, WorldConstants.WORM_LENGTH);
        // Punch out little holes in the world
        worms(WorldConstants.WORM_COUNT*1667, 2, 90,
                WorldConstants.WORM_DIRECTION_CHANGE_RATE*2, 93000);
    }
    
    /**
     * Returns the blocks in a byte[] that make up a given chunk.
     * chunkId is defined as (y << 32) | x
     * @return blocks of a chunk
     */
    public byte[] getChunk(long chunkId) {
        
        int x = (int) ((chunkId << 32) >> 32);
        int y = (int) (chunkId >> 32);
        
        return getChunk(x, y);
    }
    
    /**
     * Returns the blocks in a byte[] that make up a given chunk.
     * @param x The chunk's x coordinate.  The units for this coordinate
     * are the number of chunks from the left.
     * @param y The chunk's y coordinate.  The units for this coordinate
     * are the number of chunks from the top.
     * @return blocks of a chunk
     */
    public byte[] getChunk(int x, int y) {
        
        byte[] result = new byte[WorldConstants.WORLD_CHUNK_WIDTH
                                 * WorldConstants.WORLD_CHUNK_HEIGHT];
        
        // Convert to block coordinates
        x *= WorldConstants.WORLD_CHUNK_WIDTH;
        y *= WorldConstants.WORLD_CHUNK_HEIGHT;
        
        try {
            // Write the chunk to our result array
            for( int i=x; i < x + WorldConstants.WORLD_CHUNK_WIDTH; ++i) {
                for (int j=y; j < y + WorldConstants.WORLD_CHUNK_HEIGHT; ++j) {
                    result[(i-x) * WorldConstants.WORLD_CHUNK_HEIGHT + (j-y)] =
                            blocks[i][j];
                }
            }
            
            return result;
        } catch (Throwable t) {
            // This shouldn't happen, because blocks should be assigned, but just incase
            return null;
        }
    }
    
    /**
     * Returns the chunkId, which is defined as (y << 32) | x of the location provided.
     * @param location The location to get the chunkId of
     * @return chunkId that the location provided is a part of
     */
    public long getChunkId(Location location) {
        long x = location.getX();
        long y = location.getY();
                
        x /= WorldConstants.WORLD_CHUNK_WIDTH;
        y /= WorldConstants.WORLD_CHUNK_HEIGHT;
        
        return ((long) (y) << 32) | x;
    }
    
    public static World getInstance() {
        return WorldHolder.instance;
    }
    
    /**
     * Returns the block at location (x,y) where each value is a block.
     * @param location the location containing (x,y) of the block
     * @return the block at location (x,y).  Returns null if chunk is missing
     */
    public Block getBlock(Location location) {
        return getBlock(location.getX(), location.getY());
    }
    
    /**
     * Retrieves a block given its block coordinates.
     * Returns null if the coordinates were out of bounds.
     * 
     * @param x The x coordinate of the block
     * @param y The y coordinate of the block
     * @return block, or null if there was an error.
     */
    public Block getBlock(long x, long y) {
        try {
            return BlockType.fromValue(blocks[(int) (x)][(int) (y)]).getBlock();
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Retrieves all players near this location.  Distance to retrieve
     * players is based on <code>WorldConstants.WORLD_DRAW_DISTANCE</code>.
     * @param location The location to get the surrounding players from
     * @return players near <code>location</code>
     */
    public List<ServerPlayer> getNearbyPlayers(BoundLocation location) {
        List<ServerPlayer> result = new ArrayList<ServerPlayer>();
        
        int radius = WorldConstants.WORLD_DRAW_DISTANCE;
        int length = radius * 2 + 1;
        
        // Get all chunks around this location
        // NOTE: This breaks down if the world is abnormally small
        long x = location.getX();
        long y = location.getY();
        
        long maxX = WorldConstants.WORLD_CHUNKS_WIDE;
        long maxY = WorldConstants.WORLD_CHUNKS_TALL;
        
        x /= WorldConstants.WORLD_CHUNK_WIDTH;
        y /= WorldConstants.WORLD_CHUNK_HEIGHT;
        
        readLock.lock();
        try {
            for (int i=0; i < length; ++i) { // rows
                for (int j=0; j < length; ++j) { // columns
                    // (y << 32) | x
                    int chunkX = (int) ((x+i-radius) % maxX);
                    int chunkY = (int) (y+j-radius);
                    
                    // Make sure we don't get negative coordinates
                    chunkX = (int) (chunkX < 0 ? maxX+chunkX : chunkX);
                    chunkY = (int) (chunkY < 0    ? 0 : 
                                    chunkY > maxY ? maxY :
                                                    chunkY);
                    
                    long chunkId = ((long) (chunkY) << 32) | chunkX;
                    
                    if (players.containsKey(chunkId)) {
                        result.addAll(players.get(chunkId).values());
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
        
        return result;
    }
    
    /**
     * Moves a player to a new chunk if necessary.  Does not actually update the player's
     * location.
     * @param player The player whose chunk needs updating
     * @param location The player's new location
     */
    public void movePlayer(ServerPlayer player, Location location) {
        if (player != null && location != null &&
                getChunkId(player.getLocation()) != getChunkId(location)) {
            writeLock.lock();
            try {
                // Remove player from old chunk
                long chunkId = getChunkId(player.getLocation());
                players.get(chunkId).remove(player.getId());
                if (players.get(chunkId).size() == 0) {
                    players.remove(chunkId);
                }
                
                // Add player to new chunk
                chunkId = getChunkId(location);
                if (!players.containsKey(chunkId)) {
                    players.put(chunkId, new HashMap<Integer,ServerPlayer>());
                }
                players.get(chunkId).put(player.getId(), player);
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /**
     * Add a player to a chunk.  This should only be called one time per player.
     * To move a player from one chunk to another, call <code>movePlayer(Player player)</code>
     * @param player
     */
    public void addPlayer(ServerPlayer player) {
        if (player != null) {
            long chunkId = getChunkId(player.getLocation());
            writeLock.lock();
            try {
                if (!players.containsKey(chunkId)) {
                    players.put(chunkId, new HashMap<Integer,ServerPlayer>());
                }
                players.get(chunkId).put(player.getId(), player);
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /**
     * Removes a player from their chunk
     * @param player the player to remove
     */
    public void removePlayer(ServerPlayer player) {
        if (player != null) {
            long chunkId = getChunkId(player.getLocation());
            writeLock.lock();
            try {
                if (players.containsKey(chunkId)) {
                    players.get(chunkId).remove(player.getId());
                    
                    // No more players in this chunk.  Removing chunk to save space.
                    if (players.get(chunkId).size() == 0) {
                        players.remove(chunkId);
                    }
                }
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /**
     * Returns true if the path between <code>start</code> and <code>end</code>
     * collides with a block.
     * Uses Bresenham's line algorithm.
     * @param end the starting location (before moving)
     * @param end2 the ending location (after moving)
     * @return modified location
     */
    public Location collideWithBlock(Location end, Location end2) {
        Location specialEnd = new Location(end2); // used for sub-pixel checks
        long x0 = end.getX();
        long y0 = end.getY();
        long x1;
        long y1;

        // Special check for sub-pixels
        // In order for "jitter" to occur, start and end must be the same block
        if (end.getX() == end2.getX() && end.getY() == end2.getY()) {
            
            if (end2.getXOffset() < 1.0f && end.getXOffset() > end2.getXOffset()) {
                specialEnd.moveLeft(1);
            } else if (end2.getXOffset() > (WorldConstants.WORLD_BLOCK_WIDTH-1) && end.getXOffset() < end2.getXOffset()) {
                specialEnd.moveRight(1);
            }
            if (end2.getYOffset() < 1.0f && end.getYOffset() > end2.getYOffset()) {
                specialEnd.moveUp(1);
            } else if (end2.getYOffset() > (WorldConstants.WORLD_BLOCK_HEIGHT-1) && end.getYOffset() < end2.getYOffset()) {
                specialEnd.moveDown(1);
            }
        }
        x1 = specialEnd.getX();
        y1 = specialEnd.getY();
        
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        long tmp;
        if (steep) {
            tmp = x0;
            x0 = y0;
            y0 = tmp;
            
            tmp = x1;
            x1 = y1;
            y1 = tmp;
        }
        long deltax = Math.abs(x1 - x0);
        long deltay = Math.abs(y1 - y0);
        long error  = deltax / 2;
        long ystep;
        long y = y0;
        if (y0 < y1) {
            ystep = 1;
        } else {
            ystep = -1;
        }
        
        long inc;
        if (x0 < x1) {
            inc = 1;
        } else {
            inc = -1;
        }
        // We want to ignore starting position.  Return if we get a solid block
        // TODO:  Should we allow collisions on our current block?
        boolean isLast = false;
        for (long x=x0; x != x1 || isLast; x+=inc) {
            if (steep) {
                Block b = getBlock(y,x);
                if (b == null || b.isCollidable()) {
                    return TerrainSession.getMaxLocation(end, end2, new BoundLocation(y,x));
                }
            } else {
                Block b = getBlock(x,y);
                if (b == null || b.isCollidable()) {
                    return TerrainSession.getMaxLocation(end, end2, new BoundLocation(x,y));
                }
            }
            error -= deltay;
            if (error < 0) {
                y += ystep;
                error += deltax;
            }
            if (isLast) {
                break;
            }
            isLast = x+inc == x1;
        }
                
        return end2; // no collision
    }
}
