package constants;

import java.nio.charset.Charset;

public class ServerConstants {
    public static final String HOST = "localhost";
    public static final int PACKET_TIMEOUT = 30; // 30 seconds timeout for packets
    public static final int SERVER_PORT = 27300; // the port to listen for connections on
    public static final int HASH_SIZE = 32; // Hash shize of the password hash
    
    public static final Charset CHARSET = Charset.forName("US-ASCII"); // Default charset
}
