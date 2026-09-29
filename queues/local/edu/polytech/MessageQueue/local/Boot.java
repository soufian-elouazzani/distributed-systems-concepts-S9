package edu.polytech.MessageQueue.local;

import edu.polytech.queues.Bootstrap;
import edu.polytech.queues.Task;

public class Boot implements Bootstrap {

	public Boot() {
        // Public no-arg constructor required by specification
    }

    @Override
    public CTask newTask(Runnable r, String name) {
        return new CTask();
    }

}
