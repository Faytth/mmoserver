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
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.geom.LongRectangle;
import org.unallied.mmocraft.items.Item;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.skills.SkillType;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;


/**
 * A server player is a wrapper for player that contains important server data, such
 * as the player's client.
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
        if (lastUpdateTime == 0) {
            lastUpdateTime = System.currentTimeMillis();
        }
        long curTime = System.currentTimeMillis();
        update(curTime - lastUpdateTime);
        lastUpdateTime = curTime; // Override the last update time
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
            
            if (topLeft.equals(bottomRight)) {
                return;
            }
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
            
            if (topLeft.equals(bottomRight)) {
                return;
            }
            
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
                            collisionWidth * Location.BLOCK_GRANULARITY / WorldConstants.WORLD_BLOCK_WIDTH,
                            collisionHeight * Location.BLOCK_GRANULARITY / WorldConstants.WORLD_BLOCK_HEIGHT);
                    if (playerRect.intersects(collisionRect)) {
                        int xOff = 0;
                        if (direction == Direction.RIGHT) {
                            xOff = (int) ((player.getLocation().getRawDeltaX(this.location) * WorldConstants.WORLD_BLOCK_WIDTH / Location.BLOCK_GRANULARITY) - horizontalOffset - collisionArc[curIndex].getXOffset());
                        } else {
                            xOff = (int) (current.getWidth() - (this.location.getRawDeltaX(player.getLocation()) * WorldConstants.WORLD_BLOCK_WIDTH / Location.BLOCK_GRANULARITY + getWidth() - horizontalOffset + collisionArc[curIndex].getFlipped().getXOffset()));
                        }
                        int yOff = (int) ((player.getLocation().getRawDeltaY(this.location) * WorldConstants.WORLD_BLOCK_HEIGHT / Location.BLOCK_GRANULARITY - verticalOffset - collisionArc[curIndex].getYOffset()));
                        float damage =  (direction == Direction.RIGHT ? collisionArc[curIndex] : collisionArc[curIndex].getFlipped()).getDamage(
                                new Rectangle(collisionWidth, collisionHeight), xOff, yOff);
                        int multipliedDamage = (int)Math.round(getPvPDamageMultiplier() * damage);
                        player.damage(this, multipliedDamage);
                        // Refresh PvP duration for the client
                        if (this.pvpToggleTime != -1) {
                            this.setPvPTime(System.currentTimeMillis() + ClientConstants.PVP_FLAG_DURATION);
                        }
                    }
                }
            }
            
        } while (curIndex != endingIndex);
    }
    
    /**
     * Retrieves whether the player is currently alive or dead.
     * @return true if the player is alive, else false.
     */
    public boolean isAlive() {
        return hpCurrent > 0;
    }
    
    /**
     * Damages the player, reducing their HP by the amount given.  Does not
     * reduce HP below 0.  Passing in a negative value for 
     * <code>multipliedDamage</code> will result in nothing happening.  This
     * method sends packets to nearby players when there is a change in the
     * player's HP.
     * @param source The source of the damage.
     * @param damageDealt The amount of damage dealt.
     */
    private void damage(ServerPlayer source, int damageDealt) {
        if (damageDealt <= 0) {
            return;
        }
        hpCurrent -= damageDealt;
        hpCurrent = hpCurrent < 0 ? 0 : hpCurrent;
        client.broadcast(this, PacketCreator.getPvPPlayerDamaged(source, this, damageDealt, hpCurrent));
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
            e.printStackTrace();
        }
    
        // Guard
        if (collisionArc == null || startingIndex < 0 || endingIndex < 0 || 
                startingIndex >= collisionArc.length || endingIndex >= collisionArc.length) {
            return;
        }
    
        try {
            update();
            performBlockCollisions(collisionArc, startingIndex, endingIndex, horizontalOffset, verticalOffset);
            performPlayerCollisions(collisionArc, startingIndex, endingIndex, horizontalOffset, verticalOffset);
        } catch (Exception e) {
            e.printStackTrace(); // This should only happen if someone screwed up the arc image...
        }
    }
    
    public void addExperience(SkillType type, long experience) {
        if (experience > 0) {
            skills.addExperience(type, experience);
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
        this.pvpToggleTime = toggleTime;
        client.broadcast(this, PacketCreator.getPvPToggleResponse(this));
    }
    
    @Override
    /**
     * Retrieves the PvP flag timer.
     * @return pvpFlagTimer A value of -1 indicates that the PvP flag will not
     *                      expire.  Otherwise this is the time since the Epoch
     *                      in milliseconds at which the PvP flag will expire.
     */
    public long getPvPTime() {
        return this.pvpToggleTime;
    }
    
    @Override
    /**
     * Retrieves whether the player's PvP flag is on.
     * @return pvpFlagEnabled
     */
    public boolean isPvPFlagEnabled() {
        return this.pvpToggleTime == -1 || 
                this.pvpToggleTime > System.currentTimeMillis();
    }
    
    @Override
    /**
     * Sets the player's fall speed.  Used in gravity calculations.
     * @param fallSpeed The new player's fall speed.  Use negative values to
     *                  fall "up."
     */
    public void setFallSpeed(float fallSpeed) {
        this.fallSpeed = fallSpeed;
        lastUpdateTime = System.currentTimeMillis();
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
        inventory.addItem(new Item(itemId),  quantity);
        // TODO:  Make addItem / removeItem return a boolean if the value was changed.
        client.announce(PacketCreator.getSetItem(itemId, 
                inventory.getQuantity(itemId)));
    }
}
