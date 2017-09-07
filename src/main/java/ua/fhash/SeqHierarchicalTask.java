package ua.fhash;

import java.util.List;
import java.util.concurrent.RecursiveTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeqHierarchicalTask extends RecursiveTask<Void> {

    private final String name;
    private final int taskCount;
    private final List<Runnable> tasks;

    public SeqHierarchicalTask(String name, int threadLimit, List<Runnable> tasks) {
        this.name = name;
        this.taskCount = (int) Math.ceil((tasks.size() * 1.0 / threadLimit));
        this.tasks = tasks;
    }

    private SeqHierarchicalTask(String name, List<Runnable> tasks, int taskCount) {
        this.name = name;
        this.taskCount = taskCount;
        this.tasks = tasks;
    }

    @Override
    protected Void compute() {
        if (tasks.isEmpty()) {
            return null;
        }
        if (tasks.size() > taskCount) {
            new SeqHierarchicalTask(name, tasks.subList(taskCount, tasks.size()), taskCount).fork();
        }
        List<Runnable> runnables = tasks.subList(0, Math.min(tasks.size(), taskCount));
        log.debug("{}.doing {} tasks", name, runnables.size());
        runnables.forEach(Runnable::run);
        return null;
    }

}
