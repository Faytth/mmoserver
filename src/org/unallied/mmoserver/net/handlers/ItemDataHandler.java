package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.items.ItemManager;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;

/**
 * Received when the client is requesting information about an item from the
 * server.
 * @author Alexandria
 *
 */
public class ItemDataHandler extends AbstractServerPacketHandler {
    
    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        if (slea.available() != 4) { // Guard
            return;
        }
        int itemId = slea.readInt();
        
        // Make sure the client is able to request this item
        if (client.getPlayer().getInventory().getQuantity(itemId) > 0) {
            client.announce(PacketCreator.getItemData(ItemManager.getItemData(itemId)));
        }
    }
}
