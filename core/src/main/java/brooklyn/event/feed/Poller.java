package brooklyn.event.feed;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.Task;
import brooklyn.util.MutableMap;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;

import com.google.common.base.Objects;


/** 
 * For executing periodic polls.
 * Jobs are added to the schedule, and then the poller is started.
 * The jobs will then be executed periodically, and the handler called for the result/failure.
 * 
 * Assumes the schedule+start will be done single threaded, and that stop will not be done concurrently.
 */
public class Poller<V> {
    public static final Logger log = LoggerFactory.getLogger(Poller.class);

    private final EntityLocal entity;
    private final Set<PollJob<V>> pollJobs = new LinkedHashSet<PollJob<V>>();
    private final Set<ScheduledTask> tasks = new LinkedHashSet<ScheduledTask>();
    private volatile boolean running = false;
    
    private static class PollJob<V> {
        final Callable<V> job;
        final PollHandler<? super V> handler;
        final long pollPeriod;
        final Runnable wrappedJob;
        
        PollJob(final Callable<V> job, final PollHandler<? super V> handler, long period) {
            this.job = job;
            this.handler = handler;
            this.pollPeriod = period;
            
            wrappedJob = new Runnable() {
                public void run() {
                    try {
                        V val = job.call();
                        handler.onSuccess(val);
                    } catch (Exception e) {
                        handler.onError(e);
                    }
                }
            };
        }
    }
    
    public Poller(EntityLocal entity) {
        this.entity = entity;
    }
    
    public void scheduleAtFixedRate(Callable<V> job, PollHandler<? super V> handler, long period) {
        if (running) {
            throw new IllegalStateException("Cannot schedule additional tasks after poller has started");
        }
        PollJob<V> foo = new PollJob<V>(job, handler, period);
        pollJobs.add(foo);
    }

    public void start() {
        // TODO Previous incarnation of this logged this logged polledSensors.keySet(), but we don't know that anymore
        // Is that ok, are can we do better?
        
        if (log.isDebugEnabled()) log.debug("Starting poll for {} (using {})", new Object[] {entity, this});
        running = true;
        
        for (final PollJob<V> pollJob : pollJobs) {
            if (pollJob.pollPeriod > 0) {
                Callable<Task<?>> pollingTaskFactory = new Callable<Task<?>>() {
                    public Task<?> call() {
                        return new BasicTask<V>(MutableMap.of("entity", entity), pollJob.wrappedJob); }
                };
                ScheduledTask task = new ScheduledTask(MutableMap.of("period", pollJob.pollPeriod), pollingTaskFactory);
                tasks.add((ScheduledTask) entity.getExecutionContext().submit(task));
            } else {
                if (log.isDebugEnabled()) log.debug("Activating poll (but leaving off, as period {}) for {} (using {})", new Object[] {pollJob.pollPeriod, entity, this});
            }
        }
    }
    
    public void stop() {
        if (log.isDebugEnabled()) log.debug("Stopping poll for {} (using {})", new Object[] {entity, this});
        running = false;
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    public boolean isRunning() {
        return running;
    }
    
    protected boolean isEmpty() {
        return pollJobs.isEmpty();
    }
    
    public String toString() {
        return Objects.toStringHelper(this).add("entity", entity).toString();
    }
}
