package net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import client.Client;

public class BlockCollisionHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * A message containing 
     * This is sent every time the player successfully attacks a block.  The server
     * needs to verify which, if any, blocks the player hit as well as whether the
     * player was able to attack in their current state.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        
    }
}
