package net.handlers;

import net.PacketCreator;

import org.unallied.mmocraft.gui.MessageType;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import server.ServerPlayer;

import client.Client;

public class ChatMessageHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * A message containing [type][message]
     * This is sent every time the player sends a chat message to others.  The
     * server is responsible for filling in the player's name and broadcasting
     * the message to everyone that should receive the message.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        ServerPlayer p = client.getPlayer();
        // Make the packet that will be sent
        MessageType type = MessageType.fromValue(slea.readByte());
        String message = slea.readPrefixedAsciiString();
        
        if (Authenticator.isValidChatMessage(message)) {
        	Packet packet = PacketCreator.getChatMessage(p.getName(), type, message);
        	switch (type) {
        	case SAY: // broadcast to nearby players
        		client.broadcast(p, packet);
        		break;
        	case WORLD: // broadcast to everyone
        		client.globalBroadcast(packet);
        		break;
        	default:
        		break;
        	}
        }
    }
}
