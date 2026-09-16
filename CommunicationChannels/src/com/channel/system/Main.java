package com.channel.system;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Instantiate two brokers to simulate separate nodes
        LocalBroker serverBroker = new LocalBroker("ServerNode");
        LocalBroker clientBroker = new LocalBroker("ClientNode");

        // Server Task Logic
        Runnable serverLogic = () -> {
            Broker b = Task.getBroker();
            System.out.println("[ServerTask] Waiting for connection on port 8080...");
            Channel channel = b.accept(8080);
            System.out.println("[ServerTask] Connected to client!");

            byte[] buffer = new byte[100];
            int readBytes = channel.read(buffer, 0, buffer.length);
            String msg = new String(buffer, 0, readBytes);
            System.out.println("[ServerTask] Received: " + msg);

            // Echo back
            String reply = "Echo: " + msg;
            channel.write(reply.getBytes(), 0, reply.getBytes().length);
            channel.disconnect(); // Demi-disconnect
        };

        // Client Task Logic
        Runnable clientLogic = () -> {
            Broker b = Task.getBroker();
            System.out.println("[ClientTask] Connecting to ServerNode on port 8080...");
            Channel channel = b.connect("ServerNode", 8080);
            System.out.println("[ClientTask] Connected to server!");

            String msg = "Hello from Client Task!";
            channel.write(msg.getBytes(), 0, msg.getBytes().length);

            byte[] buffer = new byte[100];
            int readBytes = channel.read(buffer, 0, buffer.length);
            System.out.println("[ClientTask] Received reply: " + new String(buffer, 0, readBytes));
            channel.disconnect();
        };

        // Create tasks bound to their respective brokers
        Task serverTask = new Task(serverBroker, serverLogic);
        Task clientTask = new Task(clientBroker, clientLogic);

        // Start execution threads
        serverTask.start();
        clientTask.start();

        serverTask.join();
        clientTask.join();

        System.out.println("[System] Execution finished successfully.");
    }
}