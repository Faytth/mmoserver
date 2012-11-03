package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.Server;

/**
 * Handles a request from the client asking for information about a player.
 * @author Alexandria
 *
 */
public class PlayerInfoHandler extends AbstractServerPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 4) {
            return;
        }
        Server.getInstance().getPlayerInfo(client, slea.readInt());
    }
}
