package org.wgs.wamp.client;

import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import org.wgs.security.WampCRA;
import org.wgs.util.HexUtils;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


@WampModuleName("client")
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
    
    
    @WampRPC(name = "reverse_list")
    public WampList reverseList(WampList list) 
    {
        WampList retval = new WampList();
        for(int i = list.size()-1; i >= 0; i--) {
            retval.add(list.get(i));
        }
        return retval;
    }
    
    @Override
    public void run()
    {
        try {
            client.connect();

            client.hello("localhost", user, password);
            client.waitPendingMessages();
            
            client.goodbye("wamp.close.normal");
            
            client.hello("localhost", null);
            client.waitPendingMessages();
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
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
        
        WampList list = new WampList();
        list.add(1);
        list.add(2);
        list.add(3);
        client.call("client.reverse_list", list, null, null, new WampAsyncCallback() {
            @Override
            public void resolve(Object... results) {
                WampList result = (WampList)results[2];
                System.out.println(result.toString());
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
