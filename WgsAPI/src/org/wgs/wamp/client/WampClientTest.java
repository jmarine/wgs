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
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


@WampModuleName("com.myapp")
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
    
    
    @WampRPC(name = "add2")
    public Long add2(Long p1, Long p2) 
    {
        return p1+p2;
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
            client.goodbye("wamp.close.normal");
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    
    @Override
    public void onSessionEstablished(WampSocket clientSocket, WampDict details) { 
        System.out.println("WampClientModule: session established");
      
        System.out.println("Hello " + details.getText("authid"));
        
        doWork();
    }
    
    public void doWork()
    {
        WampPublishOptions options = new WampPublishOptions();
        options.setAck(true);
        client.publish("myapp.topic1", new WampList("'Hello, world from Java!!!"), null, options, new WampAsyncCallback() {
            @Override
            public void resolve(Object... results) {
                System.out.println("Event published with id: " + results[1]);
            }

            @Override
            public void progress(Object... progress) {
            }

            @Override
            public void reject(Object... errors) {
                System.out.println("Error: " + errors);
            }
        });
        
        client.call("com.myapp.add2", new WampList(2,3), null, null, new WampAsyncCallback() {
            @Override
            public void resolve(Object... results) {
                WampList result = (WampList)results[2];
                System.out.println(result.getLong(0));
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
        client.setPreferredWampEncoding(WampEncoding.MsgPack);
        
        WampClientTest test = new WampClientTest(client);
        test.run();
    }
    

    
}
