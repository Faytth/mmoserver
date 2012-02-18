package net.handlers;

import client.MMOClient;
import server.AbstractPacketHandler;
import tools.input.SeekableLittleEndianAccessor;

public class LogonHandler extends AbstractPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MMOClient client) {
        System.out.println("We received a logon packet from the client");
    }
}
