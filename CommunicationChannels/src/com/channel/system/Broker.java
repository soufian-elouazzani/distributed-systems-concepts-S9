package com.channel.system;

public abstract class Broker {
    protected final String name; //we can't change the name of a broker 

    public Broker(String name) {
        this.name = name;
    }

    public String getName() { 
        return name;
    }

    public abstract Channel accept(int port);
    public abstract Channel connect(String name, int port);
}