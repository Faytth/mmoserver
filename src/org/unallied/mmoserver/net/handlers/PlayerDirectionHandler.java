package org.unallied.mmoserver.net.handlers;

import org.unallied.mmocraft.Direction;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.ServerPlayer;

public class PlayerDirectionHandler extends AbstractServerPacketHandler {
    
    @Override
    /**
     * A message containing [direction]
     * This is sent every time the player changes their direction.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        ServerPlayer p = client.getPlayer();
        if (p == null || !Authenticator.canLivingMove(p)) {
            return;
        }
        // TODO:  Make sure player isn't lying about their current state
        p.setDirection(slea.readByte() == 0 ? Direction.RIGHT : Direction.LEFT);
        client.selectiveBroadcast(p, PacketCreator.getPlayerDirection(p));
    }
}
