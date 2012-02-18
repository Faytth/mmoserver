package server;

import client.MMOClient;
import tools.input.SeekableLittleEndianAccessor;

public interface PacketHandler {
    void handlePacket(SeekableLittleEndianAccessor slea, MMOClient client);
    boolean validState(MMOClient client);
}
