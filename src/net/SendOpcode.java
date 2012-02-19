package net;

/**
 * Opcodes used when sending packets to the client.
 * NOTE:  This is the OPPOSITE of the client's SendOpcode class.
 * @author Faythless
 *
 */
public enum SendOpcode {
    PING(0x00), 
    WELCOME(0x01),
    CHALLENGE(0x02),
    VERIFY(0x03);
    private int code = 0;
    
    private SendOpcode(int code) {
        this.code = code;
    }
    
    public int getValue() {
        return code;
    }
}
