package database;

import org.unallied.mmocraft.BoundLocation;

import server.ServerPlayer;
import client.Client;

public class Dummy implements DatabaseAccessor {

	/**
	 * Stores the account ID so that it can increment each time getPlayer is called.
	 */
    private int accountId = 1;

	@Override
	public boolean getPlayer(Client client, String username) {
        // Set client info
        client.loginSession.setPassword("dummy");
        client.setAccountId(accountId);
        
        // Get the player's information
        ServerPlayer player = new ServerPlayer();
        player.setHpMax(100);
        player.setHpCurrent(player.getHpMax());
        player.setId(accountId++);
        player.setName("Dummy");
        player.setLocation(new BoundLocation(0, 0, 0, 0));
        
        // Assign the player to the client
        client.setPlayer(player);

        return true;
	}

	@Override
	public boolean savePlayer(ServerPlayer player) {
		return true;
	}

	@Override
	public boolean createAccount(String user, String pass, String email) {
		return true;
	}

	@Override
	public void globalLogout() {
	}

}
