package database;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.tools.Hasher;

import server.ServerPlayer;
import client.Client;

public class DummyDatabase implements DatabaseAccessor {

	/**
	 * Stores the account ID so that it can increment each time getPlayer is called.
	 */
    private int accountId = 1;

	@Override
	public boolean getPlayer(Client client, String username) {
        // Set client info
		String pass = "dummy";
		String user = "dummy";
		
		// We have to do this because the password is supposed to be hashed
		byte[] byteData = Hasher.getSHA256((user + pass).getBytes());
        StringBuffer sb = new StringBuffer();
        for (int i=0; i < byteData.length; ++i) {
            sb.append(Integer.toString((byteData[i] & 0xFF) + 0x100, 16).substring(1));
        }
        pass = sb.toString();
        
        client.loginSession.setPassword(pass);
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
