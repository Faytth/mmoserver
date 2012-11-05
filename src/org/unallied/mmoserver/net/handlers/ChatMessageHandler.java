package org.unallied.mmoserver.net.handlers;


import org.unallied.mmocraft.gui.MessageType;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.skills.SkillType;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.net.PacketCreator;
import org.unallied.mmoserver.server.ServerPlayer;



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
            p.addExperience(SkillType.CONSTITUTION, message.length());
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
