import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;

public class Client {

    String Id;
    String ipAddress;
    String port;
    List<Node> allClientNodes = new LinkedList<>();
    List<Node> allServerNodes = new LinkedList<>();
    List<SocketForClient> socketConnectionListServer = new LinkedList<>();
    ServerSocket server;
    HashMap<String,SocketForClient> socketConnectionHashMapServer = new HashMap<>();
    List<List<String>> quorum = new LinkedList<>();
    Integer genRequestDelay = 0;
    Integer outStandingGrantCount = 0;
    Integer currentQuorumIndex = 0;
    Boolean requestedCS = false;
    Integer requestMessageCounter = 0;
    Integer releaseMessageCounter = 0;
    Integer grantMessageCouter = 0;
    Integer requestCounter = 0;


    public void setGenRequestDelay(Integer genRequestDelay) {
        this.genRequestDelay = genRequestDelay;
    }


    public Integer getGenRequestDelay() {
        return genRequestDelay;
    }


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
        Pattern SHOW_QUORUM_LIST = Pattern.compile("^SHOW_QUORUM_LIST$");
        Pattern AUTO_REQUEST = Pattern.compile("^AUTO_REQUEST$");

        int rx_cmd(Scanner cmd){
            String cmd_in = null;
            if (cmd.hasNext())
                cmd_in = cmd.nextLine();
            Matcher m_STATUS = STATUS.matcher(cmd_in);
            Matcher m_SERVER_TEST = SERVER_TEST.matcher(cmd_in);
            Matcher m_REQUEST_TEST = REQUEST_TEST.matcher(cmd_in);
            Matcher m_RELEASE_TEST = RELEASE_TEST.matcher(cmd_in);
            Matcher m_SHOW_QUORUM_LIST = SHOW_QUORUM_LIST.matcher(cmd_in);
            Matcher m_AUTO_REQUEST = AUTO_REQUEST.matcher(cmd_in);

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

            else if(m_RELEASE_TEST.find()){
                sendReleaseTest();
            }

            else if(m_SHOW_QUORUM_LIST.find()){
                printQuorumList();
            }

            else if(m_AUTO_REQUEST.find()){
                autoRequest();
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
        this.grantMessageCouter+=1;
        this.outStandingGrantCount -= 1;
        if(this.outStandingGrantCount == 0){
            this.enterCriticalSection();
        }

    }

    public void autoRequest(){
        Thread sendAuto = new Thread(){
            public void run(){
                try {
                    while(true) {
                        if(requestCounter < 20) {
                            System.out.println("Auto - Generating request - Client has set delay of: " + genRequestDelay);
                            Thread.sleep(genRequestDelay);
                            if (!requestedCS) {
                                sendRequest();
                            }
                        }
                    }
                }
                catch (Exception e){}
            }
        };
        sendAuto.setDaemon(true); 	// terminate when main ends
        sendAuto.start();
    }


    public void sendRequest(){
        this.requestedCS = true;
        this.requestCounter+=1;
        int randomNum = ThreadLocalRandom.current().nextInt(0, quorum.size() + 1);
        System.out.println("Chosen random number: " + randomNum + " but choosing index 0 for test purpose");
        List<String> quorumMembers = quorum.get(0);
        this.currentQuorumIndex = 0;
        Integer quorumMemberId ;
        this.outStandingGrantCount = quorumMembers.size();
        for(quorumMemberId = 0; quorumMemberId < quorumMembers.size(); quorumMemberId++){
            socketConnectionHashMapServer.get(quorumMembers.get(quorumMemberId)).sendRequest();
            this.requestMessageCounter+=1;
        }


    }

    public synchronized void enterCriticalSection(){
        System.out.println("******************In the critical section wait for three seconds******************");
        try {
            try {
                System.out.println();
                BufferedWriter writer = new BufferedWriter(new FileWriter("critical_section_log.txt", true));
                Date date = new Date();
                writer.append( this.getId()+" Client used critical section at timestamp -> "+ date.getTime()+"\n");
                writer.close();
            }
            catch (Exception e){
                System.out.println("STATUS FILE WRITE ERROR");
            }
            TimeUnit.MILLISECONDS.sleep(3000);
            System.out.println("******************Ending critical section wait for three seconds******************");
            this.releaseCriticalSection();
        }
        catch (Exception e){}

    }

    public synchronized void releaseCriticalSection(){
        System.out.println("******************SENDING RELEASE MESSAGE TO THE QUORUM******************");
        List<String> quorumMembers = quorum.get(this.currentQuorumIndex);
        Integer quorumMemberId ;
        for(quorumMemberId = 0; quorumMemberId < quorumMembers.size(); quorumMemberId++){
            socketConnectionHashMapServer.get(quorumMembers.get(quorumMemberId)).sendRelease();
            this.releaseMessageCounter+=1;
        }
        this.requestedCS = false;
        if(this.requestCounter == 20){
            sendStats();
        }
    }

    public synchronized void sendStats(){
        socketConnectionHashMapServer.get("0").sendStats("Request Message Counter: " + this.requestMessageCounter
                +" Release Message Counter: "+ this.releaseMessageCounter + " Grant Message Counter: " + this.grantMessageCouter);
    }


    public synchronized void pushServerStats(){
        Integer serverId;
        for(serverId = 0; serverId < socketConnectionListServer.size(); serverId ++){
            socketConnectionListServer.get(serverId).pushServerStats();
        }
    }
    /*Helps establish the socket connection to all the servers available*/
    public void setupServerConnection(Client current){
        try{
            System.out.println("CONNECTING SERVERS");
            Integer serverId;
            for (serverId =0; serverId < allServerNodes.size(); serverId ++){
                Socket serverConnection = new Socket(this.allServerNodes.get(serverId).getIpAddress(), Integer.valueOf(this.allServerNodes.get(serverId).getPort()));
                SocketForClient socketConnectionServer = new SocketForClient(serverConnection,this.getId(),current,String.valueOf(this.getAllClientNodes().size()));
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
            System.out.println("Client node running on port ****" + Integer.valueOf(this.allClientNodes.get(ClientId).port) +"," + "*** use ctrl-C to end");
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


    public void setQuorumList(){
        try {
            BufferedReader br = new BufferedReader(new FileReader("config_quorum.txt"));
            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    List<String> parsed_quorum = Arrays.asList(line.split(","));
                    this.quorum.add(parsed_quorum);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
            } finally {
                br.close();
            }
        }
        catch (Exception e) {
            System.out.println("Something went wrong while parsing the quorum" + e);
        }
    }

    public void printQuorumList(){
        Integer quorumId;

        for (quorumId = 0; quorumId < this.quorum.size(); quorumId ++){
            System.out.println("Quorum at ID: " + quorumId + " with quorum members: "+this.quorum.get(quorumId).toString());
        }
    }

    public static void main(String[] args) {

        if (args.length != 2)
        {
            System.out.println("Usage: java Client <client-number> <request-delay>");
            System.exit(1);
        }


        System.out.println("Starting the Client");

        Client C1 = new Client(args[0]);
        C1.setClientList();
        C1.setServerList();
        C1.setupServerConnection(C1);
        C1.clientSocket(Integer.valueOf(args[0]),C1);
        C1.setGenRequestDelay(Integer.valueOf(args[1]));
        C1.setQuorumList();
        System.out.println("Started Client with ID: " + C1.getId());
    }
}
