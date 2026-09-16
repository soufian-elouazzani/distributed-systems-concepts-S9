package com.channel.system;

public class CircularBuffer {
    private final byte[] buffer;
    private int head = 0;
    private int tail = 0;
    private int count = 0;
    private boolean closed = false;

    public CircularBuffer(int capacity) {
        this.buffer = new byte[capacity];
    }

    public synchronized int write(byte[] src, int off, int len) throws InterruptedException {
        while (count == buffer.length && !closed) {
            wait(); // Block when buffer is full
        }
        if (closed) return -1;

        int bytesWritten = 0;
        while (bytesWritten < len && count < buffer.length) {
            buffer[tail] = src[off + bytesWritten];
            tail = (tail + 1) % buffer.length;
            count++;
            bytesWritten++;
        }
        notifyAll(); // Signal reading threads
        return bytesWritten;
    }

    public synchronized int read(byte[] dest, int off, int len) throws InterruptedException {
        while (count == 0 && !closed) {
            wait(); // Block when buffer is empty
        }
        if (count == 0 && closed) return -1; // End of Stream (Demi-disconnect complete)

        int bytesRead = 0;
        while (bytesRead < len && count > 0) {
            dest[off + bytesRead] = buffer[head];
            head = (head + 1) % buffer.length;
            count--;
            bytesRead++;
        }
        notifyAll(); // Signal writing threads
        return bytesRead;
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}