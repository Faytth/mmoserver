package org.unallied.mmoserver.server;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import org.unallied.mmocraft.CollisionBlob;
import org.unallied.mmocraft.Direction;
import org.unallied.mmocraft.Location;
import org.unallied.mmocraft.Player;
import org.unallied.mmocraft.animations.AnimationState;
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.client.Game;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.net.sessions.TerrainSession;
import org.unallied.mmoserver.client.Client;


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
    private Client client = null;
    
    public Client getClient() {
        return client;
    }
    
    public void setClient(Client client) {
        this.client = client;
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
    public Location collide(Location start, Location end, float vf) {
        Location result = new Location(start);
        try {            
            // We split this into horizontal then vertical testing.
            Location horizontalEnd = new Location(start);
            horizontalEnd.setX(end.getX());
            horizontalEnd.setXOffset(end.getXOffset());
            World world = World.getInstance();
            
            // If air
            Location horizontalCollide = new Location(world.collideWithBlock(start, horizontalEnd));
            if (horizontalCollide.equals(horizontalEnd)) {
                result.setX(end.getX());
                result.setXOffset(end.getXOffset());
            }
            
            // Vertical testing
            if (end.getY() != start.getY() || end.getYOffset() != start.getYOffset()) {
                Location verticalEnd = new Location(result);
                verticalEnd.setY(end.getY());
                verticalEnd.setYOffset(end.getYOffset());
                
                // If air
                Location verticalCollide = new Location(world.collideWithBlock(result, verticalEnd));
                // If we didn't hit anything
                if (verticalCollide.equals(verticalEnd)) {
                    fallSpeed = vf;
                    if (fallSpeed > 0.0f) { // tell our state that our player is falling
                        current.fall();
                    }
                } else if (fallSpeed < 0.0f) { // We hit the ceiling!
                    fallSpeed = 0.0f;
                } else { // we landed on something!
                    current.land(); // tell our state that our player has landed on the ground
                    fallSpeed = 0.0f;
                }
                result = verticalCollide;
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
        for (Point2D.Double p : hitbox) {
            Location start = new Location(location);
            start.moveRight((float) p.getX());
            start.moveDown((float) p.getY());
            Block block = World.getInstance().getBlock(start);
            if (block != null && block.isCollidable()) {
                return true;
            }
        }
        
        return false;
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
        CollisionBlob[] collisionArc = animation.getCollisionArc();
        
        // Guard
        if (collisionArc == null || startingIndex < 0 || endingIndex < 0 || 
                startingIndex >= collisionArc.length || endingIndex >= collisionArc.length) {
            return;
        }
    
        try {
            int curIndex = startingIndex - 1;
            do {
                curIndex = (curIndex + 1) % collisionArc.length;
                
                TerrainSession ts = Game.getInstance().getClient().getTerrainSession();
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
                        if (ts.getBlock(x, y).isCollidable() || true) {
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
//                                ts.setBlock(x, y, new AirBlock());
                                // Update block damage
                                // Broadcast the damage to everyone nearby
                            }
                        }
                    }
                }
            } while (curIndex != endingIndex);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace(); // This should only happen if someone screwed up the arc image...
        }
    }
    
    /**
     * Accelerates player downwards.
     * @param delta The time in milliseconds to accelerate downwards
     * @param acceleration The acceleration rate of the fall in pixels
     * @param terminalVelocity The maximum velocity in pixels
     */
    public void accelerateDown(int delta, float acceleration, float terminalVelocity) {
        // Guard
        if (hitbox == null) {
            init();
        }
        
        // FIXME:  This is a bit incorrect
        boolean hitSomething = false;  // true if we hit something
        float t = delta / 1000.0f;
        float vf = fallSpeed + ((terminalVelocity - fallSpeed) / terminalVelocity) * t * acceleration;
        if (vf > terminalVelocity) {
            vf = terminalVelocity;
        }
        Point2D.Double distance = new Point2D.Double(); // distance contains the distance that all points are supposed to move
        distance.setLocation(0.0, ((fallSpeed + vf) / 2.0f) * t);
        
        World world = World.getInstance();
        
        // Iterate over all points in our hit box, and fix our end location as needed.
        for (Point2D.Double p : hitbox) {
            // our location should be the top-left corner.  Fix offsets as needed for start / end
            Location start = new Location(location);
            start.moveRight((float) p.getX());
            start.moveDown((float) p.getY());
            Location end = new Location(start);
            end.moveRight((float) distance.getX());
            end.moveDown((float) distance.getY());
            
            // Get our new location
            Location newEnd = new Location(world.collideWithBlock(start, end));
            
            hitSomething |= !newEnd.equals(end);
            
            // We now need to fix our distance based on our new end
            distance.setLocation(
                    (newEnd.getX()-start.getX()) * WorldConstants.WORLD_BLOCK_WIDTH + (newEnd.getXOffset()-start.getXOffset()),
                    (newEnd.getY()-start.getY()) * WorldConstants.WORLD_BLOCK_HEIGHT + (newEnd.getYOffset()-start.getYOffset()));
        }
        
        // If we didn't hit anything
        if (!hitSomething) {
            fallSpeed = vf;
            if (fallSpeed > 0.0f) { // tell our state that our player is falling
                current.fall();
            }
        } else if (fallSpeed < 0.0f) { // We hit the ceiling!
            fallSpeed = 0.0f;
        } else { // we landed on something!
            current.land(); // tell our state that our player has landed on the ground
            fallSpeed = 0.0f;
        }
        
        // Our distance is now the farthest we can travel
        location.moveRight((float) distance.getX());
        location.moveDown((float) distance.getY());
    }
}
