package net.sessions;

/**
 * Contains important information about client / server nonces and other
 * information related to a single instance of logging in.
 * @author Faythless
 *
 */
public class LoginSession {
    //private int loginAttempts = 0;
    private String username = "";
    private String password = "";
    private int clientNonce; // Nonce created by the client
    private int serverNonce; // Nonce created by the server

    public String getUsername() {
        return username;
    }
    
    /**
     * Sets the username for the login session
     * @param username account username
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * Retrieve the account password for this client
     * @return password
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * Set the account password for this client
     * @param password new account password
     */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * Get the nonce created by the client
     * @return clientNonce
     */
    public int getClientNonce() {
        return clientNonce;
    }
    
    /**
     * Set the client nonce.
     * @param clientNonce New client nonce
     */
    public void setClientNonce(int clientNonce) {
        this.clientNonce = clientNonce;
    }
    
    /**
     * Get the server nonce created by the server
     * @return serverNonce
     */
    public int getServerNonce() {
        return serverNonce;
    }
    
    /**
     * Set the server nonce to a new nonce
     * @param serverNonce new server nonce
     */
    public void setServerNonce(int serverNonce) {
        this.serverNonce = serverNonce;
    }
}
