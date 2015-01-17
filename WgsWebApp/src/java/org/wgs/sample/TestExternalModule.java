package org.wgs.sample;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.client.WampClientConfig;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;

@WampModuleName("com.myapp")
@WampClientConfig(url = "http://localhost:8082/wgs", realm = "localhost")
public class TestExternalModule extends WampModule 
{
    public TestExternalModule(WampApplication app) {
        super(app);
    }
    
    @WampRPC(name = "add2")  // implicit RPC registration
    public Long add2(Long p1, Long p2, WampCallOptions options, WampCallController task) 
    {
        System.out.println("Received invocation: " + task.getProcedureURI() + ": authid=" + options.getAuthId() + ", authprovider=" + options.getAuthProvider() + ", authrole=" + options.getAuthRole() + ", caller session id=" + options.getCallerId() + ", invocation id=" + task.getCallID());
        return p1+p2;
    }   
    
}
