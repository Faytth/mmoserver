package org.unallied.mmoserver.net.handlers;


import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.Direction;
import org.unallied.mmocraft.Velocity;
import org.unallied.mmocraft.animations.AnimationType;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.ServerPlayer;
import org.unallied.mmoserver.server.World;


public class MovementHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * A message containing [location][animationState][direction]
     * This is sent every time the player changes their direction or animation state.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        ServerPlayer p = client.getPlayer();
        if (p == null || !Authenticator.canPlayerMove(p)) {
            return;
        }
        // TODO:  Perform check to ensure that player isn't lying about their location
        // Tell the world that the player has moved
        BoundLocation location = BoundLocation.getLocation(slea);
        if (location != null && p != null) {
            World.getInstance().movePlayer(p, location);
            p.setLocation(location);
            // TODO:  Make sure player isn't lying about their current state
            p.setState(AnimationType.getState(p, p.getState(), slea.readShort()));
            p.setDirection(slea.readByte() == 0 ? Direction.RIGHT : Direction.LEFT);
            p.setVelocity(Velocity.fromBytes(slea));
            p.setFallSpeed(slea.readFloat());
            client.selectiveBroadcast(p, PacketCreator.getMovement(p));
        }
    }
}
