package processing;

import java.util.*;
import java.util.concurrent.*;

public class CDRBuffer {

    private static final int  BATCH_SIZE         = 100;
    private static final long FLUSH_INTERVAL_SEC = 5;

    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(10_000);
    private final CDRSorter sorter = new CDRSorter();
    private final Runnable onFlush; // Injected: triggers Person C's CSVFormatter

    public CDRBuffer(Runnable onFlush) {
        this.onFlush = onFlush;
        startTimedFlusher();
    }

    public void add(Object cdr) {
        if (!queue.offer(cdr)) {
            System.err.println("CdrBuffer: FULL — emergency flush");
            flush();
            queue.offer(cdr);
        }
        if (queue.size() >= BATCH_SIZE) flush();
    }

    public synchronized void flush() {
        if (queue.isEmpty()) return;
        List<Object> batch = new ArrayList<>();
        queue.drainTo(batch, BATCH_SIZE);
        List<Object> sorted = sorter.sort(batch);
        for (Object cdr : sorted) CDR_DAO.insertCdr(cdr); // persist to Mediation NeonDB
        if (onFlush != null) onFlush.run(); // trigger Person C
    }

    private void startTimedFlusher() {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::flush,
                        FLUSH_INTERVAL_SEC, FLUSH_INTERVAL_SEC, TimeUnit.SECONDS);
    }
}
