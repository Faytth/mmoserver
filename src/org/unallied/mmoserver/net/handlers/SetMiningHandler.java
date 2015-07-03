package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.ServerPlayer;

public class SetMiningHandler extends AbstractServerPacketHandler {
    
    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 9) { // Guard
            return;
        }
        try {
            int x = slea.readInt();
            int y = slea.readInt();
            boolean isDown = slea.readByte() == 1;
            BoundLocation blockLocation = new BoundLocation(x, y, 0, 0);
            ServerPlayer player = client.getPlayer();
            
            if (player != null) {
                synchronized (player) {
                    // Perform check to ensure player can place the block
                    if (Authenticator.canMineBlock(player, blockLocation)) {
                        if (isDown) {
                            player.startMining(blockLocation);
                        } else { // Stop mining
                            player.stopMining();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
