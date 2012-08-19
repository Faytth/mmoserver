package net;

import java.io.IOException;

import server.Server;

import net.handlers.ServerPacketHandler;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.unallied.mmocraft.tools.PrintError;
import org.unallied.mmocraft.tools.input.ByteArrayByteStream;
import org.unallied.mmocraft.tools.input.GenericSeekableLittleEndianAccessor;
import org.unallied.mmocraft.tools.input.SeekableLittleEndianAccessor;

import client.Client;

public class MMOServerHandler extends IoHandlerAdapter {

    private PacketProcessor processor;
    
    public MMOServerHandler(PacketProcessor processor) {
        this.processor = processor;
    }
    
    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        System.out.println("Exception occurred.  Closing connection for: " + session.getRemoteAddress());
        // If client was forcibly closed
        if (cause instanceof IOException) {
            session.close(true);
        } else {
            
            session.close(true);
            PrintError.print(PrintError.EXCEPTION_CAUGHT, cause);
        }
    }

    @Override
    public void sessionOpened(IoSession session) {
        if (!Server.getInstance().isOnline()) {
            System.out.println("Server is offline.  Closing connection for: " + session.getRemoteAddress());
            session.close(true);
            return;
        }
        System.out.println("Received connection from: " + session.getRemoteAddress());
        Client client = new Client(session);
        session.write(PacketCreator.getWelcome());
        session.setAttribute(Client.CLIENT_KEY, client);
    }
    
    @Override
    public void sessionClosed(IoSession session) {
        synchronized (session) {
            Client client = (Client) session.getAttribute(Client.CLIENT_KEY);
            if (client != null) {
                try {
                    client.disconnect();
                } finally {
                    session.removeAttribute(Client.CLIENT_KEY);
                    client.empty();
                }
            }
        }
        try {
            super.sessionClosed(session);
        } catch (IOException e) {
            // Do nothing.  Client was forcibly closed.
        } catch (Throwable t) {
            PrintError.print(PrintError.EXCEPTION_CAUGHT, t);
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
        Client client = (Client) session.getAttribute(Client.CLIENT_KEY);
        
        // Get the handler for this opcode. (e.g. LOGIN, REGISTER, LOGOUT, ATTACK, ...)
        ServerPacketHandler packetHandler = processor.getHandler(packetOpcode);
        
        if (packetHandler != null && packetHandler.validState(client)) {
            try {
                packetHandler.handlePacket(slea, client);
            } catch (Throwable t) {
                // Uh oh!  We failed to handle the packet!
                t.printStackTrace();
            }
        }
    }
    
    @Override
    /**
     * Occurs when the session becomes idle.  To prevent disconnection, we initiate
     * a ping-pong.
     */
    public void sessionIdle(final IoSession session, final IdleStatus status) {
        Client client = (Client) session.getAttribute(Client.CLIENT_KEY);
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
