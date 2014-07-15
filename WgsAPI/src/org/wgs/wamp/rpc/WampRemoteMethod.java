package org.wgs.wamp.rpc;

import java.util.HashMap;
import org.wgs.wamp.type.WampMatchType;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.WampSocket;


public class WampRemoteMethod extends WampMethod
{
    private Long registrationId;
    
    private WampSocket remotePeer;
    
    private WampMatchType matchType;
    
    private WampDict options;

    private HashMap<Long,WampSocket> pendingInvocations = new HashMap<Long,WampSocket>();
    
    
    public WampRemoteMethod(Long registrationId, WampSocket remotePeer, WampMatchType matchType, WampDict options)
    {
        super(null);
        this.registrationId = registrationId;
        this.remotePeer = remotePeer;
        this.matchType = matchType;
        this.options = options;
    }
    
    
    public boolean hasPartition(String partition)
    {
        if(options != null && options.has("partition")) {
            String regExp = options.getText("paritition");
            return partition == null || WampBroker.isUriMatchingWithRegExp(partition, regExp);
        } else {
            return true;
        }
        
    }
    
    public WampSocket getRemotePeer()
    {
        return remotePeer;
    }
    
    
    @Override
    public Object invoke(final WampCallController task, final WampSocket clientSocket, final WampList args, final WampDict argsKw, final WampCallOptions callOptions, final WampAsyncCallback callback) throws Exception
    {
        final Long invocationId = WampProtocol.newId();

        return new WampAsyncCall(callback) {
            
            @Override
            public Void call() throws Exception {
                remotePeer.addRpcController(invocationId, task);
                remotePeer.addAsyncCallback(invocationId, callback);

                WampDict invocationOptions = new WampDict();
                if(matchType != WampMatchType.exact) invocationOptions.put("procedure", task.getProcedureURI());
                if(callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) invocationOptions.put("receive_progress", true);
                if(callOptions.hasDiscloseMe()) {
                    invocationOptions.put("caller", clientSocket.getSessionId());
                    invocationOptions.put("authid", clientSocket.getAuthId());
                    invocationOptions.put("authprovider", clientSocket.getAuthProvider());
                    invocationOptions.put("authrole", clientSocket.getAuthRole());
                }
                
                //System.out.println("INVOCATION " + remotePeer.getSessionId() + " (" + invocationId + ")");
                WampProtocol.sendInvocationMessage(remotePeer, invocationId, registrationId, invocationOptions, args, argsKw);
                    
                return null;
            }

            @Override
            public void cancel(WampDict cancelOptions) {
                WampProtocol.sendInterruptMessage(remotePeer, invocationId, cancelOptions);
            }           
            
        };


    }    
    
}
