package net.handlers;

import java.util.Arrays;

import org.unallied.mmocraft.Player;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.tools.Hasher;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import net.PacketCreator;
import client.Client;
import server.Server;
import server.ServerPlayer;

public class CredsHandler extends AbstractServerPacketHandler {
    
    @Override
    /**
     * A message containing a [clientNonce][hash(clientNonce.serverNonce.password)]
     * This is sent at the very end of the login authentication.  It is also
     * safe to tell the client about who they are at this point.
     * @param slea The packet
     * @param client The client whose session we're setting
     */
    public void handlePacket(SeekableLittleEndianAccessor slea, Client client) {
        int clientNonce = slea.readInt();
        byte[] hash = slea.read(ClientConstants.HASH_SIZE);
        client.loginSession.setClientNonce(clientNonce);
        
        // Check to make sure that this client knows the password
        byte[] computedHash = Hasher.getSHA256(
                (client.loginSession.getClientNonce()
                + client.loginSession.getServerNonce()
                + client.loginSession.getPassword()).getBytes(
                        ClientConstants.CHARSET) );
        
        // Make sure the client knows the password
        if (computedHash != null && Arrays.equals(hash, computedHash)) {
            byte[] verifyHash = Hasher.getSHA256(
                    (client.loginSession.getServerNonce()
                    + client.loginSession.getClientNonce()
                    + client.loginSession.getPassword()).getBytes(
                            ClientConstants.CHARSET) );
                    
            // Send verify
            client.announce(PacketCreator.getVerify(verifyHash) );
            
            // Log out any clients who are using this account
            Server.getInstance().logout(client);
            
            // Add the player to the player pool
            Server.getInstance().addPlayer(client.getPlayer());
            
            // Tell the client that it's now logged in
            client.setLoggedIn(true);
            
            // Tell the client who they are
            // TODO:  Remove this code and instead add proper serialization for player
            ServerPlayer sp = client.getPlayer();
            Player p = new Player();
            p.setDelay(sp.getDelay());
            p.setDirection(sp.getDirection());
            p.setHpCurrent(sp.getHpCurrent());
            p.setHpMax(sp.getHpMax());
            p.setId(sp.getId());
            p.setLocation(sp.getLocation());
            p.setName(sp.getName());
            p.setState(sp.getState());
            p.setInventory(sp.getInventory());
            client.announce(PacketCreator.getItemData(p.getInventory().getItemData()));
            client.announce(PacketCreator.getPlayer(p));
        } else {
            // The client doesn't know the password, or there has been an error
            client.announce(PacketCreator.getLoginError(
                    "Wrong password.") );
        }
    }

}
