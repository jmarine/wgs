package org.wgs.wamp.client;

import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import org.wgs.security.WampCRA;
import org.wgs.util.HexUtils;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampClientTest extends WampModule implements Runnable
{
    private static String user = "magda";
    private static String password = "magda";

    
    private WampClient client;
    
    public WampClientTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
    }
    
    
    @Override
    public void run()
    {
        try {
            client.connect();

            client.hello("localhost", user, password);
            client.waitPendingMessages();
            
            client.goodbye("wamp.close.normal");
            
            client.hello("localhost", null, null);
            client.waitPendingMessages();
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    
    @Override
    public void onChallenge(WampSocket clientSocket, String authMethod, WampDict details) throws Exception { 
        
    }

    
    @Override
    public void onSessionEstablished(WampSocket clientSocket, WampDict details) { 
        System.out.println("WampClientModule: session established");
      
        client.call("wgs.get_user_info", null, null, null, new WampAsyncCallback() {
            @Override
            public void resolve(Object... results) {
                WampDict resultKw = (WampDict)results[3];
                System.out.println("Hello " + resultKw.getText("name"));
            }

            @Override
            public void progress(Object... progress) {
                System.out.println("Progress: " + progress);
            }

            @Override
            public void reject(Object... errors) {
                System.out.println("Error: " + errors);
            }
        });
      
    }
        

    
    public static final void main(String args[]) throws Exception
    {
        WampClient client = new WampClient("ws://localhost:8080/wgs");
        WampClientTest test = new WampClientTest(client);
        test.run();
    }
    

    
}
