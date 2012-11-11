package org.unallied.mmoserver.net;

import java.util.HashMap;
import java.util.Map;

import org.unallied.mmocraft.net.SendOpcode;
import org.unallied.mmoserver.net.handlers.*;


/**
 * This class is used to perform callbacks to specific packet handlers
 * based on the received opcode.
 * Game packets are of the format:
 * [Length] [Header] [Payload]
 * Length is 4 bytes, header is 2 bytes
 * @author Faythless
 *
 */
public class PacketProcessor {
    
    private Map<Short, ServerPacketHandler> handlers = new HashMap<Short, ServerPacketHandler>();
    
    private PacketProcessor() {
        init();
    }
    
    private static class PacketProcessorHolder {
        public static final PacketProcessor instance = new PacketProcessor();
    }
    
    private void init() {
        reset();
    }
    
    public static PacketProcessor getInstance() {
        return PacketProcessorHolder.instance;
    }

    /**
     * Returns a handler for a given opcode
     * @param packetOpcode The opcode to return the handler for
     * @return handler for opcode if found; else null
     */
    public ServerPacketHandler getHandler(short packetOpcode) {
        return handlers.get(packetOpcode);
    }
    
    /**
     * Registers a handler with the processor.  Opcodes must be unique
     * @param opcode Unique opcode that identifies a handler
     * @param handler A handler that receives events when this opcode is received
     */
    public void registerHandler(SendOpcode opcode, AbstractServerPacketHandler handler) {
        handlers.put((short)opcode.getValue(), handler);
    }
    
    /**
     * Resets all handlers to the default
     */
    public void reset() {
        handlers.clear();
        
        registerHandler(SendOpcode.PING, new PingHandler());
        registerHandler(SendOpcode.LOGON, new LogonHandler());
        registerHandler(SendOpcode.CREDS, new CredsHandler());
        registerHandler(SendOpcode.CHUNK, new ChunkHandler());
        registerHandler(SendOpcode.MOVEMENT, new MovementHandler());
        registerHandler(SendOpcode.REGISTER, new RegisterHandler());
        registerHandler(SendOpcode.CHAT_MESSAGE, new ChatMessageHandler());
        registerHandler(SendOpcode.PLAYER_INFO, new PlayerInfoHandler());
        registerHandler(SendOpcode.BLOCK_COLLISION, new CollisionHandler());
        registerHandler(SendOpcode.PVP_TOGGLE, new PvPToggleHandler());
    }
}
