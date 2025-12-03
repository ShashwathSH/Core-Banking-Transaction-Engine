package bankcore;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    public static void main(String[] args) throws Exception {
        // Setup storage & WAL
        File walFile = new File("data/wal/wal.log");
        WALManager wal = new WALManager(walFile);

        AccountStore store;
        File snapshot = new File("data/snapshots/snapshot.dat");
        if (snapshot.exists()) {
            System.out.println("Loading snapshot...");
            store = AccountStore.loadSnapshot(snapshot);
        } else {
            store = new AccountStore();
            // seed some accounts
            for (long i = 1; i <= 5; i++) {
                store.createAccount(i, "User" + i, new BigDecimal("1000"));
            }
        }

        TransactionManager tm = new TransactionManager(store, wal);
        // replay WAL to recover state after snapshot
        tm.replayWAL();

        // Start a background simulator to show concurrency (optional)
        Thread simulator = new Thread(() -> {
            try {
                while (true) {
                    // perform some random transfers
                    List<Long> ids = store.allAccounts().stream().map(Account::getId).collect(Collectors.toList());
                    if (ids.size() < 2) continue;
                    long from = ids.get((int) (Math.random() * ids.size()));
                    long to = ids.get((int) (Math.random() * ids.size()));
                    if (from == to) continue;
                    Future<Boolean> f = tm.transfer(from, to, new BigDecimal("1"));
                    f.get(); // wait small
                    Thread.sleep(200);
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        simulator.setDaemon(true);
        simulator.start();

        System.out.println("Core Banking Engine started. Type 'help' for commands.");
        Scanner sc = new Scanner(System.in);
        for (;;) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            String cmd = p[0].toLowerCase();
            try {
                switch (cmd) {
                    case "help":
                        System.out.println("commands: list, balance <id>, transfer <from> <to> <amt>, deposit <to> <amt>, exit");
                        break;
                    case "list":
                        store.allAccounts().forEach(System.out::println);
                        break;
                    case "balance":
                        long id = Long.parseLong(p[1]);
                        System.out.println(store.get(id));
                        break;
                    case "transfer":
                        long from = Long.parseLong(p[1]);
                        long to = Long.parseLong(p[2]);
                        BigDecimal amt = new BigDecimal(p[3]);
                        Future<Boolean> f = tm.transfer(from, to, amt);
                        boolean ok = f.get();
                        System.out.println("transfer " + (ok ? "OK" : "FAILED"));
                        break;
                    case "deposit":
                        long toId = Long.parseLong(p[1]);
                        BigDecimal a = new BigDecimal(p[2]);
                        Future<Boolean> fd = tm.deposit(toId, a);
                        System.out.println("deposit " + (fd.get() ? "OK" : "FAILED"));
                        break;
                    case "wal":
                        List<String> walLines = wal.readAll();
                        walLines.forEach(System.out::println);
                        break;
                    case "exit":
                        System.out.println("Shutting down...");
                        tm.shutdown();
                        System.exit(0);
                        break;
                    default:
                        System.out.println("unknown");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
