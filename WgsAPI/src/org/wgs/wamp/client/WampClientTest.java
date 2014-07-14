package org.wgs.wamp.client;

import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


@WampModuleName("com.myapp")
public class WampClientTest extends WampModule implements Runnable
{
    private static String user = null;
    private static String password = null;

    
    private WampClient client;
    
    public WampClientTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
    }
    
    
    @WampRPC(name = "add2")  // implicit RPC registration
    public Long add2(Long p1, Long p2, WampCallOptions options) 
    {
        System.out.println("Received call: authid=" + options.getAuthId() + ", authprovider=" + options.getAuthProvider() + ", authrole=" + options.getAuthRole() + ", caller session id=" + options.getCallerId());
        return p1+p2;
    }   
    

    @Override
    public void onEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        String topic = client.getTopicFromEventData(subscriptionId, details);
        System.out.println("OnEvent: topic=" + topic + ", publicationId=" + publicationId + ", payload=" + payload + ", payloadKw=" + payloadKw + ", " + details);
    }
 
    
    @Override
    public void run()
    {
        try {
            int repeats = 1000;
            
            client.connect();

            System.out.println("Login");
            client.hello("localhost", user, password);
            client.waitResponses();
            
            doCalls(repeats);
            client.waitResponses();
            

            System.out.println("Publication without subscription.");
            doPublications(repeats);
            client.waitResponses();
            
            System.out.println("Subscription");
            WampSubscriptionOptions subOpt = new WampSubscriptionOptions(null);
            subOpt.setMatchType(WampMatchType.prefix);
            client.subscribe("myapp", subOpt, null);
            client.waitResponses();
            
            System.out.println("Publication with subscription.");
            doPublications(repeats);
            client.waitResponses();
            
            client.unsubscribe("myapp", subOpt, null);
            client.waitResponses();
            System.out.println("Publication after unsubscription.");
            doPublications(repeats);
            client.waitResponses();

            
            client.goodbye("wamp.close.normal");
            
            
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
    
    public void doPublications(int num)
    {
        WampPublishOptions pubOpt = new WampPublishOptions();
        pubOpt.setAck(true);
        pubOpt.setExcludeMe(false);
        pubOpt.setDiscloseMe(true);
        
        for(int i = 0; i < num; i++) {
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
        }
    }
    
    public void doCalls(int num) {
        WampCallOptions callOptions = new WampCallOptions(null);
        callOptions.setDiscloseMe(true);
        callOptions.setExcludeMe(false);
        
        for(int i = 0; i < num; i++) {
            client.call("com.myapp.add2", new WampList(2,3), null, callOptions, new WampAsyncCallback() {
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
        client.setPreferredWampEncoding(WampEncoding.JSON);
        
        WampClientTest test = new WampClientTest(client);
        while(true) {
            test.run();
        }
    }
    

    
}
