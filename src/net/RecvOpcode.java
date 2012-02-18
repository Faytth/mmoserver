package net;

/**
 * Opcodes used when receiving packets from the client.
 * NOTE:  This is the OPPOSITE of the client's RecvOpcode class.
 * @author Faythless
 *
 */
public enum RecvOpcode {
    LOGON(0x00);
    private int code = 0;
    
    private RecvOpcode(int code) {
        this.code = code;
    }
    
    public int getValue() {
        return code;
    }
}
