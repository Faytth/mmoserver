package org.unallied.mmoserver.constants;

public class ServerConstants {
    /** 
     * The server configuration file which is used to assign variables such as
     * database access.
     */
    public static final String SERVER_CONF_FILE = "server_conf.properties";
    
    /** The configuration file key for the database URL. */
    public static final String CONF_DB_URL  = "DB_URL";
    /** The configuration file key for the database username. */
    public static final String CONF_DB_USER = "DB_USER";
    /** The configuration file key for the database password. */
    public static final String CONF_DB_PASS = "DB_PASS";
    
    /** The distance in pixels that an object can be from its target before it is removed. */
    public static final double OBJECT_DESPAWN_DISTANCE = 2500;

    /** This is the time in milliseconds that the server waits between global character saves. */
    public static final long SAVE_ALL_CHARACTERS_FREQUENCY = 60000;

    /** 
     * The multiplier on physical damage for monster threat.  Threat is used to determine
     * who has aggro on the monster.
     */
    public static final float MONSTER_THREAT_DAMAGE_MULTIPLIER = 1.5f;

    /** The minimum distance in pixels that a monster is allowed to spawn near the player. */
    public static final int MONSTER_SPAWNER_MIN_DISTANCE = 400;
    
    /** 
     * The added distance in pixels that a monster is allowed to spawn near a player.  The
     * maximum distance in pixels is {@link #MONSTER_SPAWNER_MIN_DISTANCE} + {@link #MONSTER_SPAWNER_DISTANCE}.
     */
    public static final int MONSTER_SPAWNER_DISTANCE = 500;
}
