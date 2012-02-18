package net;

import server.Server;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

import client.MMOClient;

import constants.ServerConstants;

import server.PacketHandler;
import server.PacketProcessor;
import tools.PrintError;
import tools.input.ByteArrayByteStream;
import tools.input.GenericSeekableLittleEndianAccessor;
import tools.input.SeekableLittleEndianAccessor;

public class MMOServerHandler extends IoHandlerAdapter {

    private PacketProcessor processor;
    
    public MMOServerHandler(PacketProcessor processor) {
        this.processor = processor;
    }
    
    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        // TODO:  Make this attempt to resolve the error instead of killing the client
        session.close(true);
        PrintError.print(PrintError.EXCEPTION_CAUGHT, cause);
    }

    @Override
    public void sessionOpened(IoSession session) {
        if (!Server.getInstance().isOnline()) {
            session.close(true);
            return;
        }

        MMOClient client = new MMOClient(session);
        session.write(PacketCreator.getWelcome());
        session.setAttribute(MMOClient.CLIENT_KEY, client);
    }
    
    @Override
    public void sessionClosed(IoSession session) {
        synchronized (session) {
            MMOClient client = (MMOClient) session.getAttribute(MMOClient.CLIENT_KEY);
            if (client != null) {
                try {
                    client.disconnect();
                } finally {
                    session.removeAttribute(MMOClient.CLIENT_KEY);
                    client.empty();
                }
            }
        }
        try {
            super.sessionClosed(session);
        } catch (Exception e) {
            PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
        }
    }
    
    @Override
    /**
     * Handle a message from the client
     */
    public void messageReceived(IoSession session, Object message) {
        byte[] content = (byte[]) message;
        SeekableLittleEndianAccessor slea = 
                new GenericSeekableLittleEndianAccessor(
                        new ByteArrayByteStream(content));
        short packetOpcode = slea.readShort();
        MMOClient client = (MMOClient) session.getAttribute(MMOClient.CLIENT_KEY);
        
        // Get the handler for this opcode. (e.g. LOGIN, REGISTER, LOGOUT, ATTACK, ...)
        PacketHandler packetHandler = processor.getHandler(packetOpcode);
        
        if (packetHandler != null && packetHandler.validState(client)) {
            try {
                packetHandler.handlePacket(slea, client);
            } catch (Throwable t) {
                // Uh oh!  We failed to handle the packet!
            }
        }
    }
    
    @Override
    /**
     * Occurs when the session becomes idle.  To prevent disconnection, we initiate
     * a ping-pong.
     */
    public void sessionIdle(final IoSession session, final IdleStatus status) {
        MMOClient client = (MMOClient) session.getAttribute(MMOClient.CLIENT_KEY);
        if (client != null) {
            client.sendPing();
        }
        try {
            super.sessionIdle(session, status);
        } catch (Exception e) {
            PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
        }
    }
}
