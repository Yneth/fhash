import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
class ThreadTest {

    private static final int CORES = Runtime.getRuntime().availableProcessors();

    @Test
    void testThreadPoolWithFailedFuture() {
        // GIVEN
        String expectedThreadName = "Name";
        ExecutorService executorService = Executors.newFixedThreadPool(1);

        // exception will be caught by FutureTask.run method
        Runnable setThreadNameAndFail = () -> {
            Thread.currentThread().setName(expectedThreadName);
            throw new RuntimeException("Test exception throw");
        };

        // WHEN
        // on submit runnable is wrapped by Callable and then by FutureTask
        executorService.submit(setThreadNameAndFail);

        // THEN
        executorService.submit(() -> {
            Assertions.assertEquals(expectedThreadName, Thread.currentThread().getName());
        });
    }

    @Test
    void testStreamBlocksCallerThread() throws Exception {
        final long startTime = System.currentTimeMillis();
        IntStream.range(0, 2).parallel().forEach(v -> wait(2_000, TimeUnit.MILLISECONDS).run());
        final long endTime = System.currentTimeMillis() - startTime;

        assertFalse(endTime < 2_000);
    }

    @Test
    void testCallerThreadWaitsForCompletionOfTheLongestTask() throws Exception {
        final long startTime = System.currentTimeMillis();
        IntStream.of(1_000, 3_000).parallel().forEach(v -> wait(v, TimeUnit.MILLISECONDS).run());
        final long endTime = System.currentTimeMillis() - startTime;

        assertFalse(endTime < 3_000);
    }

    @Test
    void testIsInterruptedBecomesFalse() throws Exception {
        // GIVEN
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> future = pool.submit(() -> {
            wait(10, TimeUnit.SECONDS).run();
        });
        Thread.sleep(500);

        Field runnerField = future.getClass().getDeclaredField("runner");
        runnerField.setAccessible(true);
        Thread runner = (Thread) runnerField.get(future);

        Thread.sleep(500);
        future.cancel(true);
        assertTrue(runner.isInterrupted(),
                "running thread should be interrupted after future.cancel");

        Thread.sleep(4000);
        assertTrue(runner.isInterrupted(),
                "running thread should remain interrupted even after some time after cancel");

        pool.shutdown();
        assertTrue(runner.isInterrupted(),
                "running thread should remain interrupted even after pool.shutdown call");

        pool.awaitTermination(60, TimeUnit.MINUTES);
        assertFalse(runner.isInterrupted(),
                "thread becomes uninterrupted after pool termination");
        assertEquals(Thread.State.TERMINATED, runner.getState());
    }

    @Test
    void test() throws Exception {
        // GIVEN
        ExecutorService pool = Executors.newFixedThreadPool(2);

        AtomicReference<Thread> runner = new AtomicReference<>();

        Future<?> future = pool.submit(() -> {
            Thread currentThread = Thread.currentThread();
            runner.compareAndSet(null, currentThread);

            Runnable task = wait(10, TimeUnit.SECONDS, () -> {
                log.debug("Thread {} interrupted - {}", currentThread.getName(), currentThread.isInterrupted());
            });
            IntStream.range(0, 10).parallel().forEach(v -> task.run());
            log.debug("------------FINISHED------------");
        });

        Thread.sleep(500);
        future.cancel(true);
        log.debug("------------CANCEL------------");
        assertTrue(runner.get().isInterrupted(),
                "running thread should be interrupted after future.cancel");

        Thread.sleep(4000);
        log.debug("------------SLEEP 4s------------");
        assertTrue(runner.get().isInterrupted(),
                "running thread should remain interrupted even after some time after cancel");

        pool.shutdown();
        log.debug("------------SHUTDOWN------------");
        assertTrue(runner.get().isInterrupted(),
                "running thread should remain interrupted even after pool.shutdown call");

        pool.awaitTermination(60, TimeUnit.MINUTES);
        log.debug("------------POOL TERMINATION------------");
        assertFalse(runner.get().isInterrupted(),
                "thread becomes uninterrupted after pool termination");
        assertEquals(Thread.State.TERMINATED, runner.get().getState());
    }

    @Test
    void test1() throws Exception {
        // GIVEN
        ExecutorService pool = Executors.newFixedThreadPool(1);
        ForkJoinPool forkJoinPool = new ForkJoinPool();

        Runnable task = wait(10, TimeUnit.SECONDS);

        Future<?> future = pool.submit(() -> {
            ForkJoinTask<?> submit = forkJoinPool.submit(() ->
                    IntStream.range(0, 10).parallel().forEach(v -> task.run()));
            try {
                submit.get();
                fail("Should throw interrupted exception");
            } catch (Exception e) {
                e.printStackTrace();
            }
            log.debug("------------FINISHED------------");
        });

        Thread.sleep(1_000);
        future.cancel(true);
        log.debug("------------CANCEL------------");

        pool.shutdown();
        log.debug("------------SHUTDOWN------------");

        pool.awaitTermination(60, TimeUnit.MINUTES);
        log.debug("------------POOL TERMINATION------------");
    }

    @Test
    void testPredicateStop() throws Exception {
         // GIVEN
        ExecutorService pool = Executors.newFixedThreadPool(1);
        ForkJoinPool forkJoinPool = new ForkJoinPool();

        AtomicBoolean interrupted = new AtomicBoolean();
        Supplier<Boolean> shouldStop = interrupted::get;

        Runnable task = wait(10, TimeUnit.SECONDS, () -> {
            Boolean val = shouldStop.get();
            log.debug("Interrupted {}", val);
            if (val) {
                throw new RuntimeException();
            }
        });

        Future<?> future = pool.submit(() -> {
            ForkJoinTask<?> submit = forkJoinPool.submit(() ->
                    IntStream.range(0, 10).parallel().forEach(v -> task.run()));
            try {
                submit.get();
                fail("Should throw interrupted exception");
            } catch (Exception e) {
                while (!interrupted.compareAndSet(false, true)) {
                }
            }
            log.debug("------------FINISHED------------");
        });

        Thread.sleep(2_000);
        future.cancel(true);
        log.debug("------------CANCEL------------");

        Thread.sleep(2_000);
        pool.shutdown();
        log.debug("------------SHUTDOWN------------");

        pool.awaitTermination(60, TimeUnit.MINUTES);
        log.debug("------------POOL TERMINATION------------");
    }

    @Test
    void testCommonThreadPool() throws Exception {
        // GIVEN
        ExecutorService callerPool = Executors.newFixedThreadPool(4);

        IntConsumer intensiveTask = task(30);
        IntConsumer easyTask = task(2);

        // WHEN
        callerPool.submit(() -> IntStream.range(0, 10).parallel().forEach(intensiveTask.andThen(log("i0"))));
        callerPool.submit(() -> IntStream.range(0, 10).parallel().forEach(intensiveTask.andThen(log("i1"))));
        callerPool.submit(() -> IntStream.range(0, 10).parallel().forEach(easyTask.andThen(log("e0"))));
        callerPool.submit(() -> IntStream.range(0, 10).parallel().forEach(easyTask.andThen(log("e1"))));

        callerPool.shutdown();
        callerPool.awaitTermination(60, TimeUnit.MINUTES);
    }

    @Test
    void testCustomForkJoinPool() throws Exception {
        // GIVEN
        ForkJoinPool pool = new ForkJoinPool(32);

        IntConsumer intensiveTask = task(32);
        IntConsumer easyTask = task(2);

        // WHEN
        pool.submit(() -> {
            log.debug("submitting");
            IntStream.range(0, 10).parallel().forEach(intensiveTask.andThen(log("e0")));
        });
        pool.submit(() -> {
            log.debug("submitting");
            IntStream.range(0, 10).parallel().forEach(intensiveTask.andThen(log("e1")));
        });
        pool.submit(() -> {
            log.debug("submitting");
            IntStream.range(0, 10).parallel().forEach(easyTask.andThen(log("e2")));
        });
        pool.submit(() -> {
            log.debug("submitting");
            IntStream.range(0, 10).parallel().forEach(easyTask.andThen(log("e3")));
        });

        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.MINUTES);
    }

    @Test
    void testCustomThreadPoolWithCoreCountThreads() throws Throwable {
        // GIVEN
        ExecutorService callerPool = Executors.newFixedThreadPool(4);
        ExecutorService executorService = Executors.newFixedThreadPool(CORES);

        IntConsumer intensiveTask = v -> executorService.submit(() -> task(30).accept(v));
        IntConsumer easyTask = v -> executorService.submit(() -> task(2).accept(v));

        // WHEN
        callerPool.submit(() -> IntStream.range(0, 10).forEach(intensiveTask));
        callerPool.submit(() -> IntStream.range(0, 10).forEach(intensiveTask));
        callerPool.submit(() -> IntStream.range(0, 10).forEach(easyTask));
        callerPool.submit(() -> IntStream.range(0, 10).forEach(easyTask));

        callerPool.shutdown();
        callerPool.awaitTermination(60, TimeUnit.MINUTES);
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.MINUTES);
    }

    @Test
    void test2() throws Exception {
        // GIVEN
        ForkJoinPool pool = new ForkJoinPool(16);

        Runnable intensiveTask = () -> {
            wait(10, TimeUnit.SECONDS).run();
            log.debug("finished");
        };
        Runnable easyTask = () -> {
            wait(2, TimeUnit.SECONDS).run();
            log.debug("finished");
        };

        List<Runnable> intensive = Stream.generate(() -> intensiveTask).limit(10).collect(Collectors.toList());
        List<Runnable> easy = Stream.generate(() -> easyTask).limit(10).collect(Collectors.toList());

        pool.submit(SeqHierarchicalTask.create("e1", 1, easy));
        pool.submit(SeqHierarchicalTask.create("e0", 1, easy));
        pool.submit(SeqHierarchicalTask.create("i0", 4, intensive));
        pool.submit(SeqHierarchicalTask.create("i1", 4, intensive));

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
    }

    private Runnable wait(long time, TimeUnit unit) {
        return wait(time, unit, () -> {});
    }

    private Runnable wait(long time, TimeUnit unit, Runnable onTick) {
        long timeToWait = TimeUnit.MILLISECONDS.convert(time, unit);
        return () -> {
            final long startTime = System.currentTimeMillis();

            long interval;
            long lastInterval = 0;
            while ((interval = System.currentTimeMillis() - startTime) <= timeToWait) {
                if (lastInterval != interval && interval % 1000 == 0) {
                    lastInterval = interval;
                    onTick.run();
                }
            }
        };
    }

    private IntConsumer task(long secondsToWait) {
        return v -> {
            wait(secondsToWait, TimeUnit.SECONDS).run();
        };
    }

    private IntConsumer log(String name) {
        return v -> {
            log.debug(name + " - " + v);
        };
    }

}
