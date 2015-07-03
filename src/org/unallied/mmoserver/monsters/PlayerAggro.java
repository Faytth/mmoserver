package org.unallied.mmoserver.monsters;

import org.unallied.mmocraft.utils.HashCodeUtil;
import org.unallied.mmoserver.server.ServerPlayer;

public class PlayerAggro {
    private ServerPlayer player;
    private long threat;
    
    private transient int hashCodeValue = 0;
    
    public PlayerAggro(ServerPlayer player) {
        this(player, 1); // 1 threat is the lowest effective threat level.
    }
    
    public PlayerAggro(ServerPlayer player, int threat) {
        this.player = player;
        this.threat = threat;
    }
    
    public ServerPlayer getPlayer() {
        return player;
    }
    
    public long getThreat() {
        return threat;
    }
    
    /**
     * Adds <code>threat</code> to the current amount of threat.  Negative
     * values will decrease threat.
     * @param threat The amount of threat to add to the current threat.
     */
    public void addThreat(long threat) {
        this.threat += threat;
    }
    
    /**
     * Sets the player's threat to <code>threat</code>.  If <code>threat</code>
     * is below 1, then the player's threat is set to 1.
     * @param threat The new threat.  Must be at least 1.
     */
    public void setThreat(long threat) {
        threat = threat < 1 ? 1 : threat;
        this.threat = threat;
    }
    
    @Override
    public int hashCode() {
        if (hashCodeValue == 0) {
            int result = 0;
            result = HashCodeUtil.hash(result, player);
            hashCodeValue = result;
        }
        
        return hashCodeValue;
    }
    
    public boolean equals(Object object) {
        if (object == null || !(object instanceof PlayerAggro)) {
            return false;
        }
        PlayerAggro other = (PlayerAggro)object;
        
        return other.player.equals(this.player);
    }
}