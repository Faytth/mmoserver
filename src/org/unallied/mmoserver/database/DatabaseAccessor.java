package org.unallied.mmoserver.database;

import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.ServerPlayer;


public interface DatabaseAccessor {
    /**
     * Attempts to populate the player's data in the client provided.
     * @param client the client to be associated with the username
     * @param username the username to grab from the database
     * @return true on success; false on failure
     */
    public boolean getPlayer(Client client, String username);

    /**
     * Saves a player's information in the database.  This should be called
     * periodically for all players to prevent exploits.
     * @param player The player to add to the database
     * @return true on success; false if failed
     */
    public boolean savePlayer(ServerPlayer player);

    /**
     * Creates a new account.
     * @param user
     * @param pass
     * @param email
     * @return 
     */
    public boolean createAccount(String user, String pass, String email);

    /*
     * Logs everyone out of the database.  Sets "logged_in" to false.
     */
	public void globalLogout();
}
