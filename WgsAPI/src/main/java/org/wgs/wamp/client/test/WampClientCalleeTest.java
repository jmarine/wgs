package org.wgs.wamp.client.test;

import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRegisterProcedure;
import org.wgs.wamp.client.WampClient;
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
    private static boolean stop = false;

    
    private WampClient client;
    
    public WampClientCalleeTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
    }
    
    
    @WampRegisterProcedure(name = "add2")  // implicit RPC registration
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
            stop = true;
            
            client.close();
            
        } catch(Exception ex) {

            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    
    @Override
    public void onWampSessionEstablished(WampSocket clientSocket, WampDict details) 
    { 
        System.out.println("Hello " + details.getText("authid"));
        // doWork(10);
    }
    
    
    @Override
    public void onWampSessionEnd(WampSocket clientSocket) 
    {
        System.out.println("Wamp session ended");
        super.onWampSessionEnd(clientSocket);
        while(!stop && !client.isOpen()) {
            try {
                System.out.println("Trying to reconnect to wamp router.....");
                client.connect();

                System.out.println("Connected");
                client.hello(realm, user, password, digestPasswordMD5);
            } catch(Exception ex) {
                System.out.println("Error reconnecting to wamp router: " + ex.getClass().getName() + ": " + ex.getMessage());
                try { Thread.sleep(1000); }
                catch(Exception ex2) { }
            }
        }
    }
    
    
    
    public static final void main(String args[]) throws Exception
    {
        WampClient client = new WampClient(url);
        client.setPreferredWampEncoding(WampEncoding.JSON);
        WampClientCalleeTest test = new WampClientCalleeTest(client);
        test.run();
    }

    
    
}