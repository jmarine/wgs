package org.wgs.wamp.client.test;

import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampSubscribed;
import org.wgs.wamp.client.WampClient;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


@WampModuleName("com.myapp")
public class WampClientAuthTest extends WampModule implements Runnable
{
    private static String  url = "ws://localhost:8080/ws"; 
    private static String  realm = "realm1";
    private static String  user = "joe";
    private static String  password = "secret1";
    private static boolean digestPasswordMD5 = false;

    
    private WampClient client;
    
    public WampClientAuthTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
    }

    //@WampSubscribed(topic = "wamp.metaevent.session.on_join", match = WampMatchType.exact)
    @WampSubscribed(topic = "test", match = WampMatchType.exact, metatopics = {"wamp.topic.on_subscribe","wamp.topic.on_unsubscribe"})
    public void onMyAppEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        String topic = client.getTopicFromEventData(subscriptionId, details);
        System.out.println("OnEvent: topic=" + topic + ", publicationId=" + publicationId + ", payload=" + payload + ", payloadKw=" + payloadKw + ", " + details);
    }    
 
    
    @Override
    public void run()
    {
        try {
            int repeats = 1;
           
            System.out.println("Connecting");
            client.connect();

            System.out.println("Connected");
            
            client.hello(realm, user, password, digestPasswordMD5);
            client.waitResponses();
            
            //listSessions();
            //client.waitResponses();
            System.out.println("Press a key to exit.");
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
    

    
    public void listSessions() {
        WampCallOptions callOptions = new WampCallOptions(null);
        callOptions.setRunOn(WampCallOptions.RunOnEnum.all);
        callOptions.setRunMode(WampCallOptions.RunModeEnum.gather);
        callOptions.setDiscloseMe(true);
        callOptions.setExcludeMe(false);
        
        client.call("wamp.session.list", new WampList(), null, callOptions)
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
        
    
    public static final void main(String args[]) throws Exception
    {
        WampClient client = new WampClient(url);
        client.setPreferredWampEncoding(WampEncoding.JSON);
        WampClientAuthTest test = new WampClientAuthTest(client);
        test.run();
    }

    
    
}