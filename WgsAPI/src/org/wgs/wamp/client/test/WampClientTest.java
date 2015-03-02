package org.wgs.wamp.client.test;

import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.annotation.WampSubscribed;
import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


@WampModuleName("com.myapp")
public class WampClientTest extends WampModule implements Runnable
{
    private static String  url = "ws://localhost:8080/wgs";
    private static String  realm = "localhost";
    private static String  user = null;
    private static String  password = null;
    private static boolean digestPasswordMD5 = false;

    
    private WampClient client;
    
    public WampClientTest(WampClient client) {
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
    

    //FIXME: why this annotation generates deadlocks on JMS cluster, and programmatic subscriptions doesn't?
    //@WampSubscribed(topic = "myapp", match = WampMatchType.prefix)
    //public void onMyAppEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
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
            int repeats = 10;
           
            System.out.println("Connecting");
            client.connect();

            System.out.println("Connected");
            
            client.hello(realm, user, password, digestPasswordMD5);
            client.waitResponses();

            System.out.println("Publication without subscription.");
            doPublications(repeats);
            client.waitResponses();

            System.out.println("Subscription");
            WampSubscriptionOptions subOpt = new WampSubscriptionOptions(null);
            subOpt.setMatchType(WampMatchType.prefix);
            client.subscribe("myapp", subOpt);  // received in onEvent method of registered modules
            client.waitResponses();
            
            System.out.println("Publication with annotated subscription.");
            doPublications(repeats);
            client.waitResponses();

            doCalls(repeats);
            client.waitResponses();            
            
            client.unsubscribe("myapp", subOpt);
            client.waitResponses();
            System.out.println("Publication after unsubscription.");
            doPublications(repeats);
            client.waitResponses();
            
            System.out.println("Closing session");
            client.goodbye("wamp.close.normal");
            client.waitResponses();

            System.out.println("Disconnection");
            client.close();
            
            
        } catch(Exception ex) {

            System.err.println("Error: " + ex.getMessage());
        }
    }

    
    @Override
    public void onWampSessionEstablished(WampSocket clientSocket, WampDict details) 
    { 
        super.onWampSessionEstablished(clientSocket, details);
        System.out.println("Hello " + details.getText("authid"));        
    }
    
    public void doPublications(int num)
    {
        WampPublishOptions pubOpt = new WampPublishOptions();
        pubOpt.setAck(true);
        pubOpt.setExcludeMe(false);
        pubOpt.setDiscloseMe(true);
        
        for(int i = 0; i < num; i++) {
            client.publish("myapp.topic1", new WampList("Hello, world from Java!!!"), null, pubOpt)
                    .done(new DoneCallback<Long>() {
                        @Override
                        public void onDone(Long id) {
                            System.out.println("Event published with id: " + id);
                        }})
                    .fail(new FailCallback<WampException>() {
                        @Override
                        public void onFail(WampException e) {
                            System.out.println("Error: " + e.toString());
                        }});
        }
    }
    
    public void doCalls(int num) {
        WampCallOptions callOptions = new WampCallOptions(null);
        callOptions.setRunOn(WampCallOptions.RunOnEnum.all);
        callOptions.setRunMode(WampCallOptions.RunModeEnum.gather);
        callOptions.setDiscloseMe(true);
        callOptions.setExcludeMe(false);
        
        for(int i = 0; i < num; i++) {
            client.call("com.myapp.add2", new WampList(2,3), null, callOptions)
                    .done(new DoneCallback<WampResult>() { 
                        @Override
                        public void onDone(WampResult r) {
                            System.out.println(r.getArgs());
                        }})
                    .progress(new ProgressCallback<WampResult>() {
                        @Override
                        public void onProgress(WampResult p) {
                            System.out.println("Progress: " + p.getArgs());
                        }})
                    .fail(new FailCallback<WampException>() {
                        @Override
                        public void onFail(WampException e) {
                            System.out.println("Error: " + e.toString());
                        }
                    });
        }
        
    }
        
    
    public static final void main(String args[]) throws Exception
    {
        WampClient client = new WampClient(url);
        client.setPreferredWampEncoding(WampEncoding.JSON);

        WampClientTest test = new WampClientTest(client);
        while(true) {        
            test.run();
        }
    }

}