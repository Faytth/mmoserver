package org.unallied.mmoserver.server;

import java.awt.Rectangle;
import java.util.List;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.CollisionBlob;
import org.unallied.mmocraft.Direction;
import org.unallied.mmocraft.Location;
import org.unallied.mmocraft.Player;
import org.unallied.mmocraft.RawPoint;
import org.unallied.mmocraft.Velocity;
import org.unallied.mmocraft.animations.AnimationState;
import org.unallied.mmocraft.animations.sword.SwordIdle;
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.geom.LongRectangle;
import org.unallied.mmocraft.items.Inventory;
import org.unallied.mmocraft.items.Item;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.skills.SkillType;
import org.unallied.mmocraft.skills.Skills;
import org.unallied.mmocraft.tools.input.GenericSeekableLittleEndianAccessor;
import org.unallied.mmocraft.tools.output.GenericLittleEndianWriter;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.constants.DatabaseConstants;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.net.PacketCreator;


/**
 * A server player is a wrapper for player that contains important server data, such
 * as the player's client.
 * 
 * TODO:  I'm not 100% certain, but I think there might be a race condition on
 * HP.  That needs to be fixed eventually.
 * @author Alexandria
 *
 */
public class ServerPlayer extends Player {
    /**
     * 
     */
    private static final long serialVersionUID = -8637946755132206345L;

    /** The last time that this player had their position updated. */
    private long lastUpdateTime = 0;
    
    private Client client = null;
    
    /** The last known location of this player on the client. */
    private BoundLocation clientLocation = null;
    
    @Override
    public void init() {
        super.init();
        try {
            recalculateStats();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    public Client getClient() {
        return client;
    }
    
    public void setClient(Client client) {
        this.client = client;
    }
    
    @Override
    /**
     * Does nothing, because this is the server.
     * @param packet The packet to send
     */
    protected void sendPacket(Packet packet) {
        // Don't send a packet because we're the server.
    }
    
    @Override
    /**
     * Updates the player, including animations.
     * @param delta time since last update.
     */
    public void update(long delta) {
        BoundLocation tempLocation = new BoundLocation(location);
        
        // Perform gravity checks
        accelerateDown((int)delta, ClientConstants.FALL_ACCELERATION * current.moveDownMultiplier(), 
                ClientConstants.FALL_TERMINAL_VELOCITY * current.moveDownMultiplier());
        
        Velocity newVelocity = new Velocity(velocity);
        newVelocity.setY(0);
        move((int)delta, newVelocity, 0, 0);
        newVelocity.setY(velocity.getY());
        newVelocity.setX(0);
        move((int)delta, newVelocity, fallSpeed, initialVelocity);
        
        current.update(delta);
        if (isStuck()) {
            location = tempLocation;
        }
        unstuck();
        lastUpdateTime = System.currentTimeMillis();
    }
    
    public void update() {
        synchronized (this) {
            if (lastUpdateTime == 0) {
                lastUpdateTime = System.currentTimeMillis();
            }
            long curTime = System.currentTimeMillis();
            update(curTime - lastUpdateTime);
            lastUpdateTime = curTime; // Override the last update time
        }
    }
    
    @Override
    public void setLocation(BoundLocation location) {
        this.location = location;
        lastUpdateTime = System.currentTimeMillis();
    }
    
    @Override
    public void setState(AnimationState current) {
        if (this.current != current && current != null) {
            this.current = current;
        }
    }
    
    @Override
    /**
     * Given a start and end, returns the farthest possible location after
     * collision is accounted for (such as running into a wall)
     * @param start Starting location
     * @param end Ending location
     * @param vf final downwards velocity
     * @return
     */
    public Location collide(Location start, Location end) {
        Location result = new BoundLocation(start);
        try {
            World world = World.getInstance();
            
            // If air
            Location horizontalCollide = new BoundLocation(world.collideWithBlock(start, end));
            result.setRawX(horizontalCollide.getRawX());
            
            // Vertical testing
            if (end.getRawY() != start.getRawY()) {
                Location verticalEnd = new BoundLocation(result);
                verticalEnd.setRawY(horizontalCollide.getRawY());
                               
                result = horizontalCollide;
            }
        } catch (NullPointerException e) {
            
        }
        return result;
    }
    
    @Override
    /**
     * Returns whether or not the player's hitbox is currently overlapping with
     * a collidable block.
     * @return stuck.  True if the player is stuck; else false.
     */
    public boolean isStuck() {
        for (RawPoint p : hitbox) {
            Location start = new Location(location);
            start.moveRawRight(p.getX());
            start.moveRawDown(p.getY());
            Block block = World.getInstance().getBlock(start);
            if (block != null && block.isCollidable()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Performs all checks needed when testing if a player hit any blocks.  Also
     * sends notifications when needed.
     * @param collisionArc
     * @param startingIndex
     * @param endingIndex
     * @param horizontalOffset
     * @param verticalOffset
     */
    public void performBlockCollisions(CollisionBlob[] collisionArc, 
            int startingIndex, int endingIndex, float horizontalOffset, float verticalOffset) {
        int curIndex = startingIndex - 1;
        do {
            curIndex = (curIndex + 1) % collisionArc.length;
            
            Location topLeft = new Location(this.location);
            if (direction == Direction.RIGHT) {
                topLeft.moveDown(verticalOffset + collisionArc[curIndex].getYOffset());
                topLeft.moveRight(horizontalOffset + collisionArc[curIndex].getXOffset());
            } else { // Flipped collision stuff.  This was such a pain to calculate.
                topLeft.moveDown(verticalOffset + collisionArc[curIndex].getYOffset());
                topLeft.moveRight(getWidth() - horizontalOffset - collisionArc[curIndex].getXOffset() - collisionArc[curIndex].getWidth());
            }
            Location bottomRight = new Location(topLeft);
            bottomRight.moveDown(collisionArc[curIndex].getHeight());
            bottomRight.moveRight(collisionArc[curIndex].getWidth());
            
            if (!topLeft.equals(bottomRight)) {
                /*
                 *  We now have the topLeft and bottomRight coords of our rectangle.
                 *  Using this, we need to grab every block in our rectangle for collision
                 *  testing.
                 */
                for (long x = topLeft.getX(); x <= bottomRight.getX(); ++x) {
                    for (long y = topLeft.getY(); y <= bottomRight.getY(); ++y) {
                        if (World.getInstance().getBlock(x, y).isCollidable()) {
                            int xOff = 0;
                            if (direction == Direction.RIGHT) {
                                xOff = (int) (((x - this.location.getX()) * WorldConstants.WORLD_BLOCK_WIDTH - horizontalOffset - collisionArc[curIndex].getXOffset() - this.location.getXOffset()));
                            } else {
                                xOff = (int) (-this.location.getXOffset() + current.getWidth() - ((this.location.getX() - x) * WorldConstants.WORLD_BLOCK_WIDTH + getWidth() - horizontalOffset + collisionArc[curIndex].getFlipped().getXOffset()));
                            }
                            int yOff = (int) (((y - this.location.getY()) * WorldConstants.WORLD_BLOCK_HEIGHT - verticalOffset - collisionArc[curIndex].getYOffset() - this.location.getYOffset()));
                            float damage =  (direction == Direction.RIGHT ? collisionArc[curIndex] : collisionArc[curIndex].getFlipped()).getDamage(
                                    new Rectangle(WorldConstants.WORLD_BLOCK_WIDTH, WorldConstants.WORLD_BLOCK_HEIGHT), xOff, yOff);
                            if (damage > 0) {
                                int multipliedDamage = (int)Math.round(getBlockDamageMultiplier() * damage);
                                
                                //if the block broke, tell everyone
                                World.getInstance().doDamage(getId(), x, y, multipliedDamage);
                            }
                        }
                    }
                }
            }
        } while (curIndex != endingIndex);
    }
    
    /**
     * Performs all checks needed to check whether a player has hit another player.
     * @param collisionArc
     * @param startingIndex
     * @param endingIndex
     * @param horizontalOffset
     * @param verticalOffset
     */
    public void performPlayerCollisions(CollisionBlob[] collisionArc, int startingIndex,
            int endingIndex, float horizontalOffset, float verticalOffset) {
        if (this.getPvPTime() == 0 || !this.isAlive()) { // The player doesn't have their PvP enabled.
            return;
        }
        int curIndex = startingIndex - 1;
        do {
            curIndex = (curIndex + 1) % collisionArc.length;
            
            Location topLeft = new Location(this.location);
            if (direction == Direction.RIGHT) {
                topLeft.moveDown(verticalOffset + collisionArc[curIndex].getYOffset());
                topLeft.moveRight(horizontalOffset + collisionArc[curIndex].getXOffset());
            } else { // Flipped collision stuff.  This was such a pain to calculate.
                topLeft.moveDown(verticalOffset + collisionArc[curIndex].getYOffset());
                topLeft.moveRight(getWidth() - horizontalOffset - collisionArc[curIndex].getXOffset() - collisionArc[curIndex].getWidth());
            }
            Location bottomRight = new Location(topLeft);
            bottomRight.moveDown(collisionArc[curIndex].getHeight());
            bottomRight.moveRight(collisionArc[curIndex].getWidth());
            
            if (!topLeft.equals(bottomRight)) {
                /*
                 *  We now have the topLeft and bottomRight coords of our rectangle.
                 *  Using this, we need to grab every player in our rectangle for collision
                 *  testing.
                 */
                List<ServerPlayer> players = World.getInstance().getNearbyPlayers(location);
                LongRectangle collisionRect = new LongRectangle(0, 0, 
                        bottomRight.getRawDeltaX(topLeft), bottomRight.getRawDeltaY(topLeft));
                for (ServerPlayer player : players) {
                    /*
                     *  We need the playerRect to be offset based on the difference 
                     *  between it and the attacking player's location.  Otherwise
                     *  we will fail to detect a collision at (0, 0) where the world
                     *  wraps around.
                     */
                    if (player != this && player.isAlive() && player.isPvPFlagEnabled() && !player.current.isInvincible()) {
                        player.update();
                        LongRectangle playerRect = new LongRectangle(player.getLocation().getRawDeltaX(topLeft), 
                                player.getLocation().getRawDeltaY(topLeft),
                                player.getWidth() * Location.BLOCK_GRANULARITY / WorldConstants.WORLD_BLOCK_WIDTH,
                                player.getHeight() * Location.BLOCK_GRANULARITY / WorldConstants.WORLD_BLOCK_HEIGHT);
                        if (playerRect.intersects(collisionRect)) {
                            int xOff = 0;
                            if (direction == Direction.RIGHT) {
                                xOff = (int) ((player.getLocation().getRawDeltaX(this.location) * WorldConstants.WORLD_BLOCK_WIDTH / Location.BLOCK_GRANULARITY) - horizontalOffset - collisionArc[curIndex].getXOffset());
                            } else {
                                xOff = (int) (current.getWidth() - (this.location.getRawDeltaX(player.getLocation()) * WorldConstants.WORLD_BLOCK_WIDTH / Location.BLOCK_GRANULARITY + getWidth() - horizontalOffset + collisionArc[curIndex].getFlipped().getXOffset()));
                            }
                            int yOff = (int) ((player.getLocation().getRawDeltaY(this.location) * WorldConstants.WORLD_BLOCK_HEIGHT / Location.BLOCK_GRANULARITY - verticalOffset - collisionArc[curIndex].getYOffset()));
                            float damage =  (direction == Direction.RIGHT ? collisionArc[curIndex] : collisionArc[curIndex].getFlipped()).getDamage(
                                    new Rectangle(player.getWidth(), player.getHeight()), xOff, yOff);
                            int multipliedDamage = (int)Math.round(getPvPDamageMultiplier() * damage);
                            player.damage(this, multipliedDamage);
                            // Refresh PvP duration for the client
                            if (this.pvpExpireTime != -1) {
                                this.setPvPTime(System.currentTimeMillis() + ClientConstants.PVP_FLAG_DURATION);
                            }
                        }
                    }
                }
            }
        } while (curIndex != endingIndex);
    }
    
    /**
     * Performs all checks needed to check whether a player has hit a monster.
     * @param collisionArc
     * @param startingIndex
     * @param endingIndex
     * @param horizontalOffset
     * @param verticalOffset
     */
    public void performMonsterCollisions(CollisionBlob[] collisionArc, int startingIndex,
            int endingIndex, float horizontalOffset, float verticalOffset) {
        int curIndex = startingIndex - 1;
        do {
            curIndex = (curIndex + 1) % collisionArc.length;
            
            Location topLeft = new Location(this.location);
            if (direction == Direction.RIGHT) {
                topLeft.moveDown(verticalOffset + collisionArc[curIndex].getYOffset());
                topLeft.moveRight(horizontalOffset + collisionArc[curIndex].getXOffset());
            } else { // Flipped collision stuff.  This was such a pain to calculate.
                topLeft.moveDown(verticalOffset + collisionArc[curIndex].getYOffset());
                topLeft.moveRight(getWidth() - horizontalOffset - collisionArc[curIndex].getXOffset() - collisionArc[curIndex].getWidth());
            }
            Location bottomRight = new Location(topLeft);
            bottomRight.moveDown(collisionArc[curIndex].getHeight());
            bottomRight.moveRight(collisionArc[curIndex].getWidth());
            
            if (!topLeft.equals(bottomRight)) {
                /*
                 *  We now have the topLeft and bottomRight coords of our rectangle.
                 *  Using this, we need to grab every player in our rectangle for collision
                 *  testing.
                 */
                List<ServerMonster> monsters = World.getInstance().getNearbyMonsters(location);
                LongRectangle collisionRect = new LongRectangle(0, 0, 
                        bottomRight.getRawDeltaX(topLeft), bottomRight.getRawDeltaY(topLeft));
                for (ServerMonster monster : monsters) {
                    /*
                     *  We need the playerRect to be offset based on the difference 
                     *  between it and the attacking player's location.  Otherwise
                     *  we will fail to detect a collision at (0, 0) where the world
                     *  wraps around.
                     */
                    if (monster.isAlive() && !monster.getCurrent().isInvincible()) {
                        monster.update();
                        LongRectangle playerRect = new LongRectangle(monster.getLocation().getRawDeltaX(topLeft), 
                                monster.getLocation().getRawDeltaY(topLeft),
                                monster.getWidth() * Location.BLOCK_GRANULARITY / WorldConstants.WORLD_BLOCK_WIDTH,
                                monster.getHeight() * Location.BLOCK_GRANULARITY / WorldConstants.WORLD_BLOCK_HEIGHT);
                        if (playerRect.intersects(collisionRect)) {
                            int xOff = 0;
                            if (direction == Direction.RIGHT) {
                                xOff = (int) ((monster.getLocation().getRawDeltaX(this.location) * WorldConstants.WORLD_BLOCK_WIDTH / Location.BLOCK_GRANULARITY) - horizontalOffset - collisionArc[curIndex].getXOffset());
                            } else {
                                xOff = (int) (current.getWidth() - (this.location.getRawDeltaX(monster.getLocation()) * WorldConstants.WORLD_BLOCK_WIDTH / Location.BLOCK_GRANULARITY + getWidth() - horizontalOffset + collisionArc[curIndex].getFlipped().getXOffset()));
                            }
                            int yOff = (int) ((monster.getLocation().getRawDeltaY(this.location) * WorldConstants.WORLD_BLOCK_HEIGHT / Location.BLOCK_GRANULARITY - verticalOffset - collisionArc[curIndex].getYOffset()));
                            float damage =  (direction == Direction.RIGHT ? collisionArc[curIndex] : collisionArc[curIndex].getFlipped()).getDamage(
                                    new Rectangle(monster.getWidth(), monster.getHeight()), xOff, yOff);
                            int multipliedDamage = (int)Math.round(getPvMDamageMultiplier() * damage);
                            monster.damage(this, multipliedDamage);
                        }
                    }
                }
            }
        } while (curIndex != endingIndex);
    }
    
    /**
     * Damages the player, reducing their HP by the amount given.  Does not
     * reduce HP below 0.  Passing in a negative value for 
     * <code>damageDealt</code> will result in nothing happening.  This
     * method sends packets to nearby players when there is a change in the
     * player's HP.
     * @param source The source of the damage.
     * @param damageDealt The amount of damage dealt.
     */
    private void damage(ServerPlayer source, int damageDealt) {
        if (damageDealt <= 0) {
            return;
        }
        synchronized (this) {
            setHpCurrent(hpCurrent - damageDealt);
        }
        client.broadcast(this, PacketCreator.getPvPPlayerDamaged(source, this, damageDealt, hpCurrent));
    }
    
    /**
     * Damages the player, reducing their HP by the amount given.  Does not
     * reduce HP below 0.  Passing in a negative value for 
     * <code>damageDealt</code> will result in nothing happening.  This
     * method sends packets to nearby players when there is a change in the
     * player's HP.
     * @param source The source of the damage.
     * @param damageDealt The amount of damage dealt.
     */
    public void damage(int damageDealt) {
        if (damageDealt <= 0) {
            return;
        }
        synchronized (this) {
            setHpCurrent(hpCurrent - damageDealt);
        }
        client.broadcast(this, PacketCreator.getPlayerDamaged(this, damageDealt, hpCurrent));
    }

    /**
     * Performs the collision checks from startingIndex to endingIndex.
     * 
     * This code may look ugly, but it's very fast.  On an i7-2600k, performing
     * a single collision check on a 15x15 block takes roughly 8 microseconds.
     * There are about 12 such checks needed per collision animation.
     * 
     * @param animation The animation state that contains the collision arc
     * @param startingIndex The starting index (inclusive) of the collision arc to check collisions for.
     * @param endingIndex The ending index (inclusive) of the collision arc to check collisions for.
     * @param horizontalOffset The horizontal offset that must be added to the collision blob.
     * @param verticalOffset The vertical offset that must be added to the collision blob.
     */
    public void doCollisionChecks(AnimationState animation, int startingIndex,
            int endingIndex, float horizontalOffset, float verticalOffset) {
        if (animation == null) {
            return;
        }
        CollisionBlob[] collisionArc = null;
        try {
            collisionArc = animation.getCollisionArc();
        } catch (Exception e) {
            /* FIXME:  The server is usually very good at this, so it usually
             doesn't need any correction.  However, it might be ever so
             slightly off at getting the player's location sometimes.  This is
             a very minor issue.
            */
            //e.printStackTrace();
        }
    
        // Guard
        if (collisionArc == null || startingIndex < 0 || endingIndex < 0 || 
                startingIndex >= collisionArc.length || endingIndex >= collisionArc.length) {
            return;
        }
    
        try {
            update();
            performBlockCollisions(collisionArc, startingIndex, endingIndex, horizontalOffset, verticalOffset);
            performMonsterCollisions(collisionArc, startingIndex, endingIndex, horizontalOffset, verticalOffset);
            performPlayerCollisions(collisionArc, startingIndex, endingIndex, horizontalOffset, verticalOffset);
        } catch (Exception e) {
            e.printStackTrace(); // This should only happen if someone screwed up the arc image...
        }
    }
    
    public void addExperience(SkillType type, long experience) {
        if (experience > 0) {
            synchronized (this) {
                // If we leveled up, recalculate the stats
                if (skills.addExperience(type, experience)) {
                    recalculateStats();
                }
            }
            
            if (client != null) { // The player's experience changed, so inform them.
                client.announce(PacketCreator.getSkillExperience(
                        type, skills.getTotalExperience(type)));
            }
        }
    }

    @Override
    /**
     * Sets the PvP flag timer.
     * @param toggleTime The time in milliseconds at which the PvP flag will
     *                   expire.  A value of -1 indicates that the PvP flag
     *                   will not expire.
     */
    public void setPvPTime(long toggleTime) {
        setPvPTime(toggleTime, true);
    }
    
    /**
     * Sets the PvP flag timer.
     * @param toggleTime The time in milliseconds at which the PvP flag will
     *                   expire.  A value of -1 indicates that the PvP flag
     *                   will not expire.
     * @param broadcast True if we should broadcast this change to the player.
     *                  An example of when you DON'T want to broadcast is while
     *                  initializing the player for the first time.
     */
    public void setPvPTime(long toggleTime, boolean broadcast) {
        synchronized (this) {
            this.pvpExpireTime = toggleTime;
        }
        if (broadcast) {
            client.broadcast(this, PacketCreator.getPvPToggleResponse(this));
        }
    }
    
    @Override
    /**
     * Retrieves the PvP flag timer.
     * @return pvpFlagTimer A value of -1 indicates that the PvP flag will not
     *                      expire.  Otherwise this is the time since the Epoch
     *                      in milliseconds at which the PvP flag will expire.
     */
    public long getPvPTime() {
        return this.pvpExpireTime;
    }
    
    /**
     * Retrieves the amount of time in milliseconds until the PvP flag expires.
     * Returns -1 if the PvP flag will not expire.
     * @return pvpFlagDuration Returns time in milliseconds until the PvP flag
     *                         expires, or 0 if it has already expired.
     */
    public long getPvPTimeRemaining() {
        long result = pvpExpireTime;
        if (result != -1) {
            result -= System.currentTimeMillis();
            result = result < 0 ? 0 : result;
        }
        
        return result;
    }
    
    @Override
    /**
     * Retrieves whether the player's PvP flag is on.
     * @return pvpFlagEnabled
     */
    public boolean isPvPFlagEnabled() {
        return this.pvpExpireTime == -1 || 
                this.pvpExpireTime > System.currentTimeMillis();
    }
    
    @Override
    /**
     * Sets the player's fall speed.  Used in gravity calculations.
     * @param fallSpeed The new player's fall speed.  Use negative values to
     *                  fall "up."
     */
    public void setFallSpeed(float fallSpeed) {
        synchronized (this) {
            this.fallSpeed = fallSpeed;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Retrieves the last location that the client told us this player was at.
     * This is needed because of the player pool in the server, which needs to
     * know where a player is in order to remove it from its associated chunk.
     * If a separate location was not kept for the client's last location,
     * then we would need to update the location in the player pool on every
     * single player update or player movement.  This would cause an 
     * unnecessary burden on the server because of the write locks.
     * @return clientLocation
     */
    public BoundLocation getClientLocation() {
        return clientLocation;
    }
    
    /**
     * Sets the last location that the client told us this player was at.
     * This is needed because of the player pool in the server, which needs to
     * know where a player is in order to remove it from its associated chunk.
     * If a separate location was not kept for the client's last location,
     * then we would need to update the location in the player pool on every
     * single player update or player movement.  This would cause an 
     * unnecessary burden on the server because of the write locks.
     * @param clientLocation
     */
    public void setClientLocation(BoundLocation clientLocation) {
        this.clientLocation = clientLocation;
    }

    /**
     * Adds an item to the player's inventory, notifying the client of the
     * change if any changes occurred.
     * @param itemId The id of the item being added.
     * @param quantity The number of the item to add.
     */
    public void addItem(int itemId, long quantity) {
        synchronized (this) {
            inventory.addItem(new Item(itemId),  quantity);
        }
        // TODO:  Make addItem / removeItem return a boolean if the value was changed.
        client.announce(PacketCreator.getSetItem(itemId, 
                inventory.getQuantity(itemId)));
    }
    
    /**
     * Adds gold to the player's inventory, notifying the client of the change
     * if any changes occurred.  No changes are made if <code>gold</code> is <= 0.
     * @param gold The amount of gold to add.
     */
    public void addGold(long gold) {
        if (gold <= 0) {
            return;
        }
        
        long previousGold;
        long newGold;
        
        synchronized (this) {
            previousGold = inventory.getGold();
            inventory.addGold(gold);
            newGold = inventory.getGold();
        }
        
        if (previousGold != newGold) {
            client.announce(PacketCreator.getSetGold(newGold));
        }
    }

    /**
     * Serializes the bytes for this class.  This method is used when saving player data
     * to the database.
     * @return playerBytes
     */
    public byte[] getBytes() {
        GenericLittleEndianWriter writer = new GenericLittleEndianWriter();
        
        int lengthOffset = 2;
        
        // Write metadata
        writer.writeShort(DatabaseConstants.DB_CHARACTER_VERSION);
        writer.writeInt(0); // Length.  Filled in later.
        
        // Write character data
        writer.writeInt(getId());
        writer.writePrefixedAsciiString(getName());
        writer.writeInt(getHpMax());
        writer.writeInt(getHpCurrent());
        writer.write(getLocation().getBytes());
        writer.write(getDirection().getValue());
        writer.write(getInventory().getBytes());
        writer.write(getSkills().getBytes());
        writer.writeLong(getPvPTimeRemaining());
        writer.writeFloat(getFallSpeed());
        
        // Write length
        byte[] result = writer.toByteArray();
        GenericLittleEndianWriter lengthWriter = new GenericLittleEndianWriter();
        lengthWriter.writeInt(result.length);
        byte[] lengthBytes = lengthWriter.toByteArray();
        for (int i=lengthOffset; i < lengthOffset + 4; ++i) {
            result[i] = lengthBytes[i-lengthOffset];
        }
        
        return result;
    }
    
    /**
     * Returns a new player from an SLEA.  This should be used when loading in
     * a player from the database.
     * @param slea A seekable little endian accessor that is
     *        currently at the position containing the bytes of a ServerPlayer.
     * @return player
     */
    public static ServerPlayer fromBytes(
            GenericSeekableLittleEndianAccessor slea) {
        short characterVersion = slea.readShort(); // The version that this character's data was last saved in.
        int length = slea.readInt(); // The length in bytes of this character's data.
        
        ServerPlayer result = null;
        
        switch (characterVersion) {
        case 1:
            result = loadFromBytesV1(length, slea);
            break;
        default:
            // Unable to read character data.  Skip it.
            System.err.println("Unable to read character data.  Incorrect version: " + characterVersion);
            slea.skip(length);
            break;
        }
        
        return result;
    }
    
    /**
     * Loads a specific type of player.  Version number is obtained prior to this
     * by looking at the first 2 bytes of the metadata preceding character data.
     * In a database "BLOB" this will be the first two bytes of the BLOB.  Length
     * is made up of the 4 bytes directly after the character version.<br /><br />
     * NOTE:  You should ONLY append to this as new versions are added.  Do NOT
     * rearrange the ordering of ANYTHING.
     * @param length  The remaining length of the character's data.
     * @param slea An SLEA positioned at the character data.  The character data
     * are the bytes directly following the character version and character data
     * length.
     * @return player
     */
    public static ServerPlayer loadFromBytesV1(int length, 
            GenericSeekableLittleEndianAccessor slea) {
        ServerPlayer result = new ServerPlayer();
        
        result.setId(slea.readInt());
        result.setName(slea.readPrefixedAsciiString());
        result.setHpMax(slea.readInt());
        result.setHpCurrent(slea.readInt());
        result.setLocation(BoundLocation.fromBytes(slea));
        result.setDirection(Direction.fromValue(slea));
        result.setInventory(Inventory.fromBytes(slea));
        result.setSkills(Skills.fromBytes(slea));
        
        // Special case.
        long pvpRemainingDuration = slea.readLong();
        if (pvpRemainingDuration != -1) {
            result.setPvPTime(pvpRemainingDuration + System.currentTimeMillis(), false);
        }
        
        result.setFallSpeed(slea.readFloat());
        
        
        return result;
    }

    /**
     * Revives a dead player, restoring their HP to full and respawning them at their
     * spawn location.
     */
    public void revive() {
        if (!isAlive()) {
            synchronized (this) {
                setHpCurrent(getHpMax());
                location.setRawX(0);
                location.setRawY(0);
                current = new SwordIdle(this, current);
                accelerateDown(100000, 100f, 100f);
                update(100000);
            }
            client.announce(PacketCreator.getRevive(this));
        }
    }
}
