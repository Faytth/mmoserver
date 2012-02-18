package net;

/**
 * Creates Packets to send to the client
 * @author Faythless
 *
 */
public class PacketCreator {

    private PacketCreator() {}
    
    /**
     * Ping the client (used to prevent connection timeouts)
     */
    public static Packet getPing() {
        // Create a new packet writer with 2 bytes (opcode only)
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(2);
        writer.write(SendOpcode.PING);
        return writer.getPacket();
    }
    
    /**
     * Send a welcome message to the client
     * @return the welcome packet
     */
    public static Packet getWelcome() {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        writer.write(SendOpcode.WELCOME);
        return writer.getPacket();
    }
}
