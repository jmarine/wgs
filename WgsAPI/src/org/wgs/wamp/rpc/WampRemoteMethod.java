package org.wgs.wamp.rpc;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampRemoteMethod extends WampMethod
{
    private static final Logger logger = Logger.getLogger(WampRemoteMethod.class.getName());    
    
    private Long registrationId;
    private WampSocket remotePeer;
    private WampMatchType matchType;
    private WampDict options;

    
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

        WampAsyncCall asyncCall = new WampAsyncCall(callback) {
            
            @Override
            public Void call() throws Exception {

                WampDict invocationOptions = new WampDict();
                if(matchType != WampMatchType.exact) invocationOptions.put("procedure", task.getProcedureURI());
                if(callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) invocationOptions.put("receive_progress", true);
                if(callOptions.hasDiscloseMe()) {
                    invocationOptions.put("caller", clientSocket.getSessionId());
                    invocationOptions.put("authid", clientSocket.getAuthId());
                    invocationOptions.put("authprovider", clientSocket.getAuthProvider());
                    invocationOptions.put("authrole", clientSocket.getAuthRole());
                }
                
                if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "CALL " + task.getCallID() + ": SENDING INVOCATION ID: " + invocationId + " (" + clientSocket.getSessionId() + " --> " + remotePeer.getSessionId() + ")");
                try {
                    WampProtocol.sendInvocationMessage(remotePeer, invocationId, registrationId, invocationOptions, args, argsKw);
                    if(!remotePeer.isOpen()) task.removeRemoteInvocation(invocationId);
                } catch(Exception ex) {
                    task.removeRemoteInvocation(invocationId);
                }
                    
                return null;
            }

            @Override
            public void cancel(WampDict cancelOptions) {
                WampProtocol.sendInterruptMessage(remotePeer, invocationId, cancelOptions);
            }           
            
        };

        task.addRemoteInvocation(invocationId, asyncCall);
        remotePeer.addInvocationController(invocationId, task);
        remotePeer.addInvocationAsyncCallback(invocationId, callback);
        return asyncCall;
    }    
    
}
