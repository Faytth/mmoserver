package server;

import java.awt.Rectangle;

import org.unallied.mmocraft.CollisionBlob;
import org.unallied.mmocraft.Direction;
import org.unallied.mmocraft.Location;
import org.unallied.mmocraft.Player;
import org.unallied.mmocraft.animations.AnimationState;
import org.unallied.mmocraft.client.Game;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.net.sessions.TerrainSession;

import client.Client;

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
}
