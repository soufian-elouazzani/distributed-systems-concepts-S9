package edu.polytech.MessageQueue.local;


import edu.polytech.queues.QueueBroker;

import java.util.HashMap;
import java.util.Map;



public class CQueueBroker implements QueueBroker {
    private static final Map<String, CQueueBroker> globalRegistry = new HashMap<>();

    private final String name;
    private final CTask creatorTask;
    private final Map<Integer, BindListener> bindings = new HashMap<>();

    public CQueueBroker(String name, CTask creatorTask) {
        this.name = name;
        this.creatorTask = creatorTask;
        synchronized (globalRegistry) {
            if (globalRegistry.containsKey(name)) {
                throw new IllegalArgumentException("Broker name already registered: " + name);
            }
            globalRegistry.put(name, this);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Task getTask() {
        return creatorTask;
    }

    @Override
    public synchronized boolean bind(int port, BindListener listener) {
        if (bindings.containsKey(port)) {
            return false;
        }
        bindings.put(port, listener);
        return true;
    }

    @Override
    public synchronized boolean unbind(int port) {
        BindListener listener = bindings.remove(port);
        if (listener != null) {
            creatorTask.post(listener::unbound);
            return true;
        }
        return false;
    }

    @Override
    public boolean connect(String targetName, int port, ConnectListener listener) {
        CQueueBroker targetBroker;
        synchronized (globalRegistry) {
            targetBroker = globalRegistry.get(targetName);
        }

        if (targetBroker == null) {
            creatorTask.post(listener::refused);
            return false;
        }

        BindListener bindListener;
        synchronized (targetBroker) {
            bindListener = targetBroker.bindings.get(port);
        }

        if (bindListener == null) {
            creatorTask.post(listener::refused);
            return false;
        }

        // Create paired message queues
        CMessageQueue clientQueue = new CMessageQueue(this, this.creatorTask);
        CMessageQueue serverQueue = new CMessageQueue(targetBroker, targetBroker.creatorTask);

        clientQueue.setPeer(serverQueue);
        serverQueue.setPeer(clientQueue);

        // Notify bind listener on target task
        targetBroker.creatorTask.post(() -> bindListener.accepted(serverQueue));

        // Notify connect listener on caller task
        this.creatorTask.post(() -> listener.connected(clientQueue));

        return true;
    }
}