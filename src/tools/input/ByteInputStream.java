package tools.input;

public interface ByteInputStream {
    int readByte();
    long getBytesRead();
    long available();
}
