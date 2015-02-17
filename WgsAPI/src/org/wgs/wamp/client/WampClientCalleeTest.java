package org.wgs.wamp.client;

import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.type.WampDict;



@WampModuleName("com.myapp")
public class WampClientCalleeTest extends WampModule implements Runnable
{
    private static String  url = "ws://localhost:8080/wgs"; 
    private static String  realm = "localhost";
    private static String  user = null;
    private static String  password = null;
    private static boolean digestPasswordMD5 = true;

    
    private WampClient client;
    
    public WampClientCalleeTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
    }
    
    
    @WampRPC(name = "add2")  // implicit RPC registration
    public Long add2(Long p1, Long p2, WampCallOptions options, WampCallController task) 
    {
        System.out.println("Received invocation: " + task.getProcedureURI() + ": authid=" + options.getAuthId() + ", authprovider=" + options.getAuthProvider() + ", authrole=" + options.getAuthRole() + ", caller session id=" + options.getCallerId() + ", invocation id=" + task.getCallID());
        return p1+p2;
    }   
    

    @Override
    public void run()
    {
        try {
          
            System.out.println("Connecting");
            client.connect();

            System.out.println("Connected");
            
            client.hello(realm, user, password, digestPasswordMD5);
            client.waitResponses();

            System.out.println("Press a key to stop application.");
            System.in.read();
            
            client.close();
            
        } catch(Exception ex) {

            System.err.println("Error: " + ex.getMessage());
        }
    }

    
    @Override
    public void onSessionEstablished(WampSocket clientSocket, WampDict details) 
    { 
        System.out.println("Hello " + details.getText("authid"));
        // doWork(10);
    }
    
    
    public static final void main(String args[]) throws Exception
    {
        WampClient client = new WampClient(url);
        client.setPreferredWampEncoding(WampEncoding.JSON);
        WampClientCalleeTest test = new WampClientCalleeTest(client);
        test.run();
    }

    
    
}