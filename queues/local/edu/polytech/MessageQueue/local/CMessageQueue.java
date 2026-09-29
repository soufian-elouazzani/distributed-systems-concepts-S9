package edu.polytech.MessageQueue.local;

import edu.polytech.queues.MessageQueue;
import edu.polytech.queues.QueueBroker;


import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CMessageQueue implements MessageQueue {
    private final QueueBroker ownerBroker;
    private CMessageQueue peer;
    
    private Listener listener;
    private CTask ownerTask;

    private final ConcurrentLinkedQueue<byte[]> messageQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isClosed = false;

    public CMessageQueue(QueueBroker ownerBroker, CTask ownerTask) {
        this.ownerBroker = ownerBroker;
        this.ownerTask = ownerTask;
    }

    public void setPeer(CMessageQueue peer) {
        this.peer = peer;
    }

    @Override
    public QueueBroker broker() {
        return ownerBroker;
    }

    @Override
    public void setListener(Listener l) {
        this.listener = l;
        this.ownerTask = (CTask) CTask.task(); // Bind callback notifications to current setting task
        drainQueue();
    }

    @Override
    public boolean send(byte[] bytes, int offset, int length, SendListener l) {
        if (offset < 0 || (offset + length) > bytes.length) {
            throw new IllegalArgumentException("Invalid range");
        }

        if (isClosed || peer.isClosed) {
            // Dropped message ownership returned immediately
            if (l != null && ownerTask != null) {
                ownerTask.post(() -> l.sent(bytes, offset, length));
            }
            return false;
        }

        byte[] payload = Arrays.copyOfRange(bytes, offset, offset + length);
        peer.enqueueIncoming(payload);

        // Notify sender that buffer ownership is returned
        if (l != null && ownerTask != null) {
            ownerTask.post(() -> l.sent(bytes, offset, length));
        }

        return true;
    }

    private void enqueueIncoming(byte[] payload) {
        messageQueue.offer(payload);
        drainQueue();
    }

    private void drainQueue() {
        if (listener == null || ownerTask == null) return;

        ownerTask.post(() -> {
            while (!messageQueue.isEmpty()) {
                byte[] msg = messageQueue.poll();
                if (msg != null && listener != null) {
                    listener.received(msg);
                }
            }
            if (isClosed && messageQueue.isEmpty() && listener != null) {
                listener.closed();
            }
        });
    }

    @Override
    public void close() {
        if (isClosed) return;
        this.isClosed = true;

        if (listener != null && ownerTask != null) {
            ownerTask.post(() -> listener.closed());
        }

        if (peer != null) {
            peer.drainQueue();
        }
    }

    @Override
    public boolean closed() {
        return isClosed;
    }
}