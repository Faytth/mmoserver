package net.handlers;

import client.Client;

public abstract class AbstractServerPacketHandler implements ServerPacketHandler {
    @Override
    public boolean validState(Client client) {
        return true; //client.isLoggedIn();
    }
}
