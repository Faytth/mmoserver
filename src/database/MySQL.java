package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.unallied.mmocraft.BoundLocation;
import org.unallied.mmocraft.tools.Authenticator;
import org.unallied.mmocraft.tools.DatabaseConnection;
import org.unallied.mmocraft.tools.PrintError;

import server.ServerPlayer;

import client.Client;

public class MySQL {
    
    /**
     * Attempts to populate the player's data in the client provided.
     * @param client the client to be associated with the username
     * @param username the username to grab from the database
     * @return true on success; false on failure
     */
    public static boolean getPlayer(Client client, String username) {
        boolean result = true;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * " +
                    "FROM account " +
                    "WHERE LOWER(account_user)=LOWER(?)");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            
            // Only set the password if there's a password to set
            if( rs != null && rs.next() ) {
                int accountId = rs.getInt("account_id");
                String password = rs.getString("account_pass");
                String playerName = rs.getString("player_name");
                long playerPosX = rs.getLong("player_posx");
                long playerPosY = rs.getLong("player_posy");
                //long playerGold = rs.getLong("player_gold");
                
                // Set client info
                client.loginSession.setPassword(password);
                client.setAccountId(accountId);
                
                // Get the player's information
                ServerPlayer player = new ServerPlayer();
                player.setHpMax(100);
                player.setHpCurrent(player.getHpMax());
                player.setId(accountId);
                player.setName(playerName);
                player.setLocation(new BoundLocation(playerPosX, playerPosY, 0, 0));
                
                // Assign the player to the client
                client.setPlayer(player);
            } else {
                result = false;
            }
        } catch(SQLException e) {
            result = false;
        }
        return result;
    }

    /**
     * Saves a player's information in the database.  This should be called
     * periodically for all players to prevent exploits.
     * @param player The player to add to the database
     * @return true on success; false if failed
     */
    public static boolean savePlayer(ServerPlayer player) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            int index = 1;
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE account " +
                    "SET player_name=?, player_posx=?, player_posy=?" +
                    "WHERE account_id=?");
            ps.setString(index++, player.getName());
            ps.setLong(index++, player.getLocation().getX());
            ps.setLong(index++, player.getLocation().getY());
            ps.setInt(index++, player.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
            return false;
        }
        return true;
    }

    /**
     * Creates a new account.
     * @param user
     * @param pass
     * @param email
     * @return 
     */
    public static boolean createAccount(String user, String pass, String email) {
        // Make sure user, pass, and email are all valid
        if (Authenticator.isValidUser(user) && Authenticator.isValidPass(pass) && 
                Authenticator.isValidEmail(email)) {
            Connection conn = DatabaseConnection.getConnection();
            try {
                int index = 1;
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO account(account_user,player_name,account_pass,account_email) VALUES(?,?,?,?)");
                ps.setString(index++, user);
                ps.setString(index++, user);
                ps.setString(index++, pass);
                ps.setString(index++, email);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                PrintError.print(PrintError.EXCEPTION_CAUGHT, e);
            }
        }
        return false;
    }
}
