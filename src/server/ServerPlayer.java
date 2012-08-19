package server;

import org.unallied.mmocraft.Player;

import client.Client;

/**
 * A server player is a wrapper for player that contains important server data, such
 * as the player's client.
 * @author Alexandria
 *
 */
public class ServerPlayer extends Player {
    /**
     * 
     */
    private static final long serialVersionUID = -8637946755132206345L;
    private Client client = null;
    
    public Client getClient() {
        return client;
    }
    
    public void setClient(Client client) {
        this.client = client;
    }
}
