package org.wgs.wamp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jdeferred.Deferred;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.wgs.wamp.annotation.WampModuleName;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampLocalMethod;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampMetaTopic;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampModule 
{
    private WampApplication app;


    public WampModule(WampApplication app) 
    {
        String moduleName = app.normalizeModuleName(getModuleName());
        this.app = app;

        for(Method method : this.getClass().getMethods()) {
            WampRPC rpc = method.getAnnotation(WampRPC.class);
            if(rpc != null) {
                String name = rpc.name();
                if(name.length() == 0) name = method.getName();
                name = moduleName + name;
                app.createRPC(name, new WampLocalMethod(name,this,method));
            }
        }
        
    }
    
    public WampApplication getWampApplication()
    {
        return app;
    }
    
    public String getModuleName() 
    {
        String retval = "";
        WampModuleName ns = this.getClass().getAnnotation(WampModuleName.class);
        if(ns != null) retval = ns.value();
        else retval = this.getClass().getPackage().getName();
        return retval;
    }
    
    public void onConnect(WampSocket clientSocket) throws Exception { }
    
    public void onChallenge(WampSocket clientSocket, String authMethod, WampDict details) throws Exception { }
    
    public void onSessionEstablished(WampSocket clientSocket, WampDict details) { }
    
    public void onSessionEnd(WampSocket clientSocket) { }
    
    public void onDisconnect(WampSocket clientSocket) throws Exception { }

    @SuppressWarnings("unchecked")
    public Promise onCall(final WampCallController task, final WampSocket clientSocket, String methodName, final WampList args, final WampDict argsKw, final WampCallOptions options) throws Exception 
    {
        WampMethod method = app.getLocalRPC(methodName);
        if(method != null) {
            final Deferred<WampResult,WampException,WampResult> deferred = new DeferredObject<WampResult,WampException,WampResult>();
            Promise localCallPromise = method.invoke(task,clientSocket,args,argsKw,options);
            localCallPromise.done(new DoneCallback() { 
                @Override
                public void onDone(Object response) {
                    WampResult result = new WampResult(task.getCallID());
                    if(response != null) {
                        if (response instanceof WampDict) {
                            result.setArgsKw((WampDict)response);
                        } else if (response instanceof WampList) {
                            result.setArgs((WampList)response);
                        } else {
                            WampList list = new WampList();
                            list.add(response);
                            result.setArgs(list);
                        }
                    }
                    deferred.resolve(result);
                }});
            
            localCallPromise.fail(new FailCallback() { 
                @Override
                public void onFail(Object f) {
                    String uri = "wgs.local_invocation_exception";
                    WampException error = new WampException(task.getCallID(), null, uri, null, null);
                    deferred.reject(error);
                }
            });
            
            localCallPromise.progress(new ProgressCallback() {
                @Override
                public void onProgress(Object response) {
                    WampResult progress = new WampResult(task.getCallID());
                    if(response != null) {
                        if (response instanceof WampDict) {
                            progress.setArgsKw((WampDict)response);
                        } else if (response instanceof WampList) {
                            progress.setArgs((WampList)response);
                        } else {
                            WampList list = new WampList();
                            list.add(response);
                            progress.setArgs(list);
                        }
                    }                    
                    deferred.notify(progress);
                }
            } );
            
            return deferred.promise();
            
        } else {
            
            final WampRealm realm = WampRealm.getRealm(clientSocket.getRealm());
            final ArrayList<WampRemoteMethod> remoteMethods = realm.getRemoteRPCs(clientSocket.getRealm(), methodName, options, clientSocket.getSessionId());
            
            if(remoteMethods.isEmpty()) {
                System.out.println("No remote method: " + methodName);
                throw new WampException(null, WampException.ERROR_PREFIX+".no_remote_method", null, null);
                
            } else {
                
                switch(options.getRunOn()) {
                    case any:
                        int index = (int)(Math.random() * remoteMethods.size());
                        method = remoteMethods.get(index);
                        return method.invoke(task,clientSocket,args,argsKw,options);

                    default:
                        final Deferred<WampResult,WampException,WampResult> deferred = new DeferredObject<WampResult,WampException,WampResult>();

                        task.setPendingInvocationCount(remoteMethods.size());
                        task.setRemoteInvocationsCompletionCallback(deferred);
                        //synchronized(task) 
                        {
                            for(final WampRemoteMethod remoteMethod : remoteMethods) {
                                Promise<WampResult,WampException,WampResult> remoteInvocation = remoteMethod.invoke(task,clientSocket,args,argsKw,options);
                                remoteInvocation.done(new DoneCallback<WampResult>() {
                                    @Override
                                    public void onDone(WampResult wampResult) {
                                        if(!clientSocket.supportsProgressiveCallResults() || options.getRunMode() != WampCallOptions.RunModeEnum.progressive) {
                                            synchronized(task) {
                                                task.incrementRemoteInvocationResults();
                                                task.getResultKw().putAll(wampResult.getArgsKw());
                                                task.getResult().add(wampResult.getArgs());
                                            }

                                        } else if(remoteMethods.size() <= 1) {
                                            synchronized(task) {
                                                task.setResult(wampResult.getArgs());
                                                task.setResultKw(wampResult.getArgsKw());
                                            }
                                        } else {
                                            deferred.notify(wampResult);
                                        }

                                        task.removeRemoteInvocation(wampResult.getRequestId());
                                    }
                                });

                                remoteInvocation.progress(new ProgressCallback<WampResult>() {
                                    @Override
                                    public void onProgress(WampResult progress) {
                                        deferred.notify(progress);
                                    }
                                });
                                
                                remoteInvocation.fail(new FailCallback<WampException>() {
                                    @Override
                                    public void onFail(WampException error) {
                                        WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, task.getCallID(), error.getDetails(), error.getErrorURI(), error.getArgs(), error.getArgsKw());
                                    }
                                 });

                            }

                        }

                        deferred.fail(new FailCallback<WampException>() {
                            @Override
                            public void onFail(WampException f) {
                                // done by task.cancel(cancelOptions);
                            }
                        });
                        
                        return deferred.promise();

                }
                
            }
            
        }
        

        
    }
    
    public void onSubscribe(WampSocket clientSocket, WampTopic topic, WampSubscription subscription, WampSubscriptionOptions options) throws Exception { 
        long sinceN = 0L;       // options.getSinceN();
        long sinceTime = 0L;    // options.getSinceTime();
        topic.addSubscription(subscription);
        clientSocket.addSubscription(subscription);
        if(options != null && options.hasEventsEnabled() && options.hasMetaTopic(WampMetaTopic.SUBSCRIBER_ADDED)) {
            if(options.hasEventsEnabled()) {
                WampDict metaEvent = new WampDict();
                metaEvent.put("session", clientSocket.getSessionId());
                WampBroker.publishMetaEvent(clientSocket.getRealm(), WampProtocol.newId(), topic, WampMetaTopic.SUBSCRIBER_ADDED, metaEvent, null);
            }
        }
    }

    public void onUnsubscribe(WampSocket clientSocket, Long subscriptionId, WampTopic topic) throws Exception { 
        WampSubscription subscription = topic.getSubscription(subscriptionId);
        if(subscription.getSocket(clientSocket.getSessionId()) != null) {
            WampSubscriptionOptions options = subscription.getOptions();
            if(options!=null && options.hasEventsEnabled() && options.hasMetaTopic(WampMetaTopic.SUBSCRIBER_REMOVED)) {
                WampDict metaEvent = new WampDict();
                metaEvent.put("session", clientSocket.getSessionId());                
                WampBroker.publishMetaEvent(clientSocket.getRealm(), WampProtocol.newId(), topic, WampMetaTopic.SUBSCRIBER_REMOVED, metaEvent, null);
            }

            subscription.removeSocket(clientSocket.getSessionId());
            if(subscription.getSocketsCount() == 0) {
                topic.removeSubscription(subscription.getId());
                if(topic.getSubscriptionCount() == 0) {
                    if(topic.getOptions() != null && topic.getOptions().isTemporary()) {
                        WampBroker.removeTopic(null, topic.getTopicName());
                    }
                }
            }
        }
    }
    
    public void onRegister(Long registrationId, WampSocket clientSocket, WampCalleeRegistration registration, WampMatchType matchType, String methodUriOrPattern, WampList request) throws Exception
    {
        WampDict options = (WampDict)request.get(2);
        
        WampRemoteMethod remoteMethod = new WampRemoteMethod(registration.getId(), clientSocket, matchType, options);
        registration.addRemoteMethod(clientSocket.getSessionId(), remoteMethod);
        
        clientSocket.addRpcRegistration(registration);
    }
    
    public void onUnregister(WampSocket clientSocket, Long registrationId) throws Exception
    {
        WampRealm realm = WampRealm.getRealm(clientSocket.getRealm());
        WampCalleeRegistration registration = realm.getRegistration(registrationId);
        if(registration == null) {
            throw new WampException(null, "wamp.error.registration_not_found", null, null);
        } else {
            
            synchronized(registration) {
                clientSocket.removeRpcRegistration(registrationId);
                registration.removeRemoteMethod(clientSocket);
                //rpcsByName.remove(method.getProcedureURI());
            
                for(Long invocationId : clientSocket.getInvocationIDs()) {
                    WampCallController task = clientSocket.removeInvocationController(invocationId);
                    WampProtocol.sendErrorMessage(clientSocket, WampProtocol.INVOCATION, invocationId, null, null, null, null); 
                    task.removeRemoteInvocation(invocationId);
                }
            }
            
        }
    }    
    
    public void onPublish(WampSocket clientSocket, WampTopic topic, WampList request) throws Exception 
    {
        Long publicationId = WampProtocol.newId();
        Long requestId = request.getLong(1);
        if(requestId != null) {
            WampList payload   = (request.size() >= 5)? (WampList)request.get(4) : null;
            WampDict payloadKw = (request.size() >= 6)? (WampDict)request.get(5) : null;;
            WampPublishOptions options = new WampPublishOptions((WampDict)request.get(2));
            if(options.hasExcludeMe()) {
                Set<Long> excludedSet = options.getExcluded();
                if(excludedSet == null) excludedSet = new HashSet<Long>();
                excludedSet.add(clientSocket.getSessionId());
                options.setExcluded(excludedSet);
            }

            if(options.hasAck()) {
                WampProtocol.sendPublishedMessage(clientSocket, requestId, publicationId);
            }
        
            WampBroker.publishEvent(clientSocket.getRealm(), publicationId, topic, payload, payloadKw, options.getEligible(), options.getExcluded(), 
                    (options.hasDiscloseMe()? clientSocket.getSessionId() : null),
                    (options.hasDiscloseMe()? clientSocket.getAuthId() : null),
                    (options.hasDiscloseMe()? clientSocket.getAuthProvider(): null),
                    (options.hasDiscloseMe()? clientSocket.getAuthRole(): null)
            );
        }
    }
    
    public void onEvent(WampSocket serverSocket, Long subscriptionId, Long publicationId, WampDict details, WampList payload, WampDict payloadKw) throws Exception
    {
        
    }

    
}
