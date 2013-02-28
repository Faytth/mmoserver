package org.unallied.mmoserver.ai;

import java.util.NoSuchElementException;
import java.util.Random;

import org.newdawn.slick.Input;
import org.unallied.mmocraft.ControlType;
import org.unallied.mmocraft.Direction;
import org.unallied.mmoserver.constants.ServerConstants;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.server.ServerPlayer;
import org.unallied.mmoserver.server.World;

public class BasicGroundAttackAI implements AI {
    
    /** 
     * The maximum distance along the x axis between the player and monster
     * before the monster pursues the player.
     */
    private transient final int MAX_X_DISTANCE = 26;
    
    private ServerMonster monster = null;
    
    private long attackCooldown = 0;
    
    Random random = new Random();
    
    @Override
    public void update(long delta) {
        attackCooldown -= delta;
        attackCooldown = attackCooldown < 0 ? 0 : attackCooldown;
        
        try {
            ServerPlayer player = monster.getCurrentTarget();
            double deltaX = player.getLocation().getDeltaX(monster.getLocation());
            if (deltaX < 0 && monster.getDirection() == Direction.RIGHT) {
                monster.setDirection(Direction.LEFT);
            } else if (deltaX > 0 && monster.getDirection() == Direction.LEFT) {
                monster.setDirection(Direction.RIGHT);
            }
        } catch (NoSuchElementException e) {
        }
        
    }

    @Override
    public void setMonster(ServerMonster monster) {
        this.monster = monster;
    }

    @Override
    public void damaged(ServerPlayer player, int damage) {
        // Give player some aggro
        monster.addThreat(player, 
                (int) (damage * ServerConstants.MONSTER_THREAT_DAMAGE_MULTIPLIER));
    }

    @Override
    public void healed(ServerPlayer healer, ServerPlayer healee, int healAmount) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean isMovingLeft(Input input) {
        try {
            ServerPlayer player = monster.getCurrentTarget();
            double deltaX = player.getLocation().getDeltaX(monster.getLocation());

            return (deltaX < -player.getWidth() - MAX_X_DISTANCE || (monster.getDirection() == Direction.RIGHT && deltaX < -50));
        } catch (NoSuchElementException e) {
            World.getInstance().removeMonster(monster);
        }
        return false;
    }

    @Override
    public boolean isMovingRight(Input input) {
        try {
            ServerPlayer player = monster.getCurrentTarget();
            double deltaX = player.getLocation().getDeltaX(monster.getLocation());

            return (deltaX > player.getWidth() / 2 + MAX_X_DISTANCE || (monster.getDirection() == Direction.LEFT && deltaX > 50));
        } catch (NoSuchElementException e) {
            World.getInstance().removeMonster(monster);
        }
        return false;
    }

    @Override
    public boolean isMovingUp(Input input) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isMovingDown(Input input) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isBasicAttack(Input input) {
        if (!isMovingLeft(input) && !isMovingRight(input) && attackCooldown <= 0) {
            try {
                ServerPlayer player = monster.getCurrentTarget();
                double deltaY = player.getLocation().getDeltaY(monster.getLocation());
                if (Math.abs(deltaY) < player.getHeight() * 1.5) {
                    attackCooldown = random.nextLong() % 1500 + 100;
                    return true;
                }
            } catch (NoSuchElementException e) {
            }
        }
        return false;
    }

    @Override
    public boolean isShielding(Input input) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public ControlType getKeyType(int key) {
        // TODO Auto-generated method stub
        return null;
    }

}
