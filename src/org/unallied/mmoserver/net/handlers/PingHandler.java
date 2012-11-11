package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;

public class PingHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * Sent from the client when they want an update on the latency between
     * the client and the server.  The client should respond with a new packet
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 8) {
            return;
        }
        client.announce(PacketCreator.getPong(slea.readLong()));
    }
}
