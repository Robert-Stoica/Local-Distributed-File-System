import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


public class Dstore {

    public static void main(String[] args) {
        if(args.length != 4){
            System.out.println("Invalid arguments!");
            System.exit(1);
        }
        try{
            int dport = Integer.parseInt(args[0]);
            int server_port = Integer.parseInt(args[1]);
            int timeout = Integer.parseInt(args[2]);
            String folder = args[3];
            Dstore store = new Dstore(dport, server_port, timeout, folder);
            store.connectToServer();
            store.startStore();
        }catch (Exception er){
            System.out.println("Invalid argument type!");
            System.exit(1);
        }
    }

    private int m_nDPort;
    private int m_nServerPort;
    private int m_nTimeout;
    private String m_strFolder;
    private PrintWriter m_ServerWriter;

    public Dstore(int dport, int sport, int timeout, String folder){
        m_nDPort = dport;
        m_nServerPort = sport;
        m_nTimeout = timeout;
        m_strFolder = folder;
    }

    public void connectToServer(){
        try  {
            new Thread(new Runnable(){
                public void run(){
                    Socket socket = null;
                    try {
                        socket = new Socket("localhost", m_nServerPort);


                        BufferedReader in = new  BufferedReader(new InputStreamReader(socket.getInputStream()));
                        m_ServerWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                        m_ServerWriter.println("JOIN " + m_nDPort);
                        String line;
                        while((line = in.readLine()) != null){
                            String[] commands = line.trim() .split(" ");
                            if(commands[0].equals("REMOVE")){
                                removeFile(commands[1], m_ServerWriter);
                            }
                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (Exception ex) {

            System.out.println("Server not found: " + ex.getMessage());

        }
    }

    public void storeFile(String filename, long filelen, BufferedReader in,PrintWriter out){
        try {
            FileWriter myWriter = new FileWriter(m_strFolder + "/" + filename);
            out.println("ACK");
            char[] buffer = new char[(int)filelen];
            int nreadeed = in.read(buffer);
            myWriter.write(buffer);
            myWriter.close();
            m_ServerWriter.println("COMPLETE " + filename);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void removeFile(String filename,PrintWriter out){
        try {
            File file = new File(m_strFolder + "/" + filename);
            file.delete();
            out.println("REMOVE_ACK " + filename);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startStore(){
        try{
            ServerSocket ss = new ServerSocket(m_nDPort);

            while(!ss.isClosed()){
                try{
                    final Socket client = ss.accept();
                    client.setSoTimeout(m_nTimeout);
                    new Thread(new Runnable(){
                        public void run(){try{
                            BufferedReader in = new  BufferedReader(new InputStreamReader(client.getInputStream()));
                            PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream()), true);

                            String line;
                            while((line = in.readLine()) != null){
                                String[] commands = line.trim() .split(" ");
                                if(commands[0].equals("LOAD_DATA")){//add DStore
                                    File file = new File(m_strFolder + "/" + commands[1]);
                                    FileInputStream fileInputStream = null;
                                    byte[] bFile = new byte[(int) file.length()];

                                    try
                                    {
                                        fileInputStream = new FileInputStream(file);
                                        fileInputStream.read(bFile);
                                        fileInputStream.close();
                                        client.getOutputStream().write(bFile);

                                    }
                                    catch (Exception e)
                                    {
                                        e.printStackTrace();
                                    }
                                }
                                else if(commands[0].equals("STORE")){
                                    storeFile(commands[1], Long.parseLong(commands[2]), in, out);
                                }


                            }
                            client.close();
                        }
                        catch(Exception e){}
                        }
                    }).start();
                }catch(Exception e){System.out.println("error "+e);}
            }
        }catch(Exception e){System.out.println("error "+e);}
    }
}
