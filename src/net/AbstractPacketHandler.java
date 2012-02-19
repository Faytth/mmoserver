package net;

import client.MMOClient;


public abstract class AbstractPacketHandler implements PacketHandler {
    @Override
    public boolean validState(MMOClient client) {
        return true; //client.isLoggedIn();
    }
}
