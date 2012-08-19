package net.handlers;

import java.security.SecureRandom;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import net.PacketCreator;
import client.Client;
import database.MySQL;

public class LogonHandler extends AbstractServerPacketHandler {

    @Override
    /**
     * Sets a username / password for a client session.  If password == null
     * after calling this function, it means no user exists with that password.
     * @param slea The packet
     * @param client The client whose session we're setting
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        // Get a new server nonce for this logon session
        int serverNonce = new SecureRandom().nextInt();
        client.loginSession.setServerNonce(serverNonce);
        
        String username = slea.readPrefixedAsciiString();
        client.loginSession.setUsername(username);
        
        if (MySQL.getPlayer(client, username)) {
            // Tell client a challenge message
            client.announce(PacketCreator.getChallenge(serverNonce));
        } else {
            // Tell client that the login process failed
            client.announce(PacketCreator.getLoginError("Invalid username."));
        }
    }
}
