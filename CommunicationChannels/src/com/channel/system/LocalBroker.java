package com.channel.system;

import java.util.HashMap;
import java.util.Map;

public class LocalBroker extends Broker {
    private static final Map<String, LocalBroker> registry = new HashMap<>();
    private final Map<Integer, ConnectionRendezvous> listeners = new HashMap<>();

    public LocalBroker(String name) {
        super(name);
        synchronized (registry) {
            if (registry.containsKey(name)) {
                throw new IllegalArgumentException("Broker already exists: " + name);
            }
            registry.put(name, this);
        }
    }

    private static class ConnectionRendezvous {
        Channel serverChannel;
        Channel clientChannel;
        boolean connected = false;
    }

    @Override
    public synchronized Channel accept(int port) {
        ConnectionRendezvous rendezvous = listeners.computeIfAbsent(port, k -> new ConnectionRendezvous());
        
        while (!rendezvous.connected) {
            try {
                wait(); // Block server task until client calls connect()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        listeners.remove(port);
        return rendezvous.serverChannel;
    }

    @Override
    public Channel connect(String targetName, int port) {
        LocalBroker targetBroker;
        synchronized (registry) {
            targetBroker = registry.get(targetName);
        }
        if (targetBroker == null) {
            throw new IllegalArgumentException("Target broker not found: " + targetName);
        }

        synchronized (targetBroker) {
            ConnectionRendezvous rendezvous = targetBroker.listeners.computeIfAbsent(port, k -> new ConnectionRendezvous());

            // Create two circular buffers for full-duplex communication
            CircularBuffer clientToServer = new CircularBuffer(1024);
            CircularBuffer serverToClient = new CircularBuffer(1024);

            rendezvous.serverChannel = new LocalChannel(clientToServer, serverToClient);
            rendezvous.clientChannel = new LocalChannel(serverToClient, clientToServer);
            rendezvous.connected = true;

            targetBroker.notifyAll(); // Unblock server task calling accept()
            return rendezvous.clientChannel;
        }
    }
}