package org.unallied.mmoserver.database;

import java.util.Collection;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.items.Item;
import org.unallied.mmocraft.items.ItemData;
import org.unallied.mmocraft.items.ItemManager;
import org.unallied.mmocraft.tools.Hasher;
import org.unallied.mmoserver.client.Client;
import org.unallied.mmoserver.server.ServerPlayer;


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
        player.setName("Dummy" + accountId);
        player.setLocation(new BoundLocation(0, 0, 0, 0));
        // Kludge:  Create player on land
        player.init();
        player.accelerateDown(100000, 10000f, 10000f);
        // Add all items in the item manager to the dummy character
        Collection<ItemData> itemData = ItemManager.getAllItemData();
        for (ItemData data : itemData) {
            try {
                player.getInventory().addItem(new Item(data.getId()), ClientConstants.MAX_ITEM_STACK);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
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
