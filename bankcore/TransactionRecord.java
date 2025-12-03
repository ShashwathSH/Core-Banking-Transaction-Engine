package bankcore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionRecord implements Serializable {
    public enum Type { DEPOSIT, WITHDRAW, TRANSFER }

    private final String txnId;
    private final Type type;
    private final long fromAccount;
    private final long toAccount; // -1 if N/A
    private final BigDecimal amount;
    private final Instant timestamp;
    private final String note;

    public TransactionRecord(Type type, long from, long to, BigDecimal amount, String note) {
        this.txnId = UUID.randomUUID().toString();
        this.type = type;
        this.fromAccount = from;
        this.toAccount = to;
        this.amount = amount;
        this.timestamp = Instant.now();
        this.note = note;
    }

    public String getTxnId() { return txnId; }
    public Type getType() { return type; }
    public long getFromAccount() { return fromAccount; }
    public long getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public Instant getTimestamp() { return timestamp; }
    public String getNote() { return note; }

    // Represent as a single-line for WAL (simple pipe-separated)
    public String toWALLine() {
        return String.join("|",
                txnId,
                type.name(),
                Long.toString(fromAccount),
                Long.toString(toAccount),
                amount.toPlainString(),
                timestamp.toString(),
                note == null ? "" : note
        );
    }

    public static TransactionRecord fromWALLine(String line) {
        String[] p = line.split("\\|", -1);
        String txnId = p[0];
        Type type = Type.valueOf(p[1]);
        long from = Long.parseLong(p[2]);
        long to = Long.parseLong(p[3]);
        java.math.BigDecimal amount = new java.math.BigDecimal(p[4]);
        // timestamp and note are not strictly needed for replay actions
        return new TransactionRecord(type, from, to, amount, p.length > 6 ? p[6] : "");
    }
}
