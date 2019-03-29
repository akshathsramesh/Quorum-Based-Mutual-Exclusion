import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {

    String Id;
    String ipAddress;
    String port;
    List<Node> allClientNodes = new LinkedList<>();
    List<Node> allServerNodes = new LinkedList<>();
    Integer logicalClock = 0;
    List<SocketForClient> socketConnectionList = new LinkedList<>();
    List<SocketForClient> socketConnectionListServer = new LinkedList<>();
    ServerSocket server;
    HashMap<String,SocketForClient> socketConnectionHashMap = new HashMap<>();
    HashMap<String,SocketForClient> socketConnectionHashMapServer = new HashMap<>();
    HashMap<String,Boolean> clientPermissionRequired = new HashMap<>();


    public Client(String id) {
        this.Id = id;
    }

    public String getId() {
        return this.Id;
    }

    public void setId(String id) {
        this.Id = id;
    }

    public List<Node> getAllClientNodes() {
        return allClientNodes;
    }

    public void setAllClientNodes(List<Node> allClientNodes) {
        this.allClientNodes = allClientNodes;
    }

    public List<Node> getAllServerNodes() {
        return allServerNodes;
    }

    public void setAllServerNodes(List<Node> allServerNodes) {
        this.allServerNodes = allServerNodes;
    }

    public Integer getLogicalClock() {
        return logicalClock;
    }

    public void setLogicalClock(Integer logicalClock) {
        this.logicalClock = logicalClock;
    }

    /* Command Parser to look for input fom terminal once the client is running*/
    public class CommandParser extends Thread{

        Client current;

        public CommandParser(Client current){
            this.current = current;
        }

        Pattern STATUS = Pattern.compile("^STATUS$");
        Pattern SERVER_TEST = Pattern.compile("^SERVER_TEST$");
        Pattern REQUEST_TEST = Pattern.compile("^REQUEST_TEST$");
        Pattern RELEASE_TEST = Pattern.compile("^RELEASE_TEST$");

        int rx_cmd(Scanner cmd){
            String cmd_in = null;
            if (cmd.hasNext())
                cmd_in = cmd.nextLine();
            Matcher m_STATUS = STATUS.matcher(cmd_in);
            Matcher m_SERVER_TEST = SERVER_TEST.matcher(cmd_in);
            Matcher m_REQUEST_TEST = REQUEST_TEST.matcher(cmd_in);
            Matcher m_RLEASE_TEST = RELEASE_TEST.matcher(cmd_in);

            if(m_STATUS.find()){
                System.out.println("CLIENT SOCKET STATUS:");
                try {
                    System.out.println("STATUS:  UP");
                    System.out.println("CLIENT ID: " + Id);
                    System.out.println("CLIENT IP ADDRESS: " + ipAddress);
                    System.out.println("CLIENT PORT: " + port);
                }
                catch (Exception e){
                    System.out.println("SOMETHING WENT WRONG IN TERMINAL COMMAND PROCESSOR");
                }

            }

            else if(m_SERVER_TEST.find()){
                sendServerTest();
            }


            else if(m_REQUEST_TEST.find()){
                sendRequestTest();
            }

            else if(m_RLEASE_TEST.find()){
                sendReleaseTest();
            }

            return 1;
        }

        public void run() {
            Scanner input = new Scanner(System.in);
            while(rx_cmd(input) != 0) { }
        }
    }

    public void sendServerTest(){
        Integer serverId;
        for (serverId = 0; serverId < this.socketConnectionListServer.size(); serverId++){
            socketConnectionListServer.get(serverId).serverTest();
        }
    }

    public void sendRequestTest(){
        Integer serverId;
        for (serverId = 0; serverId < this.socketConnectionListServer.size(); serverId++){
            socketConnectionListServer.get(serverId).serverRequestTest();
        }
    }

    public void sendReleaseTest(){
        Integer serverId;
        for (serverId = 0; serverId < this.socketConnectionListServer.size(); serverId++){
            socketConnectionListServer.get(serverId).serverReleaseTest();
        }
    }

    public synchronized void processGrant(String serverSendingGrant){
        System.out.println("Inside process grant for server ID "+ serverSendingGrant);

    }

    /*Helps establish the socket connection to all the servers available*/
    public void setupServerConnection(Client current){
        try{
            System.out.println("CONNECTING SERVERS");
            Integer serverId;
            for (serverId =0; serverId < allServerNodes.size(); serverId ++){
                Socket serverConnection = new Socket(this.allServerNodes.get(serverId).getIpAddress(), Integer.valueOf(this.allServerNodes.get(serverId).getPort()));
                SocketForClient socketConnectionServer = new SocketForClient(serverConnection,this.getId(),current);
                if(socketConnectionServer.getRemote_id() == null){
                    socketConnectionServer.setRemote_id(Integer.toString(serverId));
                }
                socketConnectionListServer.add(socketConnectionServer);
                socketConnectionHashMapServer.put(socketConnectionServer.getRemote_id(),socketConnectionServer);
            }
        }
        catch (Exception e){
            System.out.println("Setup Server Connection Failure");
        }

    }

    /*Used to create client listen socket and use the listener to add requesting socket connection*/
    public void clientSocket(Integer ClientId, Client current){
        try
        {
            server = new ServerSocket(Integer.valueOf(this.allClientNodes.get(ClientId).port));
            Id = Integer.toString(ClientId);
            ipAddress = this.allClientNodes.get(ClientId).getIpAddress();
            port = this.allClientNodes.get(ClientId).getPort();
            System.out.println("Client node running on port " + Integer.valueOf(this.allClientNodes.get(ClientId).port) +"," + " use ctrl-C to end");
            InetAddress myip = InetAddress.getLocalHost();
            String ip = myip.getHostAddress();
            String hostname = myip.getHostName();
            System.out.println("Your current IP address : " + ip);
            System.out.println("Your current Hostname : " + hostname);
        }
        catch (IOException e)
        {
            System.out.println("Error creating socket");
            System.exit(-1);
        }

        CommandParser cmdpsr = new CommandParser(current);
        cmdpsr.start();

        Thread current_node = new Thread() {
            public void run(){
                while(true){
                    try{
                        Socket s = server.accept();
                        SocketForClient socketConnection = new SocketForClient(s,Id, current);
                        socketConnectionList.add(socketConnection);
                        socketConnectionHashMap.put(socketConnection.getRemote_id(),socketConnection);
                        clientPermissionRequired.put(socketConnection.getRemote_id(),true);
                    }
                    catch(IOException e){ e.printStackTrace(); }
                }
            }
        };

        current_node.setDaemon(true);
        current_node.start();
    }


    /*Consuming client config file and save the information*/
    public void setClientList(){
        try {
            BufferedReader br = new BufferedReader(new FileReader("config_client.txt"));
            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    List<String> parsed_client = Arrays.asList(line.split(","));
                    Node n_client= new Node(parsed_client.get(0),parsed_client.get(1),parsed_client.get(2));
                    this.getAllClientNodes().add(n_client);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                String everything = sb.toString();
                System.out.println("____________________________");
                System.out.println(everything);
                System.out.println("____________________________");
                System.out.println("******** NUMBER OF CLIENTS:  "+ this.getAllClientNodes().size());
                System.out.println("____________________________");

            } finally {
                br.close();
            }
        }
        catch (Exception e) {
        }
    }

    /*Consume the server config file and save the information*/
    public void setServerList(){
        try {
            BufferedReader br = new BufferedReader(new FileReader("config_server.txt"));
            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    List<String> parsed_server = Arrays.asList(line.split(","));
                    Node n_server = new Node(parsed_server.get(0),parsed_server.get(1),parsed_server.get(2));
                    this.getAllServerNodes().add(n_server);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                String everything = sb.toString();
                System.out.println("____________________________");
                System.out.println(everything);
                System.out.println("____________________________");
                System.out.println("******** NUMBER OF SERVERS: "+this.getAllServerNodes().size());
                System.out.println("____________________________");

            } finally {
                br.close();
            }
        }
        catch (Exception e) {
        }

    }

    public static void main(String[] args) {

        if (args.length != 1)
        {
            System.out.println("Usage: java Client <client-number>");
            System.exit(1);
        }


        System.out.println("Starting the Client");

        Client C1 = new Client(args[0]);
        C1.setClientList();
        C1.setServerList();
        C1.setupServerConnection(C1);
        C1.clientSocket(Integer.valueOf(args[0]),C1);

        System.out.println("Started Client with ID: " + C1.getId());
    }
}
