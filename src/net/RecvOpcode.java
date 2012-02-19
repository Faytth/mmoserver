package net;

/**
 * Opcodes used when receiving packets from the client.
 * NOTE:  This is the OPPOSITE of the client's RecvOpcode class.
 * @author Faythless
 *
 */
public enum RecvOpcode {
    PONG(0x00),
    LOGON(0x01),
    CREDS(0x02);
    private int code = 0;
    
    private RecvOpcode(int code) {
        this.code = code;
    }
    
    public int getValue() {
        return code;
    }
}
