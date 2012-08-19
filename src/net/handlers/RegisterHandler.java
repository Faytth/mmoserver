package net.handlers;

import net.PacketCreator;

import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import client.Client;
import database.MySQL;

public class RegisterHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * A message containing [user][pass][email]
     * This is sent every time the player changes their direction or animation state.
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        String user = slea.readPrefixedAsciiString();
        String pass = slea.readPrefixedAsciiString();
        String email = slea.readPrefixedAsciiString();
        boolean accepted = MySQL.createAccount(user, pass, email);
        
        // Tell client that we received their registration request, and that they can now log in
        client.announce(PacketCreator.getRegisterAcknowledgment(accepted));
    }
}
