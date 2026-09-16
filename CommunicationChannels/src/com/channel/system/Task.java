package com.channel.system;

public class Task extends Thread {
    private static final ThreadLocal<Broker> currentBroker = new ThreadLocal<>();

    public Task(Broker b, Runnable r) {
        super(() -> {
            currentBroker.set(b);
            r.run();
            currentBroker.remove();
        });
    }

    public static Broker getBroker() {
        return currentBroker.get();
    }
}