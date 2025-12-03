package bankcore;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class TransactionManager {
    private final AccountStore store;
    private final WALManager wal;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService workers = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public TransactionManager(AccountStore store, WALManager wal) {
        this.store = store;
        this.wal = wal;
        // snapshot every 60s as example
        scheduler.scheduleAtFixedRate(() -> {
            try {
                snapshot();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    public void shutdown() throws IOException {
        scheduler.shutdownNow();
        workers.shutdown();
        wal.close();
    }

    public Future<Boolean> transfer(long from, long to, BigDecimal amount) {
        return workers.submit(() -> {
            Account a = store.get(from);
            Account b = store.get(to);
            if (a == null || b == null) throw new IllegalArgumentException("account missing");

            // lock order by id to avoid deadlocks
            Account first = a.getId() < b.getId() ? a : b;
            Account second = a.getId() < b.getId() ? b : a;

            boolean acquiredFirst = false;
            boolean acquiredSecond = false;
            try {
                acquiredFirst = first.getLock().tryLock(5, TimeUnit.SECONDS);
                if (!acquiredFirst) throw new IllegalStateException("could not acquire first lock");
                acquiredSecond = second.getLock().tryLock(5, TimeUnit.SECONDS);
                if (!acquiredSecond) throw new IllegalStateException("could not acquire second lock");

                // validate
                if (!a.getBalance().subtract(amount).equals(a.getBalance()) && a.getBalance().compareTo(amount) < 0) {
                    return false; // insufficient
                }

                // WAL PREPARE
                TransactionRecord prepare = new TransactionRecord(TransactionRecord.Type.TRANSFER, from, to, amount, "PREPARE");
                synchronized (wal) { wal.append("PREPARE|" + prepare.toWALLine()); }

                // apply
                boolean withdrawn = a.withdraw(amount);
                if (!withdrawn) {
                    wal.append("ABORT|" + prepare.toWALLine());
                    return false;
                }
                b.deposit(amount);

                // WAL COMMIT
                TransactionRecord commit = new TransactionRecord(TransactionRecord.Type.TRANSFER, from, to, amount, "COMMIT");
                synchronized (wal) { wal.append("COMMIT|" + commit.toWALLine()); }

                return true;
            } finally {
                if (acquiredSecond) second.getLock().unlock();
                if (acquiredFirst) first.getLock().unlock();
            }
        });
    }

    // deposit
    public Future<Boolean> deposit(long to, BigDecimal amount) {
        return workers.submit(() -> {
            Account a = store.get(to);
            if (a == null) throw new IllegalArgumentException("account missing");
            a.getLock().lock();
            try {
                TransactionRecord t = new TransactionRecord(TransactionRecord.Type.DEPOSIT, -1, to, amount, "DEPOSIT");
                wal.append("COMMIT|" + t.toWALLine());
                a.deposit(amount);
                return true;
            } finally {
                a.getLock().unlock();
            }
        });
    }

    // replay WAL (simplistic)
    public void replayWAL() throws IOException {
        List<String> lines = wal.readAll();
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            // format: PREFIX|txnLine...
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2) continue;
            String prefix = parts[0];
            String txnLine = parts[1];
            TransactionRecord rec = TransactionRecord.fromWALLine(txnLine);
            if ("COMMIT".equals(prefix)) {
                applyRecord(rec);
            } else if ("PREPARE".equals(prefix)) {
                // For simplicity, ignore PREPARE-only entries; assume if COMMIT present it's done.
            } else if ("ABORT".equals(prefix)) {
                // skip
            }
        }
    }

    private void applyRecord(TransactionRecord rec) {
        try {
            if (rec.getType() == TransactionRecord.Type.TRANSFER) {
                Account from = store.get(rec.getFromAccount());
                Account to = store.get(rec.getToAccount());
                if (from == null || to == null) return;
                // Acquire locks briefly to apply idempotently
                Account first = from.getId() < to.getId() ? from : to;
                Account second = from.getId() < to.getId() ? to : from;
                first.getLock().lock();
                second.getLock().lock();
                try {
                    // naive: if from has enough, move; else skip (real world would handle idempotency)
                    if (from.getBalance().compareTo(rec.getAmount()) >= 0) {
                        from.withdraw(rec.getAmount());
                        to.deposit(rec.getAmount());
                    }
                } finally {
                    second.getLock().unlock();
                    first.getLock().unlock();
                }
            } else if (rec.getType() == TransactionRecord.Type.DEPOSIT) {
                Account to = store.get(rec.getToAccount());
                if (to == null) return;
                to.getLock().lock();
                try {
                    to.deposit(rec.getAmount());
                } finally {
                    to.getLock().unlock();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // snapshot the store to disk
    public void snapshot() throws IOException {
        File snap = new File("data/snapshots/snapshot.dat");
        store.writeSnapshot(snap);
        System.out.println("Snapshot written: " + snap.getAbsolutePath());
    }
}
