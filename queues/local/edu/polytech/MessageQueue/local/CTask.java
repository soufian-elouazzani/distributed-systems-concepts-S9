package edu.polytech.MessageQueue.local;

import edu.polytech.queues.QueueBroker;
import edu.polytech.queues.Task;


import edu.polytech.utils.Executor;
import java.util.concurrent.*;

public class CTask extends Task {
    private final String name;
    private final BlockingQueue<Runnable> eventQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Thread eventLoopThread;
    
    private QueueBroker broker;
    private Listener listener;
    
    private volatile boolean dead = false;
    private volatile Object exitValue = null;
    private volatile Throwable failureReason = null;

    public CTask(String name) {
        this.name = name;
        this.eventLoopThread = new Thread(this::runEventLoop, "TaskThread-" + name);
        this.eventLoopThread.start();
    }

    public CTask(Runnable initialLogic, String name) {
        this(name);
        if (initialLogic != null) {
            post(initialLogic);
        }
    }

    private void runEventLoop() {
        Executor.setCurrentTask(this);
        try {
            while (!dead) {
                Runnable event = eventQueue.take();
                try {
                    event.run();
                } catch (Throwable th) {
                    fail(th);
                }
            }
        } catch (InterruptedException e) {
            // Task thread interrupted on exit/fail
        } finally {
            scheduler.shutdownNow();
            Executor.clearCurrentTask();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public QueueBroker getBroker() {
        return broker;
    }

    public void setBroker(QueueBroker broker) {
        this.broker = broker;
    }

    @Override
    public void post(Runnable r) {
        if (!dead) {
            eventQueue.offer(r);
        }
    }

    @Override
    public void post(Runnable r, int delay) {
        if (!dead) {
            scheduler.schedule(() -> post(r), delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean dead() {
        return dead;
    }

    @Override
    public Task newTask(String name) {
        return new CTask(name);
    }

    @Override
    public QueueBroker newBroker(String name) {
        CQueueBroker qb = new CQueueBroker(name, this);
        if (this.broker == null) {
            this.broker = qb;
        }
        return qb;
    }

    @Override
    public void set(Listener l) {
        this.listener = l;
    }

    @Override
    public void exit(Object o) {
        if (dead) return;
        this.dead = true;
        this.exitValue = o;
        this.eventLoopThread.interrupt();

        if (listener != null) {
            Task callerTask = Executor.task();
            if (callerTask != null) {
                callerTask.post(() -> listener.completed(this, o));
            } else {
                listener.completed(this, o);
            }
        }
    }

    @Override
    public void fail(Throwable th) {
        if (dead) return;
        this.dead = true;
        this.failureReason = th;
        this.eventLoopThread.interrupt();

        if (listener != null) {
            Task callerTask = Executor.task();
            if (callerTask != null) {
                callerTask.post(() -> listener.failed(this, th));
            } else {
                listener.failed(this, th);
            }
        }
    }
}