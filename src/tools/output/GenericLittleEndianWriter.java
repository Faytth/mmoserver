package tools.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import net.SendOpcode;

/**
 * Provides a generic writer for little-endian byte sequences
 * @author Faythless
 *
 */
public class GenericLittleEndianWriter implements LittleEndianWriter {

    private static final Charset ASCII = Charset.forName("US-ASCII");
    private ByteArrayOutputStream baos;
    
    public GenericLittleEndianWriter() {
        this(32);
    }
    
    public GenericLittleEndianWriter(int size) {
        baos = new ByteArrayOutputStream(size);
    }
    
    @Override
    public void write(byte b) {
        baos.write(b);
    }

    /**
     * Write an opcode to the sequence
     * @param o
     */
    @Override
    public void write(SendOpcode o) {
        writeShort(o.getValue());
    }
    
    @Override
    public void writeShort(int s) {
        baos.write((byte)((s >>> 0) & 0xFF));
        baos.write((byte)((s >>> 8) & 0xFF));
    }

    @Override
    public void writeInt(int i) {
        baos.write((byte)((i >>> 0) & 0xFF));
        baos.write((byte)((i >>> 8) & 0xFF));
        baos.write((byte)((i >>> 16) & 0xFF));
        baos.write((byte)((i >>> 24) & 0xFF));
    }

    @Override
    public void writeLong(long l) {
        baos.write((byte)((l >>> 0) & 0xFF));
        baos.write((byte)((l >>> 8) & 0xFF));
        baos.write((byte)((l >>> 16) & 0xFF));
        baos.write((byte)((l >>> 24) & 0xFF));
        baos.write((byte)((l >>> 32) & 0xFF));
        baos.write((byte)((l >>> 40) & 0xFF));
        baos.write((byte)((l >>> 48) & 0xFF));
        baos.write((byte)((l >>> 56) & 0xFF));
    }

    @Override
    public void writeAsciiString(String str) {
        write(str.getBytes(ASCII));
    }

    @Override
    public void writeNullTerminatedAsciiString(String str) {
        writeAsciiString(str);
        write((byte)0);
    }
    
    /**
     * Writes an ASCII string with the number of bytes in front.
     * The number of bytes is in a short.
     * @param str The ASCII string to write
     */
    public void writePrefixedAsciiString(String str) {
        writeShort(str.length());
        writeAsciiString(str);
    }

    /**
     * Creates a newly allocated byte array. Its size is the current size of 
     * this output stream and the valid contents of the buffer have been copied 
     * into it. 
     * @return the current contents of this output stream, as a byte array.
     */
    public byte[] toByteArray() {
        return baos.toByteArray();
    }

    @Override
    public void write(byte[] b) {
        try {
            baos.write(b);
        } catch (IOException e) {
        }
    }
}
