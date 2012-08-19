package net;

import org.unallied.mmocraft.Player;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.net.PacketLittleEndianWriter;
import org.unallied.mmocraft.net.RecvOpcode;

import server.ServerPlayer;
import server.World;

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
        writer.write(RecvOpcode.PING);
        return writer.getPacket();
    }
    
    /**
     * Send a welcome message to the client
     * @return the welcome packet
     */
    public static Packet getWelcome() {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        writer.write(RecvOpcode.WELCOME);
        return writer.getPacket();
    }
    
    public static Packet getChallenge(int serverNonce) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(6);
        writer.write(RecvOpcode.CHALLENGE);
        writer.writeInt(serverNonce);
        
        return writer.getPacket();
    }
    
    /**
     * Sends an error message to the client specifying that there has been
     * an error in the login process.  This terminates the client's login
     * attempt.
     * @param errorMsg The message to send to the client, such as "Invalid username."
     * @return
     */
    public static Packet getLoginError(String errorMsg) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(6);
        writer.write(RecvOpcode.LOGIN_ERROR);
        writer.writePrefixedAsciiString(errorMsg);
        
        return writer.getPacket();
    }

    /**
     * Sends a verify message to the client specifying that the server has
     * accepted the client's login.  The verify message is used by the client
     * to know that the server is authentic.
     * @param verifyHash Hash([ServerNonce].[ClientNonce].[Password])
     * @return
     */
    public static Packet getVerify(byte[] verifyHash) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(6);
        writer.write(RecvOpcode.VERIFY);
        writer.write(verifyHash);
        
        return writer.getPacket();
    }

    /**
     * A packet containing all of a player's information.  This should be sent
     * right after a player has logged in (and optionally chosen their world).
     * @param player The client's player
     * @return Packet containing all information of a client's player
     */
    public static Packet getPlayer(Player player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER);
        // TODO:  Improve this.  writeObject may be nice for most cases, but we should
        //        avoid using it because of its size and "unknown" problems.
        writer.writeObject(player);
        
        return writer.getPacket();
    }

    /**
     * A packet containing all of the blocks that make up a chunk.
     * @param chunkId The unique chunk ID that identifies this chunk
     * @return Packet containing [header][chunkId][blocks]
     */
    public static Packet getChunk(long chunkId) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.CHUNK);
        writer.writeLong(chunkId);
        writer.write(World.getInstance().getChunk(chunkId));
        
        return writer.getPacket();
    }

    /**
     * Returns a movement packet to the client, which contains player's x,y coords,
     * direction, current animation, and other movement related status.
     * @param player the player whose movement is to be "packetized."
     * @return packet
     */
    public static Packet getMovement(Player player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.MOVEMENT);
        writer.writeInt(player.getId());
        writer.writeInt(player.getDelay());
        writer.write(player.getLocation().getBytes());
        writer.writeShort(player.getState().getId().getValue());
        writer.write((byte)player.getDirection().ordinal()); // right is 0, left is 1

        return writer.getPacket();
    }

    /**
     * Returns a player disconnect packet which contains a player's ID.
     * The server sends this packet to the client when a relevant player disconnects.
     * Relevant players are nearby players, players in a party / raid / guild / friend's list
     * @param player the player who's disconnecting
     * @return packet
     */
    public static Packet getPlayerDisconnect(ServerPlayer player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER_DISCONNECT);
        writer.writeInt(player.getId());
        
        return writer.getPacket();
    }

    /**
     * Returns a registration acknowledgment packet which tells the client that their
     * registration was either accepted or denied.
     * @param accepted True if the registration was accepted.  False if denied
     * @return packet
     */
    public static Packet getRegisterAcknowledgment(boolean accepted) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.REGISTRATION_ACK);

        /*
         *  The reason for the switch in 0 and 1 is that this parameter is
         *  an "error code" parameter.  In the future, this means we can
         *  expand this to be more specific.
         */
        writer.write(accepted ? (byte)0 : (byte)1);
        
        return writer.getPacket();
    }
}
