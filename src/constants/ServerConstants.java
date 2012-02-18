package constants;

public class ServerConstants {
    public static final String HOST = "localhost";
    public static final String DB_URL = "jdbc:mysql://localhost:/var/lib/mysql/mysql.sock/admin_daemon?autoReconnect=true";
    public static final String DB_USER = "admin_mmocraft";
    public static final String DB_PASS = "oq1rvn7t0";
    public static final int PACKET_TIMEOUT = 30; // 30 seconds timeout for packets
    public static final int SERVER_PORT = 27300; // the port to listen for connections on
}
