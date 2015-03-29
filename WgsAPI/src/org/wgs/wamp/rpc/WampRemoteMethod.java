package org.wgs.wamp.rpc;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdeferred.Deferred;
import org.jdeferred.DoneCallback;
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
    private Long calleeSessionId;
    private WampMatchType matchType;
    private WampDict regOptions;

    
    public WampRemoteMethod(Long registrationId, String methodName, WampSocket remotePeer, Long clientSessionId, WampMatchType matchType, WampDict options)
    {
        super(methodName);
        this.registrationId = registrationId;
        this.remotePeer = remotePeer;
        this.calleeSessionId = clientSessionId;
        this.matchType = matchType;
        this.regOptions = options;
    }
    
    
    public boolean hasPartition(String partition)
    {
        if(regOptions != null && regOptions.has("partition")) {
            String regExp = regOptions.getText("paritition");
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
    public Promise<WampResult, WampException, WampResult> invoke(final WampCallController task, final WampSocket clientSocket, final WampList args, final WampDict argsKw, final WampCallOptions callOptions) throws Exception
    {
        final Long invocationId = WampProtocol.newSessionScopeId(remotePeer);
        
        DeferredObject<WampResult,WampException,WampResult> deferred = new DeferredObject<WampResult,WampException,WampResult>();
        Promise<WampResult,WampException,WampResult> promise = deferred.promise();
        promise.done(new DoneCallback<WampResult>() {
            @Override
            public void onDone(WampResult d) {
                remotePeer.removeInvocation(invocationId);
            }
        });
        promise.fail(new FailCallback<WampException>() {
            @Override
            public void onFail(WampException f) {
                remotePeer.removeInvocation(invocationId);
                if(f.getErrorURI().equals("wgs.cancel_invocation")) {
                    WampProtocol.sendInterruptMessage(remotePeer, f.getInvocationId(), f.getDetails());
                }
            }
        });

        task.addRemoteInvocation(remotePeer.getSocketId(), invocationId, deferred);
        remotePeer.addInvocation(invocationId, task, deferred);

        WampDict invocationOptions = new WampDict();
        if(matchType != WampMatchType.exact) invocationOptions.put("procedure", task.getProcedureURI());
        if(callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) invocationOptions.put("receive_progress", true);
        if(callOptions.hasDiscloseMe() || regOptions.getBoolean("disclose_caller")) {
            if("cluster".equals(clientSocket.getRealm())) {            
                invocationOptions.put("caller", callOptions.getCallerId());
                invocationOptions.put("authid", callOptions.getAuthId());
                invocationOptions.put("authprovider", callOptions.getAuthProvider());
                invocationOptions.put("authrole", callOptions.getAuthRole());
            } else {
                invocationOptions.put("caller", clientSocket.getWampSessionId());
                invocationOptions.put("authid", clientSocket.getAuthId());
                invocationOptions.put("authprovider", clientSocket.getAuthProvider());
                invocationOptions.put("authrole", clientSocket.getAuthRole());
            }
        }

        if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "CALL " + task.getCallID() + ": SENDING INVOCATION ID: " + invocationId + " (" + clientSocket.getWampSessionId() + " --> " + remotePeer.getWampSessionId() + ")");
        try {
            task.decrementPendingInvocationCount();
            WampProtocol.sendInvocationMessage(remotePeer, invocationId, registrationId, invocationOptions, args, argsKw);
            
            /* DEBUG: skip-invocations 
            { 
                WampResult wampResult = new WampResult(invocationId);
                wampResult.setArgs(new WampList(6L));
                deferred.resolve(wampResult);
            }
            */

            if(!remotePeer.isOpen()) throw new Exception();
        } catch(Exception ex) {
            task.removeRemoteInvocation(remotePeer.getSocketId(), invocationId);
        }

        return promise;
    }    
    
}
