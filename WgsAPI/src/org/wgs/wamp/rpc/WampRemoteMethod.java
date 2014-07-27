package org.wgs.wamp.rpc;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdeferred.Deferred;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampResult;
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
    public Promise invoke(final WampCallController task, final WampSocket clientSocket, final WampList args, final WampDict argsKw, final WampCallOptions callOptions) throws Exception
    {
        DeferredObject<WampResult,WampException,WampResult> deferred = new DeferredObject<WampResult,WampException,WampResult>();
        Promise<WampResult,WampException,WampResult> promise = deferred.promise();
        promise.fail(new FailCallback<WampException>() {
            @Override
            public void onFail(WampException f) {
                if(f.getErrorURI().equals("wgs.cancel_invocation")) {
                    WampProtocol.sendInterruptMessage(remotePeer, f.getInvocationId(), f.getDetails());
                }
            }
        });
        
        final Long invocationId = WampProtocol.newId();
        task.addRemoteInvocation(invocationId, deferred);
        remotePeer.addInvocationController(invocationId, task);
        remotePeer.addInvocationAsyncCallback(invocationId, deferred);

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
            task.decrementPendingInvocationCount();
            WampProtocol.sendInvocationMessage(remotePeer, invocationId, registrationId, invocationOptions, args, argsKw);
            if(!remotePeer.isOpen()) throw new Exception();
        } catch(Exception ex) {
            task.removeRemoteInvocation(invocationId);
        }

        return promise;
    }    
    
}
