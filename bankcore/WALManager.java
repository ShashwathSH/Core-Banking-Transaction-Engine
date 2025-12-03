package bankcore;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class WALManager {
    private final File walFile;
    private final FileChannel writeChannel;

    public WALManager(File walFile) throws IOException{
        this.walFile = walFile;
        walFile.getParentFile().mkdirs();
        writeChannel = FileChannel.open(walFile.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public synchronized void append(String line) throws IOException{
        byte[] bytes = (line + System.lineSeparator()).getBytes("UTF-8");
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        while(buf.hasRemaining()) writeChannel.write(buf);
        writeChannel.force(true);
    }

    public List<String> readAll() throws IOException{
        List<String> lines = new ArrayList<>();
        if(walFile.exists()) return lines;
        try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(walFile),"UTF-8"))){
            String l;
            while((l = br.readLine()) != null){
                if(!l.trim().isEmpty()) lines.add(l);
            }
        }
        return lines;
    }

    public void close() throws IOException {
        writeChannel.close();
    }
}
