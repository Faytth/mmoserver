package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;


public interface ServerPacketHandler {
    
    /**
     * Handles a packet from the client.
     * @param slea The slea containing the packet.
     * @param client The client who is sending the packet.
     */
    void handlePacket(SeekableLittleEndianAccessor slea, Client client);
    
    /**
     * Retrieves whether the given client is in a state capable of sending /
     * receiving packets.
     * @param client The client to check
     * @return valid
     */
    boolean validState(Client client);
}
