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
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


@WampModuleName("com.myapp")
public class WampClientTest extends WampModule implements Runnable
{
    private static String user = null;      //"username";
    private static String password = null;  //"secret";

    
    private WampClient client;
    
    public WampClientTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
    }
    
    
    @WampRPC(name = "add2")  // implicit RPC registration
    public Long add2(Long p1, Long p2) 
    {
        return p1+p2;
    }   
    
    
    @WampRPC(name = "reverse_list")   // implicit RPC registration
    public WampList reverseList(WampList list) 
    {
        WampList retval = new WampList();
        for(int i = list.size()-1; i >= 0; i--) {
            retval.add(list.get(i));
        }
        return retval;
    }

    @Override
    public void onEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        String topic = client.getTopicFromEventData(subscriptionId, details);
        System.out.println("OnEvent: topic=" + topic + ", publicationId=" + publicationId + ": payload=" + payload + ", payloadKw=" + payloadKw);
    }
 
    
    @Override
    public void run()
    {
        try {
            
            client.connect();

            client.hello("localhost", user, password);
            client.waitResponses();
            client.goodbye("wamp.close.normal");
            
            client.close();

            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    
    @Override
    public void onSessionEstablished(WampSocket clientSocket, WampDict details) { 
        System.out.println("WampClientModule: session established");
      
        System.out.println("Hello " + details.getText("authid"));
        
        doWork(100);
    }
    
    public void doWork(int num)
    {
        WampSubscriptionOptions subOpt = new WampSubscriptionOptions(null);
        subOpt.setMatchType(WampMatchType.prefix);
        client.subscribe("myapp", subOpt, null);

        WampPublishOptions pubOpt = new WampPublishOptions();
        pubOpt.setAck(true);
        pubOpt.setExcludeMe(false);
        
        for(int i = 1; i < num; i++) {
            client.publish("myapp.topic1", new WampList("'Hello, world from Java!!!"), null, pubOpt, new WampAsyncCallback() {
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
                    System.out.println(result);
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
        
    }
        

    
    public static final void main(String args[]) throws Exception
    {
        WampClient client = new WampClient("ws://localhost:8080/wgs");
        client.setPreferredWampEncoding(WampEncoding.MsgPack);
        
        WampClientTest test = new WampClientTest(client);
        test.run();
    }
    

    
}
