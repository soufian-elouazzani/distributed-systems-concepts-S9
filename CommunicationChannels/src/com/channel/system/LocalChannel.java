package com.channel.system;

public class LocalChannel extends Channel {
    private final CircularBuffer in;
    private final CircularBuffer out;
    private volatile boolean isDisconnected = false;

    public LocalChannel(CircularBuffer in, CircularBuffer out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) {
        try {
            return in.read(bytes, offset, length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public int write(byte[] bytes, int offset, int length) {
        try {
            return out.write(bytes, offset, length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public void disconnect() {
        if (!isDisconnected) {
            isDisconnected = true;
            out.close(); // Half-close: stop writing, let remote finish reading remaining bytes
        }
    }

    @Override
    public boolean disconnected() {
        return isDisconnected;
    }
}