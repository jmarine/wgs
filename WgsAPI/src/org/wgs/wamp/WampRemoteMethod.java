package org.wgs.wamp;

import java.util.HashMap;


public class WampRemoteMethod implements WampMethod
{
    private Long   registrationId;
    private String procedureURI;
    private WampSocket remotePeer;
    
    private HashMap<Long,WampSocket> pendingInvocations = new HashMap<Long,WampSocket>();
    
    
    public WampRemoteMethod(Long registrationId, String procedureURI, WampSocket remotePeer)
    {
        this.registrationId = registrationId;
        this.procedureURI = procedureURI;
        this.remotePeer = remotePeer;
    }
    
    public WampSocket getRemotePeer()
    {
        return remotePeer;
    }
    
    public String getProcedureURI()
    {
        return procedureURI;
    }
            
    @Override
    public Object invoke(WampCallController task, WampSocket clientSocket, final WampList args, final WampDict argsKw, final WampCallOptions callOptions) throws Exception
    {
        final Long invocationId = WampProtocol.newId();
        remotePeer.addRpcController(invocationId, task);

        return new WampAsyncCall() {

            @Override
            public void call() throws Exception {
                WampDict invocationOptions = new WampDict();
                WampList msg = new WampList();
                msg.add(80);
                msg.add(invocationId);
                msg.add(registrationId);
                msg.add(invocationOptions);
                msg.add(args);
                msg.add(argsKw);                
                remotePeer.sendWampMessage(msg); 
            }

            @Override
            public void cancel(WampDict cancelOptions) {
                WampList msg = new WampList();
                msg.add(81);
                msg.add(invocationId);
                msg.add(cancelOptions);
                remotePeer.sendWampMessage(msg);
            }            
        };

    }    
    
}
