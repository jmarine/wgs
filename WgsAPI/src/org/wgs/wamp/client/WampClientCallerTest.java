package org.wgs.wamp.client;

import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


@WampModuleName("com.myapp")
public class WampClientCallerTest extends WampModule implements Runnable
{
    private static String  url = "ws://localhost:8082/wgs"; 
    private static String  realm = "localhost";
    private static String  user = null;
    private static String  password = null;
    private static boolean digestPasswordMD5 = true;

    
    private WampClient client;
    
    public WampClientCallerTest(WampClient client) {
        super(client.getWampApplication());
        client.getWampApplication().registerWampModule(this);
        this.client = client;
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
            
            doCalls(repeats);
            client.waitResponses();
            
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
        WampClientCallerTest test = new WampClientCallerTest(client);
        test.run();
    }

    
    
}