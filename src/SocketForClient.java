import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketForClient {

    Socket otherClient;
    String my_id;
    String remote_id;
    BufferedReader in;
    PrintWriter out;
    Boolean Initiator;
    Client my_master;

    public String getRemote_id() {
        return remote_id;
    }

    public void setRemote_id(String remote_id) {
        this.remote_id = remote_id;
    }

    public SocketForClient(Socket otherClient, String myId, Boolean Initiator, Client my_master) {
        this.otherClient = otherClient;
        this.my_id = myId;
        this.my_master = my_master;
        try{
            in = new BufferedReader(new InputStreamReader(this.otherClient.getInputStream()));
            out = new PrintWriter(this.otherClient.getOutputStream(), true);
        }
        catch (Exception e){

        }

        try {
            if(!Initiator) {
                out.println("SEND_ID");
                System.out.println("SEND_ID request sent");
                remote_id = in.readLine();
                System.out.println("SEND_ID request response received with ID: " + remote_id);
            }
        }

        catch (Exception e){

        }
        Thread read = new Thread(){
            public void run(){
                while(rx_cmd(in,out) != 0) { }
            }
        };
        read.setDaemon(true); 	// terminate when main ends
        read.start();
    }


    public int rx_cmd(BufferedReader cmd,PrintWriter out) {
        try {
            String cmd_in = cmd.readLine();
            if(cmd_in.equals("P")){
                System.out.println("P recieved from sender");
            }

            else if(cmd_in.equals("SEND_ID")){
                out.println(this.my_id);
            }

            else if(cmd_in.equals("SEND_CLIENT_ID")){
                out.println(this.my_id);
            }

        }
        catch (Exception e){}
        return 1;
    }


    public synchronized void publish() {
        out.println("P");
    }


    public synchronized  void serverWriteTest() {
        out.println("WRITE_TEST");
    }




    public synchronized void read(String fileName){
        System.out.println("Sending read request from Client ID: " + this.my_id +" to server with SERVER ID: " + this.getRemote_id());
        out.println("READ_FROM_FILE");
        out.println(fileName);
        out.println(this.my_id);
    }


    public synchronized void request(Integer logicalClock, String fileName ){
        System.out.println("SENDING REQ FROM CLIENT WITH CLIENT ID: " + this.my_id +" to remote CLIENT ID: " + this.getRemote_id() + " for file: "+ fileName);
        out.println("REQ");
        out.println(this.my_id);
        out.println(logicalClock);
        out.println(fileName);
    }

    public synchronized void reply(String fileName){
        System.out.println("SENDING REP FROM CLIENT" + this.my_id +" TO remote CLIENT ID" + this.getRemote_id() + " for file: "+ fileName);
        out.println("REP");
        out.println(this.my_id);
        out.println(fileName);
    }


    public synchronized  void sendEnquire(){
        System.out.println("Send Enquire to Server");
        out.println("ENQUIRE");
        out.println(this.my_id);
    }

    public Socket getOtherClient() {
        return otherClient;
    }

    public void setOtherClient(Socket otherClient) {
        this.otherClient = otherClient;
    }
}
