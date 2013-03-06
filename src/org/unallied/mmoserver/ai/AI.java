package org.unallied.mmoserver.ai;

import org.unallied.mmocraft.ControlIntf;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.server.ServerPlayer;

/**
 * Artificial Intelligence.  Used primarily for monsters.
 * @author Alexandria
 *
 */
public interface AI extends ControlIntf {
    /**
     * Updates the AI logic, where delta is the length of time in milliseconds
     * that has passed since the last call to update.  The AI is responsible
     * for issuing "commands" to the monster in much the same way a person would
     * issue commands to their character through use of a keyboard or controller.
     * @param delta The time in milliseconds that has transpired since the last call.
     */
    public void update(long delta);
    
    /**
     * Sets the monster for this AI.  This is needed so that the AI can perform
     * calculations based on monster health, animation state, and so on and so
     * forth.
     * @param monster The monster to set for this AI.
     */
    public void setMonster(ServerMonster monster);
    
    /**
     * Called when the monster is damaged.
     * @param player The player doing the damage.
     * @param threatAmount The amount of threat generated by the damage.
     */
    public void damaged(ServerPlayer player, int threatAmount);
    
    /**
     * Called when a player is healed.  If the healee is on this monster's aggro
     * list, then the healer will be placed on the aggro list as well.
     * @param healer The person doing the healing.
     * @param healee The person receiving the healing.
     * @param threatAmount The amount of threat that the healer generates from the heal.
     */
    public void healed(ServerPlayer healer, ServerPlayer healee, int threatAmount);
}