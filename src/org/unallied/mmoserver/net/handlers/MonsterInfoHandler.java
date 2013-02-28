package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.Server;

public class MonsterInfoHandler extends AbstractServerPacketHandler {
    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 4) {
            return;
        }
        Server.getInstance().getMonsterInfo(client, slea.readInt());
    }
}
