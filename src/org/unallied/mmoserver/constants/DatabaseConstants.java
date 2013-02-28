package org.unallied.mmoserver.constants;

/**
 * Note that not all variables in this class are true constants.  This is
 * because these variables can be optionally loaded from a server configuration
 * file as described in ServerConstants.
 * @author Alexandria
 *
 */
public abstract class DatabaseConstants {
//  public static final String DB_URL = "jdbc:mysql://localhost:/var/lib/mysql/mysql.sock/admin_daemon?autoReconnect=true";
  public static String DB_URL = "jdbc:mysql://localhost:3306/mmocraft?autoReconnect=true";
  public static String DB_USER = "root";
  public static String DB_PASS = "oq1rvn7t0";
  
  /** The latest character version used when saving player data. */
  public static final short DB_CHARACTER_VERSION = 1;
}
