package org.unallied.mmoserver.net;

import java.util.Collection;

import org.iq80.snappy.Snappy;
import org.unallied.mmocraft.BlockType;
import org.unallied.mmocraft.Player;
import org.unallied.mmocraft.constants.WorldConstants;
import org.unallied.mmocraft.gui.MessageType;
import org.unallied.mmocraft.items.ItemData;
import org.unallied.mmocraft.monsters.Monster;
import org.unallied.mmocraft.net.Packet;
import org.unallied.mmocraft.net.PacketLittleEndianWriter;
import org.unallied.mmocraft.net.RecvOpcode;
import org.unallied.mmocraft.skills.SkillType;
import org.unallied.mmoserver.monsters.ServerMonster;
import org.unallied.mmoserver.server.ServerPlayer;
import org.unallied.mmoserver.server.World;


/**
 * Creates Packets to send to the client
 * @author Faythless
 *
 */
public class PacketCreator {

    private PacketCreator() {}
    
    /**
     * Ping the client (used to prevent connection timeouts)
     */
    public static Packet getPing() {
        // Create a new packet writer with 2 bytes (opcode only)
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(2);
        writer.write(RecvOpcode.PONG);
        return writer.getPacket();
    }
    
    /**
     * Send a welcome message to the client
     * @return the welcome packet
     */
    public static Packet getWelcome() {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        writer.write(RecvOpcode.WELCOME);
        return writer.getPacket();
    }
    
    public static Packet getChallenge(int serverNonce) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(6);
        writer.write(RecvOpcode.CHALLENGE);
        writer.writeInt(serverNonce);
        
        return writer.getPacket();
    }
    
    /**
     * Sends an error message to the client specifying that there has been
     * an error in the login process.  This terminates the client's login
     * attempt.
     * @param errorId The error ID to send to the client.
     * @return
     */
    public static Packet getLoginError(int errorId) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(6);
        writer.write(RecvOpcode.LOGIN_ERROR);
        writer.writeInt(errorId);
        
        return writer.getPacket();
    }

    /**
     * Sends a verify message to the client specifying that the server has
     * accepted the client's login.  The verify message is used by the client
     * to know that the server is authentic.
     * @param verifyHash Hash([ServerNonce].[ClientNonce].[Password])
     * @return
     */
    public static Packet getVerify(byte[] verifyHash) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter(6);
        writer.write(RecvOpcode.VERIFY);
        writer.write(verifyHash);
        
        return writer.getPacket();
    }

    /**
     * A packet containing all of a player's information.  This should be sent
     * right after a player has logged in (and optionally chosen their world).
     * @param player The client's player
     * @return Packet containing all information of a client's player
     */
    public static Packet getPlayer(Player player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER);
        // TODO:  Improve this.  writeObject may be nice for most cases, but we should
        //        avoid using it because of its size and "unknown" problems.
        writer.writeObject(player);
        writer.write(player.getSkills().getBytes());
        writer.write(player.getInventory().getBytes());
        
        return writer.getPacket();
    }

    /**
     * A packet containing all of the blocks that make up a chunk.
     * @param chunkId The unique chunk ID that identifies this chunk
     * @return Packet containing [header][chunkId][blocks]
     */
    public static Packet getChunk(long chunkId) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.CHUNK);
        writer.writeLong(chunkId);
        writer.write(Snappy.compress(World.getInstance().getChunk(chunkId)));
        
        return writer.getPacket();
    }

    /**
     * Returns a player movement packet to the client, which contains player's x,y coords,
     * direction, current animation, and other movement related info.
     * @param player the player whose movement is to be "packetized."
     * @return packet
     */
    public static Packet getPlayerMovement(Player player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER_MOVEMENT);
        writer.writeInt(player.getId());
        writer.write(player.getLocation().getBytes());
        writer.writeShort(player.getState().getId());
        writer.write((byte)player.getDirection().ordinal()); // right is 0, left is 1
        writer.write(player.getVelocity().getBytes());
        writer.writeFloat(player.getFallSpeed());
        writer.writeFloat(player.getInitialVelocity());

        return writer.getPacket();
    }

    /**
     * Creates a player direction packet which informs the client of a player's
     * change in the direction they're facing.
     * @param player player whose direction has changed
     * @return packet
     */
    public static Packet getPlayerDirection(ServerPlayer player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER_DIRECTION);
        writer.writeInt(player.getId());
        writer.write((byte)player.getDirection().ordinal()); // right is 0, left is 1
        
        return writer.getPacket();
    }
    
    /**
     * Returns a monster movement packet to the client, which contains monster's x,y coords,
     * direction, current animation, and other movement related info.
     * @param monster the monster whose movement is to be "packetized."
     * @return packet
     */
    public static Packet getMonsterMovement(Monster monster) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.MONSTER_MOVEMENT);
        writer.write(monster.getMovement());
        
        return writer.getPacket();
    }
    
    /**
     * Creates a player direction packet which informs the client of a monster's
     * change in the direction they're facing.
     * @param monster monster whose direction has changed
     * @return packet
     */
    public static Packet getMonsterDirection(Monster monster) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.MONSTER_DIRECTION);
        writer.writeInt(monster.getId());
        writer.write((byte)monster.getDirection().ordinal()); // right is 0, left is 1
        
        return writer.getPacket();
    }
    
    /**
     * Returns a player disconnect packet which contains a player's ID.
     * The server sends this packet to the client when a relevant player disconnects.
     * Relevant players are nearby players, players in a party / raid / guild / friend's list
     * @param player the player who's disconnecting
     * @return packet
     */
    public static Packet getPlayerDisconnect(ServerPlayer player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER_DISCONNECT);
        writer.writeInt(player.getId());
        
        return writer.getPacket();
    }

    /**
     * Returns a registration acknowledgment packet which tells the client that their
     * registration was either accepted or denied.
     * @param accepted True if the registration was accepted.  False if denied
     * @return packet
     */
    public static Packet getRegisterAcknowledgment(boolean accepted) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.REGISTRATION_ACK);

        /*
         *  The reason for the switch in 0 and 1 is that this parameter is
         *  an "error code" parameter.  In the future, this means we can
         *  expand this to be more specific.
         */
        writer.write(accepted ? (byte)0 : (byte)1);
        
        return writer.getPacket();
    }
    
    /**
     * Returns a chat message packet which tells the client who sent the message,
     * the type of the message, and what the message contains.
     * @param name Name of the person sending the chat message
     * @param type Type of the chat message to send (SAY, PARTY, GUILD, etc.)
     * @param message The actual message that the player is sending
     * @return packet
     */
	public static Packet getChatMessage(String name, MessageType type,
			String message) {
		PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
		
		writer.write(RecvOpcode.CHAT_MESSAGE);
		writer.writePrefixedAsciiString(name);
		writer.write(type.getValue());
		writer.writePrefixedAsciiString(message);
		
		return writer.getPacket();
	}
	
	/**
	 * Returns a packet containing a collection of items.  The packet is as follows:
	 * [numberOfItems] [item] [item] ...
	 * @param items
	 * @return packet
	 */
	public static Packet getItemData(Collection<ItemData> items) {
	    PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
	    
	    writer.write(RecvOpcode.ITEM_DATA);
	    writer.writeInt(items.size());
	    for (ItemData item : items) {
	        writer.write(item.getBytes());
	    }
	    
	    return writer.getPacket();
	}
	
	/**
	 * Returns a packet containing the data of an item.  The packet is as follows:
	 * [numberOfItems] [item]
	 * @param itemId The id of the item to retrieve the data for.
	 * @return packet
	 */
    public static Packet getItemData(ItemData item) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.ITEM_DATA);
        writer.writeInt(1); // only 1 item
        writer.write(item.getBytes());
        
        return writer.getPacket();
    }
	
	/**
	 * Returns a packet containing the information of a player.  The packet is as follows:
	 * [playerId] [playerData]
	 * @param player The player whose info will be written to the packet.
	 * @return packet
	 */
	public static Packet getPlayerInfo(Player player) {
	    PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
	    
	    // FIXME:  Fix this please!!! We need to provide more than just this.
	    writer.write(RecvOpcode.PLAYER_INFO);
	    writer.writeInt(player.getId());
	    writer.writeInt(player.getHpMax());
	    writer.writeInt(player.getHpCurrent());
	    writer.writeLong(player.getPvPTime());
	    writer.writePrefixedAsciiString(player.getName());
	    
	    return writer.getPacket();
	}
	
    /**
     * Returns a packet containing the information of a monster.  The packet is as follows:
     * [monsterId] [monsterData]
     * @param monster The monster whose info will be written to the packet.
     * @return packet
     */
    public static Packet getMonsterInfo(Monster monster) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.MONSTER_INFO);
        writer.write(monster.getBytes());

        return writer.getPacket();
    }
	
	/**
	 * Returns a packet containing the information of a skill.
	 * @param type
	 * @param experience
	 * @return
	 */
	public static Packet getSkillExperience(SkillType type, long experience) {
	    PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
	    
	    writer.write(RecvOpcode.SKILL_EXPERIENCE);
	    writer.write(type.getValue());
	    writer.writeLong(experience);
	    
	    return writer.getPacket();
	}
	
	/**
	 * Returns a packet containing that a block has changed at a specific (x, y)
	 * @param x
	 * @param y
	 * @param newBlockType
	 * @return
	 */
	public static Packet getBlockChanged(long x, long y, BlockType newBlockType) {
        x = x >= 0 ? x % WorldConstants.WORLD_WIDTH : WorldConstants.WORLD_WIDTH + x;
        y = y >= 0 ? y : 0;
        y = y >= WorldConstants.WORLD_HEIGHT ? WorldConstants.WORLD_HEIGHT - 1 : y;
		PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
		
		writer.write(RecvOpcode.BLOCK_CHANGED);
		writer.writeLong(x);
		writer.writeLong(y);
		writer.write(newBlockType.getValue());
		
		return writer.getPacket();
	}

	/**
	 * Creates a pong packet for a response to a client ping packet.  Helps the
	 * client know its latency and the time difference between client and server.
	 * @param clientTime Passed from the original send packet by the client.
	 *                   Contains the client's time that they sent this packet.
	 * @return packet
	 */
    public static Packet getPong(long clientTime) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PONG);
        writer.writeLong(clientTime);
        writer.writeLong(System.currentTimeMillis());
        
        return writer.getPacket();
    }
    
    /**
     * 
     * @param player The player whose PvP time is being updated.
     * @return packet
     */
    public static Packet getPvPToggleResponse(Player player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PVP_TOGGLE_RESPONSE);
        writer.writeInt(player.getId());
        writer.writeLong(player.getPvPTime());
        
        return writer.getPacket();
    }

    /**
     * Creates a PvP damaged packet which informs the client of who attacked who
     * and the amount of damage dealt.
     * @param source The source of the damage.  This is the player that attacked the other player.
     * @param damagedPlayer The damaged player.
     * @param damageDealt The amount of damage inflicted.
     * @param hpCurrent The new HP that the damaged player has.
     * @return packet
     */
    public static Packet getPvPPlayerDamaged(ServerPlayer source,
            ServerPlayer damagedPlayer, int damageDealt, int hpCurrent) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PVP_PLAYER_DAMAGED);
        writer.writeInt(source.getId());
        writer.writeInt(damagedPlayer.getId());
        writer.writeInt(damageDealt);
        writer.writeInt(hpCurrent);

        return writer.getPacket();
    }

    /**
     * Creates a monster damaged packet which informs the client of who attacked who
     * and the amount of damage dealt.
     * @param damagedPlayer The damaged player.
     * @param damageDealt The amount of damage inflicted.
     * @param hpCurrent The new HP that the damaged player has.
     * @return packet
     */
    public static Packet getPlayerDamaged(ServerPlayer damagedPlayer, 
            int damageDealt, int hpCurrent) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.PLAYER_DAMAGED);
        writer.writeInt(damagedPlayer.getId());
        writer.writeInt(damageDealt);
        writer.writeInt(hpCurrent);
        
        return writer.getPacket();
    }
    
    /**
     * Creates a monster damaged packet which informs the client of who attacked
     * the monster and the amount of damage dealt.
     * @param source The source of the damage.  This is the player that attacked the monster.
     * @param damagedMonster The damaged monster.
     * @param damageDealt The amount of damage inflicted.
     * @param hpCurrent The new HP that the damaged monster has.
     * @return packet
     */
    public static Packet getMonsterDamaged(ServerPlayer source, 
            ServerMonster damagedMonster, int damageDealt, int hpCurrent) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.MONSTER_DAMAGED);
        writer.writeInt(source == null ? -1 : source.getId());
        writer.writeInt(damagedMonster.getId());
        writer.writeInt(damageDealt);
        writer.writeInt(hpCurrent);
        
        return writer.getPacket();
    }
    
    /**
     * Creates a Set Item packet which informs the client of a new item and
     * quantity of the item. 
     * @param itemId The id of the item whose quantity we're setting.
     * @param quantity The new quantity of the item.  A quantity of 0 means
     *                 that the item should be removed from the client's 
     *                 inventory.
     * @return packet
     */
    public static Packet getSetItem(int itemId, long quantity) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.SET_ITEM);
        writer.writeInt(itemId);
        writer.writeLong(quantity);
        
        return writer.getPacket();
    }

    /**
     * Creates a Set Gold packet which informs the client of the amount of gold that
     * they now have.
     * @param gold The new amount of gold that the client has.
     * @return packet
     */
    public static Packet getSetGold(long gold) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.SET_GOLD);
        writer.writeLong(gold);
        
        return writer.getPacket();
    }

    /**
     * Creates a Revive packet which informs the player of their new location /
     * HP / state.
     * @param player The player who is being revived.
     * @return packet
     */
    public static Packet getRevive(ServerPlayer player) {
        PacketLittleEndianWriter writer = new PacketLittleEndianWriter();
        
        writer.write(RecvOpcode.REVIVE);
        writer.writeInt(player.getHpCurrent());
        writer.write(player.getLocation().getBytes());
        writer.writeShort(player.getState().getId());
        writer.write(player.getVelocity().getBytes());
        writer.writeFloat(player.getFallSpeed());
        writer.writeFloat(player.getInitialVelocity());
        
        return writer.getPacket();
    }
}
