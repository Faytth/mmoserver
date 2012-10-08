package org.unallied.mmoserver.net.handlers;

import org.unallied.mmoserver.client.Client;

public abstract class AbstractServerPacketHandler implements ServerPacketHandler {
    @Override
    public boolean validState(Client client) {
        return true; //client.isLoggedIn();
    }
}
