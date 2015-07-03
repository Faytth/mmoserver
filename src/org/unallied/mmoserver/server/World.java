package org.unallied.mmoserver.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import libnoiseforjava.exception.ExceptionInvalidParam;
import libnoiseforjava.module.Perlin;

import org.unallied.mmocraft.BlockType;
import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.Location;
import org.unallied.mmocraft.RawPoint;
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.sessions.TerrainSession;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.monsters.ServerMonsterData;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.regions.DesertRegion;
import org.unallied.mmoserver.server.regions.HillsRegion;
import org.unallied.mmoserver.server.regions.PlainsRegion;
import org.unallied.mmoserver.server.regions.Region;
import org.unallied.mmoserver.server.regions.ShatteredRegion;

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

    /** Chunk objects to be used in synchronization (e.g. when changing a block). */
    private Object[][] chunks;
    
    /**
     *  We will use regions to modify Perlin noise variables for world generation.
     */
    private Region[][] regions = new Region[(int)WorldConstants.WORLD_REGIONS_WIDE][(int)WorldConstants.WORLD_REGIONS_TALL];
    
    private BlockDamage blockDamage = new BlockDamage();
    
    /**
     * A map of all players by chunk id.  When a player moves from one chunk to another,
     * they need to be removed from their old chunk and placed in their new chunk.
     * TODO: Combine this with the PlayerPool somehow.
     */
    private HashMap<Long, HashMap<Integer, ServerPlayer>> players = new HashMap<Long, HashMap<Integer, ServerPlayer>>();
    
    /**
     * A map of all monsters by chunk id.  When a monster moves from one chunk to another,
     * they need to be removed from their old chunk and placed in their new chunk.
     * TODO: Combine this with the MonsterPool somehow.
     */
    private HashMap<Long, HashMap<Integer, ServerMonster>> monsters = new HashMap<Long, HashMap<Integer, ServerMonster>>();
    
    private World() {
        chunks = new Object[(int)WorldConstants.WORLD_CHUNKS_WIDE][(int)WorldConstants.WORLD_CHUNKS_TALL];
        for (int i=0; i < chunks.length; ++i) {
            for (int j=0; j < chunks[i].length; ++j) {
                chunks[i][j] = new Object();
            }
        }
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
     * Returns an object representing the chunk.  This can be used in
     * synchronization.  The object does not contain any information about the
     * chunk.  If you need a chunk's information, use {@link World#getChunk(long)}.
     * @param location The location containing this chunk.
     * @return chunkObject
     */
    public Object getChunkObject(Location location) {
        return getChunkObject(getChunkId(location));
    }
    
    /**
     * Returns an object representing the chunk.  This can be used in
     * synchronization.  The object does not contain any information about the
     * chunk.  If you need a chunk's information, use {@link World#getChunk(long)}.
     * @param chunkId The id of the chunk object we're retrieving.
     * @return chunkObject
     */
    public Object getChunkObject(long chunkId) {
        
        int x = (int) ((chunkId << 32) >> 32);
        int y = (int) (chunkId >> 32);
        
        return getChunkObject(x, y);
    }
    
    /**
     * Returns an object representing the chunk.  This can be used in
     * synchronization.  The object does not contain any information about the
     * chunk.  If you need a chunk's information, use {@link World#getChunk(int, int)}.
     * @param x The chunk's x coordinate.  The units for this coordinate
     * are the number of chunks from the left.
     * @param y The chunk's y coordinate.  The units for this coordinate
     * are the number of chunks from the top.
     * @return chunkObject
     */
    public Object getChunkObject(int x, int y) {
        return chunks[x][y];
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
        } catch (NullPointerException e) {
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
    public Block getBlock(int x, int y) {
        x = x >= 0 ? x % WorldConstants.WORLD_WIDTH : WorldConstants.WORLD_WIDTH + x;
        y = y >= 0 ? y : 0;
        y = y >= WorldConstants.WORLD_HEIGHT ? WorldConstants.WORLD_HEIGHT - 1 : y;
        try {
            return BlockType.fromValue(blocks[x][y]).getBlock();
        } catch (NullPointerException e) {
            return null;
        }
    }
    
    /**
     * Sets a block at the given location.  This method does NOT check to see
     * whether or not the block at the given location should be replaceable.
     * It simply replaces it and deletes and "block damage" at the given location.
     * 
     * @param location The location of the block to place
     * @param type The type of block that we're changing to.
     */
    public void setBlock(BoundLocation location, BlockType type) {
        if (location == null || type == null) { // Guard
            return;
        }
        int x = location.getX();
        int y = location.getY();
        blockDamage.clearDamage(new RawPoint(x, y));
        if (blocks[x][y] != type.getValue()) {
            blocks[x][y] = type.getValue();
            
            // Tell all nearby players that the block has changed.
            Server.getInstance().localBroadcast(location,
                    PacketCreator.getBlockChanged(x, y, type));
        }
    }
    
    /**
     * Deals damage to the block, sending packets to the client if necessary as
     * well as providing experience and loot.
     * @param playerId The player who damaged the block.  Used for keeping track of
     *                 who receives the block as an item and gets exp.
     * @param x The x location of the block.  Each block counts as 1 unit.
     * @param y The y location of the block.  Each block counts as 1 unit.
     * @param damage The amount of damage dealt to the block.
     * @return True if the block has broken, else false.
     */
    public void doDamage(int playerId, int x, int y, long damage) {
        x = x >= 0 ? x % WorldConstants.WORLD_WIDTH 
                : WorldConstants.WORLD_WIDTH + x;
        y = y >= 0 ? y: 0;
        y = y >= WorldConstants.WORLD_HEIGHT ? WorldConstants.WORLD_HEIGHT - 1 : y;
    	RawPoint point = new RawPoint(x, y);
    	
    	if (blockDamage.doDamage(point, playerId, damage, getBlock(x, y))) {
    	    // Block damage says the block has broken, so break it.
    	    blocks[(int)x][(int)y] = BlockType.AIR.getValue();
            // Tell all nearby players that the block has broken
            Server.getInstance().localBroadcast(new BoundLocation(x, y), 
                    PacketCreator.getBlockChanged(x, y, getBlock(x, y).getType()));
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
        int x = location.getX();
        int y = location.getY();
        
        int maxX = WorldConstants.WORLD_CHUNKS_WIDE;
        int maxY = WorldConstants.WORLD_CHUNKS_TALL;
        
        x /= WorldConstants.WORLD_CHUNK_WIDTH;
        y /= WorldConstants.WORLD_CHUNK_HEIGHT;
        
        readLock.lock();
        try {
            for (int i = 0; i < length; ++i) { // rows
                for (int j = 0; j < length; ++j) { // columns
                    // (y << 32) | x
                    int chunkX = (x + i - radius) % maxX;
                    int chunkY = y + j - radius;
                    
                    // Make sure we don't get negative coordinates
                    chunkX = chunkX < 0 ? maxX + chunkX : chunkX;
                    chunkY = chunkY < 0 ? 0 : 
                                     chunkY > maxY ? maxY :
                                             chunkY;
                    
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
     * Retrieves all monsters near this location.  Distance to retrieve
     * monsters is based on <code>WorldConstants.WORLD_DRAW_DISTANCE</code>.
     * @param location The location to get the surrounding monsters from
     * @return monsters near <code>location</code>
     */
    public List<ServerMonster> getNearbyMonsters(BoundLocation location) {
        List<ServerMonster> result = new ArrayList<ServerMonster>();
        
        int radius = WorldConstants.WORLD_DRAW_DISTANCE;
        int length = radius * 2 + 1;
        
        // Get all chunks around this location
        // NOTE: This breaks down if the world is abnormally small
        int x = location.getX();
        int y = location.getY();
        
        int maxX = WorldConstants.WORLD_CHUNKS_WIDE;
        int maxY = WorldConstants.WORLD_CHUNKS_TALL;
        
        x /= WorldConstants.WORLD_CHUNK_WIDTH;
        y /= WorldConstants.WORLD_CHUNK_HEIGHT;
        
        readLock.lock();
        try {
            for (int i=0; i < length; ++i) { // rows
                for (int j=0; j < length; ++j) { // columns
                    // (y << 32) | x
                    int chunkX = (x+i-radius) % maxX;
                    int chunkY = y+j-radius;
                    
                    // Make sure we don't get negative coordinates
                    chunkX = chunkX < 0 ? maxX+chunkX : chunkX;
                    chunkY = chunkY < 0 ? 0 : 
                                     chunkY > maxY ? maxY :
                                             chunkY;
                    
                    long chunkId = ((long) (chunkY) << 32) | chunkX;
                    
                    if (monsters.containsKey(chunkId)) {
                        result.addAll(monsters.get(chunkId).values());
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
                getChunkId(player.getClientLocation()) != getChunkId(location)) {
            writeLock.lock();
            try {
                // Remove player from old chunk
                long chunkId = getChunkId(player.getClientLocation());
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
                
                // Notify player of nearby Living objects (players / monsters).
                player.getClient().selectiveConvergecast();
            } catch (NullPointerException e) {
                e.printStackTrace();
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /**
     * Moves a monster to a new chunk if necessary.  Does not actually update the monster's
     * location.
     * @param monster The monster whose chunk needs updating
     * @param location The monster's new location
     */
    public void moveMonster(ServerMonster monster, Location location) {
        if (monster != null && location != null &&
                getChunkId(monster.getLocation()) != getChunkId(location)) {
            writeLock.lock();
            try {
                // Remove monster from old chunk
                long chunkId = getChunkId(monster.getLocation());
                monsters.get(chunkId).remove(monster.getId());
                if (monsters.get(chunkId).size() == 0) {
                    monsters.remove(chunkId);
                }
                
                // Add player to new chunk
                chunkId = getChunkId(location);
                if (!monsters.containsKey(chunkId)) {
                    monsters.put(chunkId, new HashMap<Integer,ServerMonster>());
                }
                monsters.get(chunkId).put(monster.getId(), monster);
            } catch (NullPointerException e) {
                System.out.println("monsters.get(chunkId) == " + 
                        monsters.get(getChunkId(monster.getLocation())) == null ? "null" : "not null");
                e.printStackTrace();
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
            writeLock.lock();
            try {
                long chunkId = getChunkId(player.getClientLocation());
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
     * Add a monster to a chunk.  This should only be called one time per monster.
     * To move a monster from one chunk to another, call <code>moveMonster(Monster monster)</code>
     * @param monster
     */
    public void addMonster(ServerMonster monster) {
        if (monster != null) {
            writeLock.lock();
            try {
                long chunkId = getChunkId(monster.getLocation());
                if (!monsters.containsKey(chunkId)) {
                    monsters.put(chunkId, new HashMap<Integer,ServerMonster>());
                }
                monsters.get(chunkId).put(monster.getId(), monster);
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /**
     * Removes a player from their chunk.
     * @param player the player to remove
     */
    public void removePlayer(ServerPlayer player) {
        if (player != null) {
            writeLock.lock();
            try {
                long chunkId = getChunkId(player.getClientLocation());
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
     * Removes a monster from their chunk.
     * @param monster the monster to remove
     */
    public void removeMonster(ServerMonster monster) {
        if (monster != null) {
            writeLock.lock();
            try {
                long chunkId = getChunkId(monster.getLocation());
                if (monsters.containsKey(chunkId)) {
                    monsters.get(chunkId).remove(monster.getId());
                    
                    // Broadcast the deletion of this monster
                    Server.getInstance().localBroadcast(monster.getLocation(), 
                            PacketCreator.getMonsterDamaged(null, monster, 0, 0));
                    
                    // No more monsters in this chunk.  Removing chunk to save space.
                    if (monsters.get(chunkId).size() == 0) {
                        monsters.remove(chunkId);
                    }
                }
            } finally {
                writeLock.unlock();
            }
        }
    }
    
    /**
     * Get the location that is just before <code>end</code> based off of the
     * starting location.
     * TODO:  This function needs to be thought out better.  It's messing with 
     * {@link #collideWithBlock(Location, Location)}collideWithBlock.
     * @param start the location to start at
     * @param end the old end location
     * @param collision the new end location
     * @return the location right before the end location; returns null if any parameter is null
     */
    public Location getMaxLocation(Location start, Location end, BoundLocation collision) {
        // Guard
        if (start == null || end == null || collision == null) {
            return null;
        }
        
        // Special case for same block
        if (start.getX() == collision.getX() && start.getY() == collision.getY()) {
            return start;
        }
        
        boolean simpleMaxLocation = false; // true if this max location is a change in only one dimension (x or y)
        
        // Fix offsets for collision.
        if (start.getX() == end.getX() && start.getXOffset() == end.getXOffset()) {
            collision.setRawX(start.getRawX());
            simpleMaxLocation = true;
        }
        if (start.getY() == end.getY() && start.getYOffset() == end.getYOffset()) {
            collision.setRawY(start.getRawY());
            simpleMaxLocation = true;
        }
        
        if (simpleMaxLocation) {
            // Fix the x location
            if (collision.getBlockDeltaX(start) > 0) {
                collision.decrementX();
            } else if (collision.getBlockDeltaX(start) < 0){
                collision.moveRawRight(Location.BLOCK_GRANULARITY);
            }
            
            // Fix the y location
            if (collision.getBlockDeltaY(start) > 0) {
                collision.decrementY();
            } else if (collision.getBlockDeltaY(start) < 0) {
                collision.moveRawDown(Location.BLOCK_GRANULARITY);
            }
        } else {
            /*
             *  Special case:  Because Bresenham's line algorithm is using whole
             *  block coordinates (and not subpixel coordinates), we need to use
             *  a do-while loop to ensure that collision is not part of a block
             *  that has collision.
             */
            Block b;
            BoundLocation prevCollision;
            do {
                prevCollision = collision;
                collision = new BoundLocation(TerrainSession.getComplexMaxLocation(start, end, collision));
                b = getBlock(collision.getX(), collision.getY());
            } while (b != null && b.isCollidable() && !collision.equals(prevCollision));
        }
        
        return collision;
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
        int x0 = end.getX();
        int y0 = end.getY();
        int x1;
        int y1;

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
        int tmp;
        if (steep) {
            tmp = x0;
            x0 = y0;
            y0 = tmp;
            
            tmp = x1;
            x1 = y1;
            y1 = tmp;
        }
        int deltax = Math.abs(x1 - x0);
        int deltay = Math.abs(y1 - y0);
        int error  = deltax / 2;
        int ystep;
        int y = y0;
        if (y0 < y1) {
            ystep = 1;
        } else {
            ystep = -1;
        }
        
        int inc;
        if (x0 < x1) {
            inc = 1;
        } else {
            inc = -1;
        }
        // We want to ignore starting position.  Return if we get a solid block
        // TODO:  Should we allow collisions on our current block?
        boolean isLast = false;
        for (int x=x0; x != x1 || isLast; x+=inc) {
            if (steep) {
                Block b = getBlock(y, x);
                if (b == null || b.isCollidable()) {
                    return getMaxLocation(end, end2, new BoundLocation(y,x));
                }
            } else {
                Block b = getBlock(x, y);
                if (b == null || b.isCollidable()) {
                    return getMaxLocation(end, end2, new BoundLocation(x,y));
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

    /**
     * Updates everything in the world, such as block HP.
     * @param delta The amount of time that has passed in milliseconds.
     */
    public void update(long delta) {
        blockDamage.update(delta);
    }

    /**
     * Retrieves the spawn chance (from 0 to 1 inclusive) of a particular
     * region.  Returns 0 if the location didn't exist.  The higher the spawn
     * chance, the greater the chance a mob will spawn.
     * @param location The location in the region to retrieve the spawn chance for.
     * @return spawnChance
     */
    public float getSpawnChance(BoundLocation location) {
        if (location == null) {
            return 0;
        }
        
        int rx = location.getX() / WorldConstants.WORLD_CHUNK_WIDTH / WorldConstants.WORLD_REGION_WIDTH;
        int ry = location.getY() / WorldConstants.WORLD_CHUNK_HEIGHT / WorldConstants.WORLD_REGION_HEIGHT;
        if (rx >= 0 && ry >= 0 && regions.length > rx && regions[rx].length > ry) {
            return regions[rx][ry].getSpawnChance();
        }
        
        return 0; // Impossible location
    }
    
    /**
     * Retrieves the average monster level associated with this location.  Monster
     * levels are decided at the region level.  Returns 0 if the location is impossible.<br />
     * Difficulty ranges from 0 to 120.  Difficulty increases as you approach the center x
     * of the world (up to +100), and as you go downwards (up to +20).
     * @param location The location at which to retrieve the monster level.
     * @return monsterDifficulty
     */
    public int getMonsterDifficulty(BoundLocation location) {
        if (location == null) {
            return 0;
        }
        
        int difficultyX = (WorldConstants.WORLD_WIDTH / 2) - location.getX();
        difficultyX = difficultyX < 0 ? -difficultyX : difficultyX;
        difficultyX = (int) (100.0 * ((WorldConstants.WORLD_WIDTH / 2.0 - difficultyX) / (WorldConstants.WORLD_WIDTH / 2.0)));

        int difficultyY = 0;
        if (location.getY() / WorldConstants.WORLD_CHUNK_HEIGHT > 20) {
            difficultyY = (int) (location.getY() / WorldConstants.WORLD_CHUNK_HEIGHT) - 20;
            difficultyY = difficultyY < 0 ? 0 : difficultyY;
            difficultyY = (int)(20.0 * (difficultyY / (WorldConstants.WORLD_CHUNKS_TALL - 20.0)));
        }
        
        return difficultyX + difficultyY;
    }

    /**
     * Retrieves a random monster from this location.  The monster is based on
     * the region's location (monster difficulty), and should also be based on
     * the region type (e.g. desert).<br />
     * Returns null if the region is not found or if there are no monsters to retrieve.
     * @param location The location to get a monster from.
     * @return monsterData
     */
    public ServerMonsterData getMonster(BoundLocation location) {
        int monsterDifficulty = getMonsterDifficulty(location);
        
        int rx = location.getX() / WorldConstants.WORLD_CHUNK_WIDTH / WorldConstants.WORLD_REGION_WIDTH;
        int ry = location.getY() / WorldConstants.WORLD_CHUNK_HEIGHT / WorldConstants.WORLD_REGION_HEIGHT;
        if (rx >= 0 && ry >= 0 && regions.length > rx && regions[rx].length > ry) {
            return regions[rx][ry].getMonster(monsterDifficulty);
        }
        
        return null;
    }
}
