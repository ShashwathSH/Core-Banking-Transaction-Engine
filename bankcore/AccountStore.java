package bankcore;


import javax.imageio.IIOException;
import java.io.*;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccountStore implements Serializable{
    private final ConcurrentHashMap<Long,Account> accounts = new ConcurrentHashMap<>();

    public Account createAccount(long id, String name, BigDecimal initial){
        Account a = new Account(id,name,initial);
        accounts.put(id,a);
        return a;
    }

    public Account get(long id) { return accounts.get(id); }

    public Collection<Account> allAccounts(){ return accounts.values(); }

    public Map<Long,Account> rawMap() { return accounts; }

    public synchronized void writeSnapshot(File file) throws IOException{
        file.getParentFile().mkdirs(); // ensure parent exists
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))){
            oos.writeObject(accounts);
        }
    }

    @SuppressWarnings("unchecked")
    public static AccountStore loadSnapshot(File file) throws IOException, ClassNotFoundException{
        AccountStore store = new AccountStore();
        try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))){
            ConcurrentHashMap<Long,Account> loaded = (ConcurrentHashMap<Long, Account>) ois.readObject();

            for(Map.Entry<Long,Account> e: loaded.entrySet()){
                Account a = e.getValue();
                Account fresh = new Account(a.getId(),a.getOwner(),a.getBalance());
                store.accounts.put(fresh.getId(), fresh);
            }
        }
        return store;
    }
}
