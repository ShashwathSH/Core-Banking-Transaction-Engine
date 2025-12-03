package bankcore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

public class Account implements Serializable{
    private final long id;
    private final String owner;
    private BigDecimal balance;
    private transient final ReentrantLock lock = new ReentrantLock();

    public Account(long id,String owner, BigDecimal initial){
        this.id = id;
        this.owner = owner;
        this.balance = initial;
    }

    private Object readResolve(){
        return this;
    }

    public String getOwner() {
        return owner;
    }

    public long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void deposit(BigDecimal amount){
        synchronized (this){
            balance = balance.add(amount);
        }
    }

    public boolean withdraw(BigDecimal amount){
        synchronized (this){
            if(balance.compareTo(amount) < 0) return false;
            balance = balance.subtract(amount);
            return true;
        }
    }

    @Override
    public String toString(){
        return "Account{" + "id=" + id + ", owner='" + owner + '\'' + ", balance=" + getBalance() + '}';
    }
}