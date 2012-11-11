package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;

public class PvPToggleHandler extends AbstractServerPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 1) { // Guard
            return;
        }
        boolean pvpEnable = slea.readByte() == 1;
        
        // Note:  Setting the PvP time will automagically send a packet to the client.
        if (pvpEnable) { // Player is enabling PvP
            client.getPlayer().setPvPTime(-1);
        } else if (client.getPlayer().getPvPTime() == -1) { // Player is disabling PvP
            client.getPlayer().setPvPTime(System.currentTimeMillis() + ClientConstants.PVP_FLAG_DURATION);
        }
    }
}
