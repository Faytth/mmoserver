package org.unallied.mmoserver.monsters;

import java.util.HashMap;
import java.util.Map;

import org.unallied.mmocraft.Passive;
import org.unallied.mmocraft.PassiveType;
import org.unallied.mmocraft.animations.AnimationType;
import org.unallied.mmocraft.monsters.MonsterData;
import org.unallied.mmocraft.monsters.MonsterType;
import org.unallied.mmocraft.tools.input.GenericSeekableLittleEndianAccessor;
import org.unallied.mmocraft.tools.output.GenericLittleEndianWriter;
import org.unallied.mmoserver.ai.AI;
import org.unallied.mmoserver.constants.ServerConstants;
import org.unallied.mmoserver.loot.Loot;

public class ServerMonsterData implements MonsterData {

    /** The monster's id.  This should be unique for each monster. */
    protected final int id;
    
    /** The monster's name. */
    protected final String name;
    
    /** The HP of the monster. */
    protected final int maxHP;
    
    /** The experience points of the monster. */
    protected final int experience;
    
    /** The minimum gold obtainable from this monster on death. */
    protected final int minimumGold;
    
    /** The maximum gold obtainable from this monster on death. */
    protected final int maximumGold;
    
    /** The monster's level. */
    protected final short level;
    
    /** The width of the monster's hit box. */
    protected final short width;
    
    /** The height of the monster's hit box. */
    protected final short height;
    
    /** The multiplier to use for all of the monster's attacks. */
    protected final double damageMultiplier;
    
    /** The movement speed, where 1f is the player's default speed. */
    protected final float movementSpeed;
    
    /** The monster's type (e.g. Ground, Flying). */
    protected final MonsterType type;
    
    /** The AI of this monster. */
    protected final AI ai;
    
    /** The animations that the monster has (e.g. idle, jump). */
    protected Map<AnimationType, String> animations = 
            new HashMap<AnimationType, String>();
    
    protected Loot loot = new Loot();
    
    /** The monster's passives (e.g. armor, resist). */
    protected Map<PassiveType, Passive> passives = 
            new HashMap<PassiveType, Passive>();
    
    public ServerMonsterData(final int id, final String name, final int maxHP,
            final int experience, final int minimumGold, final int maximumGold,
            final short level, final short width, final short height, 
            final double damageMultiplier, final float movementSpeed,
            final MonsterType type, final AI ai) {
        this.id = id;
        this.name = name;
        this.maxHP = maxHP;
        this.experience = experience;
        this.minimumGold = minimumGold;
        this.maximumGold = maximumGold;
        this.level = level;
        this.width = width;
        this.height = height;
        this.damageMultiplier = damageMultiplier;
        this.movementSpeed = movementSpeed;
        this.type = type;
        this.ai = ai;
    }
    
    /**
     * Serializes the bytes for this class.  This is used in place of
     * writeObject / readObject because Java adds a lot of "extra"
     * stuff that isn't particularly useful in this case.
     * 
     * @return byteArray
     */
    public byte[] getBytes() {
        GenericLittleEndianWriter writer = new GenericLittleEndianWriter();
        
        writer.writeInt(id);
        writer.write7BitPrefixedAsciiString(name);
        writer.writeInt(maxHP);
        writer.writeInt(experience);
        writer.writeInt(minimumGold);
        writer.writeInt(maximumGold);
        writer.writeShort(level);
        writer.writeDouble(damageMultiplier);
        writer.writeFloat(movementSpeed);
        writer.write(type.getValue());
        writer.write7BitPrefixedAsciiString(ai.getClass().getName());
        
        // Write animations
        writer.write((byte)animations.size());
        for (Map.Entry<AnimationType, String> animation : animations.entrySet()) {
            writer.writeShort(animation.getKey().getValue());
            // TODO:  If the client and server disagree on the resource location, this will mess up.
            writer.write7BitPrefixedAsciiString(animation.getValue());
        }
        
        // Write passives
        writer.write((byte)passives.size());
        for (Passive passive : passives.values()) {
            writer.write(passive.getBytes());
        }
        
        return writer.toByteArray();
    }

    /**
     * Retrieves this class from an SLEA, which contains the raw bytes of this class
     * obtained from the getBytes() method.
     * @param slea A seekable little endian accessor that is currently at the position
     *             containing the bytes of a MonsterData object.
     * @return monsterData
     */
    @SuppressWarnings("rawtypes")
    public static MonsterData fromBytes(GenericSeekableLittleEndianAccessor slea) {
        try {
            if (slea.available() <= 0) {
                return null;
            }
            int id = slea.readInt();
            String name = slea.read7BitPrefixedAsciiString();
            int maxHP = slea.readInt();
            int experience = slea.readInt();
            int minimumGold = slea.readInt();
            int maximumGold = slea.readInt();
            short level = slea.readShort();
            short width = slea.readShort();
            short height = slea.readShort();
            double damageMultiplier = slea.readDouble();
            float movementSpeed = slea.readFloat();
            MonsterType monsterType = MonsterType.fromValue(slea.readByte());
            Class c = Class.forName(slea.read7BitPrefixedAsciiString());
            AI ai;
            if (c != null) {
                ai = (AI)c.newInstance();
            } else {
                throw new ClassNotFoundException("Class is not an extension of AI.");
            }
            MonsterData result = new ServerMonsterData(id, name, maxHP, 
                    experience, minimumGold, maximumGold, level, width, height,
                    damageMultiplier, movementSpeed, monsterType, ai);
            
            // Read animations
            int count = slea.readByte();
            for (int i=0; i < count; ++i) {
                AnimationType type = AnimationType.fromValue(slea.readShort());
                String animationLocation = ServerConstants.SERVER_RESOURCE_ANIMATION_LOCATION + slea.read7BitPrefixedAsciiString();
                result.getAnimations().put(type, animationLocation);
            }
            // Add any animations that must be present but are missing
            try {
                if (!result.getAnimations().containsKey(AnimationType.WALK)) {
                    result.getAnimations().put(AnimationType.WALK, result.getAnimations().get(AnimationType.IDLE));
                }
                if (!result.getAnimations().containsKey(AnimationType.JUMP)) {
                    result.getAnimations().put(AnimationType.JUMP, result.getAnimations().get(AnimationType.IDLE));
                }
                if (!result.getAnimations().containsKey(AnimationType.RUN)) {
                    result.getAnimations().put(AnimationType.RUN, result.getAnimations().get(AnimationType.WALK)); 
                }
                if (!result.getAnimations().containsKey(AnimationType.SHIELD)) {
                    result.getAnimations().put(AnimationType.SHIELD, result.getAnimations().get(AnimationType.IDLE));
                }
                if (!result.getAnimations().containsKey(AnimationType.FALL)) {
                    result.getAnimations().put(AnimationType.FALL, result.getAnimations().get(AnimationType.JUMP));
                }
                if (!result.getAnimations().containsKey(AnimationType.DEAD)) {
                    result.getAnimations().put(AnimationType.DEAD, result.getAnimations().get(AnimationType.IDLE));
                }
            } catch (Throwable t) {
                System.err.println("Error populating monster's animations for monster data ID: " + id + ".\n" +
                        "Note:  All monsters MUST have an IDLE animation.");
                t.printStackTrace();
            }
            
            // Read loot
            count = slea.readShort();
            for (int i=0; i < count; ++i) {
                result.getLoot().addItem(slea);
            }
            
            // Read passives
            count = slea.readByte();
            for (int i=0; i < count; ++i) {
                Passive passive = Passive.fromBytes(slea);
                result.getPassives().put(passive.getType(), passive);
            }
            
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Retrieves the monster data id.
     * @return dataId
     */
    public Integer getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public int getMaxHP() {
        return maxHP;
    }
    
    public short getLevel() {
        return level;
    }
    
    public double getDamageMultiplier() {
        return damageMultiplier;
    }
    
    public MonsterType getType() {
        return type;
    }
    
    public Map<AnimationType, String> getAnimations() {
        return animations;
    }
    
    public Map<PassiveType, Passive> getPassives() {
        return passives;
    }

    public int getExperience() {
        return experience;
    }
    
    public int getMinimumGold() {
        return minimumGold;
    }
    
    public int getMaximumGold() {
        return maximumGold;
    }
    
    public Loot getLoot() {
        return loot;
    }
    
    public AI getAI() {
        return ai;
    }

    @Override
    public short getWidth() {
        return width;
    }

    @Override
    public short getHeight() {
        return height;
    }
    
    @Override
    public float getMovementSpeed() {
        return movementSpeed;
    }
}
