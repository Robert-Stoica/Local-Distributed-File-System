import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;


public class Controller {

    public static void main(String [] args){
        if(args.length != 4){
            System.out.println("Invalid arguments!");
            System.exit(1);
        }
        try{
            int cport = Integer.parseInt(args[0]);
            int r = Integer.parseInt(args[1]);
            int timeout = Integer.parseInt(args[2]);
            int balance = Integer.parseInt(args[3]);
            Controller con = new Controller(cport, r, timeout, balance);
            con.startServer();
        }catch (Exception er){
            System.out.println("Invalid argument type!");
            System.exit(1);
        }

    }

    private int m_nPort;
    private int m_nRepeat;
    private int m_nTimeout;
    private int m_nBalance_period;
    private List<DStoreHandler> m_lstDStore = new ArrayList<>();
    private List<FileData> m_IndexFiles = new ArrayList<>();
    private int m_nCurrent_DStore = -1;
    private final Object lock = new Object();

    public Controller(int cport, int r, int timeout, int balance){
        this.m_nPort = cport;
        this.m_nRepeat = r;
        this.m_nTimeout = timeout;
        this.m_nBalance_period = balance;
    }

    public void startServer(){
        try{
            ServerSocket ss = new ServerSocket(m_nPort);

            while(!ss.isClosed()){
                try{
                    final Socket client = ss.accept();

                    new Thread(new Runnable(){
                        public void run(){try{
                            BufferedReader in = new  BufferedReader(new InputStreamReader(client.getInputStream()));
                            PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream()), true);

                            String line;
                            boolean bStore = false;
                            while((line = in.readLine()) != null){
                                String[] commands = line.trim() .split(" ");
                                if(commands[0].equals("JOIN")){//add DStore
                                    DStoreHandler dstore = new DStoreHandler(client, Integer.parseInt(commands[1]) ,in, out);
                                    m_lstDStore.add(dstore);
                                    bStore = true;
                                    break;
                                }
                                else{
                                    if(m_lstDStore.size() < m_nRepeat){
                                        out.println("ERROR_NOT_ENOUGH_DSTORES");
                                        continue;
                                    }
                                    if(commands[0].equals("STORE")){
                                        if(indexFile(commands[1]) != null){
                                            out.println("ERROR ALREADY_EXISTS");
                                            continue;
                                        }

                                        doStore(commands[1], Long.parseLong(commands[2]), out);
                                    }
                                    else if(commands[0].equals("LOAD") || commands[0].equals("RELOAD")){
                                        FileData fd = indexFile(commands[1]);
                                        if(fd == null){
                                            out.println("ERROR_FILE_DOES_NOT_EXIST");
                                            continue;
                                        }
                                        if(!fd.bState){
                                            out.println("ERROR_FILE_DOES_NOT_EXIST");
                                            continue;
                                        }
                                        m_nCurrent_DStore++;
                                        if(m_nCurrent_DStore >= m_lstDStore.size())
                                            m_nCurrent_DStore = 0;
                                        out.println("LOAD_FROM " + m_lstDStore.get(m_nCurrent_DStore).getPort() + " " + fd.size);
                                    }
                                    else if(commands[0].equals("REMOVE")){
                                        FileData fd = indexFile(commands[1]);
                                        if(fd == null){
                                            out.println("ERROR_FILE_DOES_NOT_EXIST");
                                            continue;
                                        }
                                        doRemove(commands[1]);
                                        out.println("REMOVE_COMPLETE");
                                    }
                                    else if(commands[0].equals("LIST")){
                                        String strlst = "LIST";
                                        for(FileData fd : m_IndexFiles){
                                            strlst += " " + fd.name;
                                        }
                                        out.println(strlst);
                                    }

                                }

                            }
                            if(!bStore)
                                client.close();
                        }
                        catch(Exception e){}
                        }
                    }).start();
                }catch(Exception e){System.out.println("error "+e);}
            }
        }catch(Exception e){System.out.println("error "+e);}
    }

    public FileData indexFile(String name){
        for(FileData fd : m_IndexFiles){
            if(fd.name.equals(name))
                return fd;
        }
        return null;
    }


    private void doStore(String filename, long file_len, PrintWriter out){
        synchronized (lock) {
            m_IndexFiles.add(new FileData(filename, file_len));
        }
        String contactPort = "";
        for(DStoreHandler dstore : m_lstDStore){
            //dstore.createFile(filename, file_len);
            contactPort += " " + dstore.getPort();
        }
        out.println("STORE_TO" + contactPort);
//        char[] buffer = new char[1024];
//        int curlen = 0;
//        while(curlen < file_len){
//            try {
//                int readed = reader.read(buffer);
//                curlen += readed;
//                for(DStoreHandler dstore : m_lstDStore){
//
//                    dstore.storeFile(buffer);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        for(DStoreHandler dstore : m_lstDStore){
            dstore.closeFile(filename);
        }
        synchronized (lock) {
            indexFile(filename).bState = true;
        }
        out.println("STORE_COMPLETE");
    }


    private void doRemove(String filename){
        synchronized (lock) {
            indexFile(filename).bState = false;
        }
        for(DStoreHandler dstore : m_lstDStore){
            dstore.removeFile(filename);
        }
        synchronized (lock) {
            m_IndexFiles.remove(indexFile(filename));
        }
    }
    class FileData{
        public String name;
        public long size;
        public boolean bState;
        public FileData(String _n, long _s){
            name = _n;
            size = _s;
            bState = false;
        }

    }

    class DStoreHandler
    {
        private int m_Dport;
        final BufferedReader dis;
        final PrintWriter dos;
        Socket m_dsoc;

        // constructor
        public DStoreHandler(Socket s, int dport,BufferedReader dis, PrintWriter dos) {
            this.dis = dis;
            this.dos = dos;
            this.m_Dport = dport;
            this.m_dsoc = s;
        }

        public boolean createFile(String filename, long filelen){
            if(!m_dsoc.isClosed()){
                dos.println("CREATE " + filename + " " + filename);
                try {
                    String reponse = dis.readLine().trim().split(" ")[0];
                    if(reponse.equals("CREATED"))
                        return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        public boolean closeFile(String filename){
            if(!m_dsoc.isClosed()){
                //dos.println("CLOSE " + filename);
                try {
                    String reponse = dis.readLine().trim();
                    if(reponse.equals("COMPLETE " + filename))
                        return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        public boolean removeFile(String filename){
            if(!m_dsoc.isClosed()){
                dos.println("REMOVE " + filename);
                try {
                    String reponse = dis.readLine().trim();
                    if(reponse.equals("REMOVE_ACK " + filename))
                        return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        public boolean storeFile(char[] buf){

            if(!m_dsoc.isClosed()){
                dos.write(buf);
                try {
                    String reponse = dis.readLine().trim().split(" ")[0];
                    if(reponse.equals("WRITED"))
                        return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        public int getPort(){
            return this.m_Dport;
        }

    }
}

