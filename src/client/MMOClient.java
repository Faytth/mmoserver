package client;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.Packet;
import net.PacketCreator;

import org.apache.mina.core.session.IoSession;

import server.Player;
import tools.DatabaseConnection;

public class MMOClient {
    public static final String CLIENT_KEY = "CLIENT";
    
    private IoSession session;
    private Player player;
    private int accountId;
    private boolean loggedIn = false;
    private String accountName;
    private long lastPong;
    private byte loginAttempt = 0; // Stores the number of failed login attempts
    
    public MMOClient(IoSession session) {
        this.session = session;
    }
    
    /**
     * Return the IoSession associated with this connection
     * @return the IoSession for this connection
     */
    public synchronized IoSession getSession() {
        return session;
    }
    
    /**
     * Returns the player associated with this account
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }
    
    public void setPlayer(Player player) {
        this.player = player;
    }
    
    private void loadPlayer() {
        PreparedStatement ps;
        try {
            ps = DatabaseConnection.getConnection().prepareStatement(
                    "SELECT * " + 
                    "FROM account a, character c " +
                    "WHERE c.account_id = a.account_id AND " +
                    "    a.account_id = ?");
            ps.setInt(1, getAccountId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                player = new Player();
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public boolean isLoggedIn() {
        return loggedIn;
    }
    
    public void login(String username, String password) {
        
    }
    
    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }
    
    public int getAccountId() {
        return accountId;
    }
    
    public String getAccountName() {
        return accountName;
    }
    
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
    
    /**
     * Remove a player from the server
     */
    private void removePlayer() {
        
    }
    
    /**
     * Disconnects a player from the server
     */
    public final void disconnect() {
        try {
            if (player != null && isLoggedIn()) {
                removePlayer();
                player.save(true);
            }
        } finally {
            player = null;
            session.close(false);
        }
    }
    
    /**
     * Empties out the connection
     */
    public final void empty() {
        player = null;
        session = null;
    }
    
    /**
     * Update the time that the last pong was received to the current time
     */
    public void pongReceived() {
        lastPong = System.currentTimeMillis();
    }
    
    /**
     * Send a ping to the client to make sure it's still alive
     */
    public void sendPing() {
        final long pingSent = System.currentTimeMillis();
        announce(PacketCreator.getPing());
        // FIXME:  Not finished!!!
    }
    
    public void announce(Packet packet) {
        session.write(packet);
    }
}
