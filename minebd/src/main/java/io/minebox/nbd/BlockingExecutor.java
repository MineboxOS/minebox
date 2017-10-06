package io.minebox.nbd;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An executor which blocks and prevents further tasks from
 * being submitted to the pool when the queue is full.
 * <p>
 * Based on the BoundedExecutor example in:
 * Brian Goetz, 2006. Java Concurrency in Practice. (Listing 8.4)
 */
public class BlockingExecutor extends ThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.
            getLogger(BlockingExecutor.class);
    private final Semaphore semaphore;

    /**
     * Creates a BlockingExecutor which will block and prevent further
     * submission to the pool when the specified queue size has been reached.
     *
     * @param poolSize  the number of the threads in the pool
     * @param queueSize the size of the queue
     */
    public BlockingExecutor(final int poolSize, final int queueSize) {
        super(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        // the semaphore is bounding both the number of tasks currently executing
        // and those queued up
        semaphore = new Semaphore(poolSize + queueSize);
    }

    AtomicLong taskCount = new AtomicLong(0);

    /**
     * Executes the given task.
     * This method will block when the semaphore has no permits
     * i.e. when the queue has reached its capacity.
     */
    @Override
    public void execute(final Runnable task) {
        final Runnable wrappedTask;
        if (LOGGER.isDebugEnabled()) {
            wrappedTask = () -> {
                final long taskNumber = taskCount.incrementAndGet();
                LOGGER.debug("starting {}", taskNumber);
                task.run();
                LOGGER.debug("ended    {}", taskNumber);
            };
        } else {
            wrappedTask = task;
        }
        boolean acquired = false;
        do {
            try {
                semaphore.acquire();
                acquired = true;
            } catch (final InterruptedException e) {
                LOGGER.warn("InterruptedException whilst aquiring semaphore", e);
            }
        } while (!acquired);

        try {
            super.execute(wrappedTask);
        } catch (final RejectedExecutionException e) {
            semaphore.release();
            throw e;
        }
    }

    /**
     * Method invoked upon completion of execution of the given Runnable,
     * by the thread that executed the task.
     * Releases a semaphore permit.
     */
    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        super.afterExecute(r, t);
        semaphore.release();
    }
}