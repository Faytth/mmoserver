package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;


public class CollisionHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * A message containing [animationID][startingIndex][endingIndex][horizontalOffset]
     * [verticalOffset].  Offsets are the offsets for the animation from the player location.
     * This is sent every time the player successfully attacks a block.  The server
     * needs to verify which, if any, blocks the player hit as well as whether the
     * player was able to attack in their current state.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 16 || Authenticator.canPlayerAttack(client.getPlayer())) { // Guard
            return;
        }
    	int startingIndex      = slea.readInt();
    	int endingIndex        = slea.readInt();
    	float horizontalOffset = slea.readFloat();
    	float verticalOffset   = slea.readFloat();
    	
    	client.getPlayer().doCollisionChecks(client.getPlayer().getState(), 
    	        startingIndex, endingIndex, horizontalOffset, verticalOffset);
    }
}
