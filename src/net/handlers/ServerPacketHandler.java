package net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import client.Client;

public interface ServerPacketHandler {
    void handlePacket(SeekableLittleEndianAccessor slea, Client client);
    boolean validState(Client client);
}
