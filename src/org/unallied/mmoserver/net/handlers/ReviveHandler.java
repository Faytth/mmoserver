package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.ServerPlayer;

public class ReviveHandler extends AbstractServerPacketHandler {
    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() > 0) { // Guard
            return;
        }
        try {
            ServerPlayer player = client.getPlayer();
            if (!player.isAlive()) {
                player.revive();
            }
        } catch (Throwable t) {
            // Failed to revive; do nothing.
        }
    }
}
