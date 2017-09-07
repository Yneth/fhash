package ua.fhash;

import java.util.List;
import java.util.concurrent.RecursiveTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeqHierarchicalTask extends RecursiveTask<Void> {

    private final String name;
    private final int taskCount;
    private final List<Runnable> tasks;

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
        int actualTaskCount = tasks.size();
        if (actualTaskCount > taskCount) {
            List<Runnable> toDelegate = tasks.subList(taskCount, actualTaskCount);
            new SeqHierarchicalTask(name, toDelegate, taskCount).fork();
        }
        List<Runnable> toRun = tasks.subList(0, Math.min(actualTaskCount, taskCount));
        log.debug("{}.doing {} tasks", name, toRun.size());
        toRun.forEach(Runnable::run);
        return null;
    }

    public static SeqHierarchicalTask create(String name, int threadLimit, List<Runnable> tasks) {
        int taskCountPerThread = (int) Math.ceil((tasks.size() * 1.0 / threadLimit));
        return new SeqHierarchicalTask(name, tasks, taskCountPerThread);
    }

}
