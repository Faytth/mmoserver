package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.ServerPlayer;

public class PlaceBlockHandler extends AbstractServerPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 12) { // Guard
            return;
        }
        try {
            int itemId = slea.readInt();
            int x = slea.readInt();
            int y = slea.readInt();
            BoundLocation blockLocation = new BoundLocation(x, y, 0, 0);
            ServerPlayer player = client.getPlayer();
            
            if (player != null) {
                synchronized (player) {
                    // Perform check to ensure player can place the block
                    if (Authenticator.canPlaceBlock(player, blockLocation)) {
                        player.placeBlock(itemId, blockLocation);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
