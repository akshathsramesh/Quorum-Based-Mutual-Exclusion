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
    Integer grantMessageCounter = 0;
    Integer requestCounter = 0;
    Integer simulationCount = 1;
    Long requestTimeStamp;
    Long criticalSectionTimeStamp;


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
        /*Pattern matching, command parsing from terminal*/
        Pattern STATUS = Pattern.compile("^STATUS$");
        Pattern SERVER_TEST = Pattern.compile("^SERVER_TEST$");
        Pattern REQUEST_TEST = Pattern.compile("^REQUEST_TEST$");
        Pattern RELEASE_TEST = Pattern.compile("^RELEASE_TEST$");
        Pattern SHOW_QUORUM_LIST = Pattern.compile("^SHOW_QUORUM_LIST$");
        Pattern AUTO_REQUEST = Pattern.compile("^AUTO_REQUEST$");
        Pattern SET_ELAPSE = Pattern.compile("^SET_ELAPSE$");

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
            Matcher m_SET_ELAPSE = SET_ELAPSE.matcher(cmd_in);

            if(m_STATUS.find()){
                System.out.println("CLIENT SOCKET STATUS:");
                try {
                    System.out.println("STATUS:  UP");
                    System.out.println("CLIENT ID: " + Id);
                    System.out.println("CLIENT IP ADDRESS: " + ipAddress);
                    System.out.println("CLIENT PORT: " + port);
                    System.out.println("GEN DELAY: " + genRequestDelay);
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

            else if(m_SET_ELAPSE.find()){
                int i =1;
                while (i==1) {
                    if (cmd.hasNext()) {
                        String timeElapse = cmd.nextLine();
                        System.out.println("Setting Time Elapse between request to: " + timeElapse);
                        genRequestDelay = Integer.valueOf(timeElapse);
                        i+=1;
                    }
                }
            }

            return 1;
        }

        public void run() {
            Scanner input = new Scanner(System.in);
            while(rx_cmd(input) != 0) { }
        }
    }
    /*Test to check Server connection */
    public void sendServerTest(){
        Integer serverId;
        for (serverId = 0; serverId < this.socketConnectionListServer.size(); serverId++){
            socketConnectionListServer.get(serverId).serverTest();
        }
    }

    /*Test functionality to send REQUEST message*/
    public void sendRequestTest(){
        Integer serverId;
        for (serverId = 0; serverId < this.socketConnectionListServer.size(); serverId++){
            socketConnectionListServer.get(serverId).serverRequestTest();
        }
    }

    /*Test to send RELEASE message*/
    public void sendReleaseTest(){
        Integer serverId;
        for (serverId = 0; serverId < this.socketConnectionListServer.size(); serverId++){
            socketConnectionListServer.get(serverId).serverReleaseTest();
        }
    }

    /*Process grant message received from client*/
    public synchronized void processGrant(String serverSendingGrant){
        System.out.println("Inside process grant for server ID "+ serverSendingGrant);
        this.grantMessageCounter+=1;
        this.outStandingGrantCount -= 1;
        if(this.outStandingGrantCount == 0){
            this.enterCriticalSection(); // once all the grant messages are received the client enters the critical section
        }

    }

    /*Generates REQUEST every Request delay time lapse; Only if it has not already requested*/
    public void autoRequest(){
        Thread sendAuto = new Thread(){
            public void run(){
                try {
                    while(true) {
                        if(requestCounter < 20) { // Stop sending request after 20 simulation
//                          System.out.println("Auto - Generating request - Client has set delay of: " + genRequestDelay);
                            Thread.sleep(genRequestDelay);
                            if (!requestedCS) {
                                System.out.println("REQUESTING CS");
                                sendRequest();
                            }
//                            else {
//                                System.out.println(" REQUESTED CS WAITING");
//                            }
                        }
                        else {
                            System.out.println("COMPLETED SIMULATION"); // On completing simulation
                            Thread.sleep(10000);
                        }
                    }
                }
                catch (Exception e){
                    System.out.println("AUTO GENERATE EXCEPTION" + e);
                }
            }
        };
        sendAuto.setDaemon(true); 	// terminate when main ends
        sendAuto.start();
    }

    /*Restart mechanism on entering deadlock*/
    public synchronized void processRestartTrigger(){
        System.out.println("Process Restart Trigger");
        this.requestedCS = false;
    }

    /*Set necessary variable and send out request message*/
    public void sendRequest(){
        this.requestedCS = true;
        Date date = new Date();
        this.requestTimeStamp = date.getTime();
        this.requestCounter+=1;
        int randomNum = ThreadLocalRandom.current().nextInt(0, quorum.size());
        System.out.println("Chosen random number: " + randomNum );
//        List<String> quorumMembers = quorum.get(0);
//        this.currentQuorumIndex = 0;
        List<String> quorumMembers = quorum.get(randomNum);
        this.currentQuorumIndex = randomNum;
        Integer quorumMemberId ;
        this.outStandingGrantCount = quorumMembers.size();
        for(quorumMemberId = 0; quorumMemberId < quorumMembers.size(); quorumMemberId++){
            socketConnectionHashMapServer.get(quorumMembers.get(quorumMemberId)).sendRequest();
            this.requestMessageCounter+=1;
        }


    }

    /*Write to file using critical section*/
    public synchronized void enterCriticalSection(){
        System.out.println("******************In the critical section wait for three seconds******************");
        try {
            try {
                System.out.println();
                BufferedWriter writer = new BufferedWriter(new FileWriter("critical_section_log.txt", true));
                Date dateCS = new Date();
                writer.append( this.getId()+" Client used critical section at timestamp -> "+ dateCS.getTime()+" : Time elapsed: " + (dateCS.getTime() - this.requestTimeStamp) +"\n");
                writer.close();
            }
            catch (Exception e){
                System.out.println("STATUS FILE WRITE ERROR");
            }
            TimeUnit.MILLISECONDS.sleep(3);
            this.releaseCriticalSection();
        }
        catch (Exception e){}

    }

    /*varaiable reset after critical section use*/
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
            sendStats(); // after completing 20 request - send out the stats
        }
        System.out.println("******************EXITING RELEASE ******************");
    }

    /*Send statts to sever 0*/
    public synchronized void sendStats(){
        socketConnectionHashMapServer.get("0").sendStats("Request Message Counter: " + this.requestMessageCounter
                +" Release Message Counter: "+ this.releaseMessageCounter + " Grant Message Counter: " + this.grantMessageCounter);
    }
    
    /*Restart functionality after deadlock */
    public synchronized void clearClient(){
        try {
            if(this.getId().equals("0")) {
            this.simulationCount += 1;
            BufferedWriter writercs = new BufferedWriter(new FileWriter("critical_section_log.txt", true));
            writercs.append( "************* SIMULATION COUNT:" + this.simulationCount +" *************" +"\n");
            writercs.close();
            BufferedWriter writerst = new BufferedWriter(new FileWriter("stat.txt", true));
            writerst.append( "************* SIMULATION COUNT:" + this.simulationCount +" *************" +"\n");
            writerst.close();
            }
        }
        catch (Exception e){
            System.out.println("STATUS FILE WRITE ERROR");
        }

        this.outStandingGrantCount = 0;
        this.currentQuorumIndex = 0;
        this.requestMessageCounter = 0;
        this.releaseMessageCounter = 0;
        this.grantMessageCounter = 0;
        this.requestCounter = 0;
        this.requestedCS = true;
        Integer serverId;
        if(this.getId().equals("0")) {
            System.out.println("SEND SERVER RESTART");
            for (serverId = 0; serverId < socketConnectionListServer.size(); serverId++) {
                socketConnectionListServer.get(serverId).sendServerRestart();
            }
        }


    }

    /*After all Clients complete simulation Client 0 server connection will be used to push server stats */
    public synchronized void pushServerStats(){
        System.out.println("SEND PUSH SERVER STATS TO ALL SERVERS");
        Integer serverId;
        for(serverId = 0; serverId < socketConnectionListServer.size(); serverId ++){
            try {
                TimeUnit.SECONDS.sleep(1);
                socketConnectionListServer.get(serverId).pushServerStats();
            }
            catch (Exception e){
                System.out.println("Error while sleep of client sending push notification to server");
            }
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

    /*Used config quorum file to populate the list*/
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

        Client C1 = new Client(args[0]); // Create Client instance
        C1.setClientList(); //Reads from client file and adds it to list of clients
        C1.setServerList(); // Reads from config_server file and adds it to list of server
        C1.setupServerConnection(C1); // Used the method to establish TCP connection to serve
        C1.clientSocket(Integer.valueOf(args[0]),C1); // Reserve socket with port number
        C1.setGenRequestDelay(Integer.valueOf(args[1])); // set delay between two requests from same client
        C1.setQuorumList(); // read from config quorum and set it to list of quorum
        System.out.println("Started Client with ID: " + C1.getId());
    }
}
