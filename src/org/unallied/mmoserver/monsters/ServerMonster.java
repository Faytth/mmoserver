package org.unallied.mmoserver.monsters;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.CollisionBlob;
import org.unallied.mmocraft.Direction;
import org.unallied.mmocraft.Location;
import org.unallied.mmocraft.LootIntf;
import org.unallied.mmocraft.RawPoint;
import org.unallied.mmocraft.Velocity;
import org.unallied.mmocraft.animations.AnimationState;
import org.unallied.mmocraft.blocks.Block;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.geom.LongRectangle;
import org.unallied.mmocraft.items.Item;
import org.unallied.mmocraft.monsters.Monster;
import org.unallied.mmocraft.monsters.MonsterData;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.skills.SkillType;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmoserver.ai.AI;
import org.unallied.mmoserver.constants.ServerConstants;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.Server;
import org.unallied.mmoserver.server.ServerPlayer;
import org.unallied.mmoserver.server.World;

public class ServerMonster extends Monster {
    
    /** The last time that this monster had its position updated. */
    private long lastUpdateTime = 0;

    /** Used in calculation of gold that the player should receive. */
    private Random random = new Random();

    /**
     * 
     */
    private static final long serialVersionUID = -6756229562346956165L;

    private PriorityQueue<PlayerAggro> aggro = new PriorityQueue<PlayerAggro>(10, new Comparator<PlayerAggro>() {
            public int compare(PlayerAggro a, PlayerAggro b) {
                return (int) (b.getThreat() - a.getThreat());
            }
    });
    
    /** The AI to use for this monster's controls. */
    protected AI ai;
    
    public ServerMonster(final ServerMonsterData data, int id, BoundLocation location) {
        super(data, id, location);
        try {
            this.ai = data.getAI().getClass().newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (this.ai == null) {
            throw new NullPointerException("Unable to instantiate instance of ServerMonster.  AI was null for monster data ID: " + data.getId());
        }
        this.ai.setMonster(this);
    }

    /**
     * Returns true if a target on the aggro list is within range.
     * @return hasNearby
     */
    public boolean hasNearbyTarget() {
        boolean result = false;
        
        for (PlayerAggro a : aggro) {
            double distance = a.getPlayer().getLocation().getDistance(location);
            // If the player is close enough to the monster
            if (distance < ServerConstants.OBJECT_DESPAWN_DISTANCE) {
                result = true;
                break;
            }
        }
        
        return result;
    }

    private void updateMovement(int delta) {
        boolean idle = true;
        
        // Perform movement
        if (Authenticator.canLivingMove(this)) {
            if (ai.isMovingLeft(null)) {
                idle &= !this.tryMoveLeft(delta);
            }
            if (ai.isMovingRight(null)) {
                idle &= !this.tryMoveRight(delta);
            }
            if (idle && this.getState().canChangeVelocity()) {
                this.setVelocity(0, this.getVelocity().getY());
            }
            if (ai.isMovingUp(null)) {
                this.tryMoveUp(delta);
                idle = false;
            } else {
                this.setMovingUp(false); // Have to tell the monster that we're not moving up
            }
            if (ai.isMovingDown(null)) {
                this.tryMoveDown(delta);
                idle = false;
            }
            if (!ai.isMovingUp(null) && !ai.isMovingDown(null)) {
                if (this.getState().canChangeVelocity()) {
                    this.setVelocity(this.getVelocity().getX(), 0);
                }
            }
        }        
    
        // perform attacks
        if (Authenticator.canLivingAttack(this)) {
            this.attackUpdate(ai.isBasicAttack(null));
        }

        if (idle) {
            this.idle();
            if (this.getState().canChangeVelocity()) {
                this.setVelocity(0, this.getVelocity().getY());
            }
        }
        
        // Perform player-based controls
        if (Authenticator.canLivingMove(this)) {
            try {
                shieldUpdate(ai.isShielding(null));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
    
    /**
     * Updates the monster.
     */
    public void update(long delta) {
        synchronized (this) {
            // Check the current target.  If they're too far away, remove them from aggro.
            if (!aggro.isEmpty()) {
                double distance = aggro.element().getPlayer().getLocation().getDistance(location);
                // If player is too far away, remove them from aggro
                try {
                    if (distance >= ServerConstants.OBJECT_DESPAWN_DISTANCE || !aggro.element().getPlayer().getClient().isLoggedIn()) {
                        aggro.remove();
                    }
                } catch (Throwable t) {
                    aggro.remove();
                    t.printStackTrace();
                }
            }
            
            // Perform AI
            ai.update(delta);
            updateMovement((int) (delta));
            
            // Perform gravity checks
            BoundLocation tempLocation = new BoundLocation(location);
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
            
            // Do AI (NOTE:  Do NOT forget to call World.getInstance().moveMonster(this) on movement!!!
        }
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
    
    /**
     * Sends a packet to all nearby players.
     * @param packet
     */
    protected void sendPacket(Packet packet) {
        Server.getInstance().localBroadcast(location, packet);
    }
    
    @Override
    public void setState(AnimationState current) {
        if (this.current != current && current != null) {
            this.current = current;
            sendPacket(PacketCreator.getMonsterMovement(this));
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
     * Gives the player specified aggro on the mob.  This method does so by
     * giving the player threat+1 of the current highest target.  If the player
     * is already the target with the highest threat, this method does nothing.<br />
     * NOTE:  If a player is at max threat already, it will not be possible to
     * gain aggro over them.
     * @param player the player who should be moved to the top of the aggro list.
     */
    public void setAggro(ServerPlayer player) {
        if (aggro.isEmpty()) {
            aggro.offer(new PlayerAggro(player));
        } else if (player != aggro.element().getPlayer()) {
            // If aggro contains this player already
            if (aggro.contains(new PlayerAggro(player))) {
                for (PlayerAggro a : aggro) {
                    if (a.getPlayer().equals(player)) {
                        // This is broken up into two operations to prevent negatives.
                        a.setThreat(aggro.element().getThreat());
                        a.addThreat(1);
                        break;
                    }
                }
            } else {
                PlayerAggro newPlayer = new PlayerAggro(player);
                newPlayer.setThreat(aggro.element().getThreat());
                newPlayer.addThreat(1);
                aggro.offer(newPlayer);
            }
        }
    }
    
    /**
     * Gives the player specified some added threat.  If the player does not
     * currently have any threat, they are added to the monster's aggro list
     * and given the proper amount of threat.  If threat is <= 0 or player
     * is null, this is a no-op.
     * @param player The player who is gaining threat.
     * @param threat The amount of threat to give the player.
     */
    public void addThreat(ServerPlayer player, int threat) {
        // Guard
        if (threat <= 0 || player == null) {
            return;
        }
        if (aggro.isEmpty()) {
            aggro.offer(new PlayerAggro(player));
        } else {
            if (aggro.contains(new PlayerAggro(player))) {
                for (PlayerAggro a : aggro) {
                    if (a.getPlayer().equals(player)) {
                        a.addThreat(threat);
                        break;
                    }
                }
            } else {
                aggro.offer(new PlayerAggro(player, threat));
            }
        }
    }
    
    /**
     * Rewards a player with loot and experience.  This should be called whenever
     * the monster dies.
     * @param player
     */
    public void rewardPlayer(ServerPlayer player) {
        MonsterData data = ServerMonsterManager.getInstance().getMonsterData(dataId);
        
        // Reward Experience
        // TODO:  Add way to get exp in other combat skills other than Strength, like ranged / magic?
        player.addExperience(SkillType.STRENGTH, data.getExperience());
        player.addExperience(SkillType.CONSTITUTION, data.getExperience() / 2);
        
        // Reward Gold
        int goldDifference = data.getMaximumGold() - data.getMinimumGold();
        int goldLooted = data.getMinimumGold();
        if (goldDifference > 0) {
            goldLooted += random.nextInt(goldDifference);
        }
        player.addGold(goldLooted);
        
        // Reward items
        LootIntf loot = data.getLoot();
        List<Item> items = loot.getLoot(player.getLootMultiplier());
        for (Item item : items) {
            player.addItem(item.getId(), item.getQuantity());
        }
    }
    
    /**
     * Damages the monster and increases a player's aggro on the target.  Does
     * not reduce HP below 0.  Passing in a negative value for <code>damage</code>
     * will result in nothing happening.  This method sends packets to nearby
     * players when there is a change in the monster's HP.
     * @param source The source of the damage.
     * @param damage The amount of damage dealt.
     */
    public void damage(ServerPlayer source, int damage) {
        if (damage <= 0) {
            return;
        }
        
        // Tell our AI
        ai.damaged(source, damage);
        
        // Change the HP
        setHpCurrent(hpCurrent - damage);
        if (!isAlive()) { // The monster died!  Reward the players then inform the World
            for (PlayerAggro pa : aggro) {
                if (pa.getThreat() > 1) { // Must be greater than 1 because the player starts at 1.
                    rewardPlayer(pa.getPlayer());
                }
            }
            // Delete ourself
            Server.getInstance().getServerMonsterPool().removeMonster(id);
        }
        
        // Broadcast the damage
        Server.getInstance().localBroadcast(location, 
                PacketCreator.getMonsterDamaged(source, this, damage, hpCurrent));
    }
    
    @Override
    /**
     * Sets the velocity to x and y.  These values should be in pixels / millisecond.
     * @param x The horizontal velocity to set in pixels / millisecond.
     * @param y The vertical velocity to set in pixels / millisecond.
     */
    public void setVelocity(float x, float y) {
        if (x != velocity.getX() || y != velocity.getY()) {
            velocity.setX(x);
            velocity.setY(y);
            try {
                sendPacket(PacketCreator.getMonsterMovement(this));
            } catch (Throwable t) {
                // We don't care if this fails.
            }
        }
    }
    
    @Override
    /**
     * Sets the direction that the living object is facing (left or right).
     * @param direction
     */
    public void updateDirection(Direction direction) {
        // If we need to update the direction
        if (this.direction != direction) {
            this.direction = direction;
            sendPacket(PacketCreator.getMonsterDirection(this));
        }
    }
    
    @Override
    /**
     * Sets the monster's fall speed.  Used in gravity calculations.
     * @param fallSpeed The monster's new fall speed.  Use negative values to
     *                  fall "up."
     */
    public void setFallSpeed(float fallSpeed) {
        synchronized (this) {
            this.fallSpeed = fallSpeed;
            lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Retrieves the player with the most aggro.
     * @return the player with the most aggro
     * @throws NoSuchElementException if there are no players in the aggro list.
     */
    public ServerPlayer getCurrentTarget() throws NoSuchElementException {
        return aggro.element().getPlayer();
    }
    
    /**
     * Performs all checks needed to check whether a monster has hit a player.
     * @param collisionArc
     * @param startingIndex
     * @param endingIndex
     * @param horizontalOffset
     * @param verticalOffset
     */
    private void performPlayerCollisions(CollisionBlob[] collisionArc, int startingIndex,
            int endingIndex, float horizontalOffset, float verticalOffset) {
        if (!this.isAlive()) {
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
                    if (player.isAlive()) {
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
                            int multipliedDamage = (int)Math.round(getMonsterDamageMultiplier() * damage * player.getDefenseMultiplier());
                            if (player.getCurrent().isInvincible()) { // Player is invincible
                                // Give player defense exp for being awesome.
                                player.addExperience(SkillType.DEFENSE,
                                        player.getHpCurrent() > multipliedDamage ? (int) (multipliedDamage * 1.0) :
                                        (int) (player.getHpCurrent() * 1.0));
                            } else { // Not invincible, so hurt the player
                                if (player.getCurrent().isShielding()) { // Reduce damage if shielding
                                    // Give player defense exp for being sort of awesome.
                                    int damageShielded = (int) (multipliedDamage * 0.75);
                                    if (damageShielded > 0) {
                                        multipliedDamage -= damageShielded; // reduce damage taken
                                        int expGained = player.getHpCurrent() > damageShielded ? (int) (damageShielded * 0.35) :
                                            (int) (player.getHpCurrent() * 0.35);
                                        expGained = expGained < 1 ? 1 : expGained;
                                        player.addExperience(SkillType.DEFENSE, expGained);
                                    }
                                }
                                player.damage(multipliedDamage);
                                int expGain = player.getHpCurrent() > multipliedDamage ? (int) (multipliedDamage * 0.25) :
                                    (int) (player.getHpCurrent() * 0.25);
                                expGain = expGain < 1 ? 1 : expGain;
                                player.addExperience(SkillType.CONSTITUTION, expGain);
                            }
                        }
                    }
                }
            }
            
        } while (curIndex != endingIndex);
    }
    
    public double getMonsterDamageMultiplier() {
        return data.getDamageMultiplier() * (1000.0 + data.getLevel() * 150.0);
    }
    
    @Override
    public void doCollisionChecks(CollisionBlob[] collisionArc, int startingIndex,
            int endingIndex, float horizontalOffset, float verticalOffset) {
        // Guard
        if (collisionArc == null || startingIndex < 0 || endingIndex < 0 || 
                startingIndex >= collisionArc.length || endingIndex >= collisionArc.length) {
            return;
        }
    
        try {
            update();
            performPlayerCollisions(collisionArc, startingIndex, endingIndex, horizontalOffset, verticalOffset);
        } catch (Exception e) {
            e.printStackTrace(); // This should never happen.
        }
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#moveRawRight(long)}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     * @param x
     */
    public void moveRawRight(long x) {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.moveRawRight(x);
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#moveRawDown(long)}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     * @param y
     */
    public void moveRawDown(long y) {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.moveRawDown(y);
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#setRawX(long)}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     * @param x
     */
    public void setRawX(long x) {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.setRawX(x);
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#setRawY(long)}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     * @param y
     */
    public void setRawY(long y) {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.setRawY(y);
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#setXOffset(float)}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     * @param x
     */
    public void setXOffset(float x) {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.setXOffset(x);
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#setYOffset(float)}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     * @param y
     */
    public void setYOffset(float y) {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.setYOffset(y);
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#decrementX()}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     */
    public void decrementX() {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.decrementX();
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
    
    @Override
    /**
     * A wrapper for {@link BoundLocation#decrementY()}.  Use this instead
     * of accessing location directly.  Otherwise you could break the server's
     * ability to know where players and monsters are.
     */
    public void decrementY() {
        BoundLocation newLocation = new BoundLocation(location);
        newLocation.decrementY();
        World.getInstance().moveMonster(this,  newLocation);
        location = newLocation;
    }
}
