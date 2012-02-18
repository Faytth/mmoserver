package net;

public interface Packet {
    public byte[] getBytes();
    public Runnable getOnSend();
    public void setOnSend(Runnable onSend);
}
