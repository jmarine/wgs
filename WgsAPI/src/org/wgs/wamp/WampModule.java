package org.wgs.wamp;

import org.wgs.wamp.types.WampMatchType;
import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampList;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampAsyncCall;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.rpc.WampLocalMethod;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampTopic;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.topic.WampMetaTopic;
import org.wgs.wamp.topic.WampPublishOptions;
import org.wgs.wamp.annotation.WampRPC;
import org.wgs.wamp.annotation.WampModuleName;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.JmsServices;


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
    
    public void onDisconnect(WampSocket clientSocket) throws Exception { }

    @SuppressWarnings("unchecked")
    public Object onCall(final WampCallController task, final WampSocket clientSocket, String methodName, final WampList args, final WampDict argsKw, final WampCallOptions options) throws Exception 
    {
        WampMethod method = app.getLocalRPCs(methodName, options);
        if(method != null) {
            return method.invoke(task,clientSocket,args,argsKw,options);
        } else {
            final ArrayList<WampRemoteMethod> remoteMethods = app.getRemoteRPCs(methodName, options);
            final ArrayList<WampAsyncCall> remoteInvocations = new ArrayList<WampAsyncCall>();
            
            if(!remoteMethods.isEmpty()) {
                switch(options.getRunOn()) {
                    case any:
                        int index = (int)(Math.random() * remoteMethods.size());
                        method = remoteMethods.get(index);
                        return method.invoke(task,clientSocket,args,argsKw,options);

                    default:
                        
                        final AtomicInteger barrier = new AtomicInteger(remoteMethods.size());

                        final WampAsyncCallback completeCallback = new WampAsyncCallback()
                        {
                            @Override
                            public void resolve(Object... results) {
                                Long id = (Long)results[0];
                                WampDict details  = (WampDict)results[1];
                                WampList result   = (WampList)results[2];
                                WampDict resultKw = (WampDict)results[3];
                                WampProtocol.sendResult(clientSocket, task.getCallID(), details, result, resultKw);
                            }

                            @Override
                            public void progress(Object... progressParams) {
                                Long id = (Long)progressParams[0];
                                WampDict details = (WampDict)progressParams[1];
                                WampList progress = (WampList)progressParams[2];
                                WampDict progressKw = (WampDict)progressParams[3];
                                if(clientSocket.supportsProgressiveCallResults() && options.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                                    if(details == null) details = new WampDict();
                                    details.put("progress", true);
                                    WampProtocol.sendResult(clientSocket, task.getCallID(), details, progress, progressKw);
                                } else {
                                    task.getResultKw().putAll(progressKw);
                                    switch(progress.size()) {
                                        case 0: 
                                            break;
                                        case 1:
                                            task.getResult().add(progress.get(0));
                                            break;
                                        default:
                                            task.getResult().add(progress);
                                    }
                                }
                            }

                            @Override
                            public void reject(Object... errors) {
                                Long invocationId = (Long)errors[0];
                                WampDict details = (WampDict)errors[1];
                                String   errorURI = (String)errors[2];
                                WampList args = (errors.length > 3) ? (WampList)errors[3] : null;
                                WampDict argsKw = (errors.length > 4) ? (WampDict)errors[4] : null;
                                WampProtocol.sendError(clientSocket, WampProtocol.CALL, task.getCallID(), details, errorURI, args, argsKw);
                            }                            
                        };
                                
                        return new WampAsyncCall(completeCallback) {

                            @Override
                            public Void call() throws Exception {
                                for(final WampRemoteMethod method : remoteMethods) {
                                    WampAsyncCall remoteInvocation = (WampAsyncCall)method.invoke(task,clientSocket,args,argsKw,options);
                                    remoteInvocation.setAsyncCallback(new WampAsyncCallback() {
                                        @Override
                                        public void resolve(Object... results) {
                                            Long id = (Long)results[0];
                                            WampDict details = (WampDict)results[1];
                                            WampList result = (WampList)results[2];
                                            WampDict resultKw = (WampDict)results[3];
                                            if(!clientSocket.supportsProgressiveCallResults() || options.getRunMode() != WampCallOptions.RunModeEnum.progressive) {
                                                task.getResultKw().putAll(resultKw);
                                                task.getResult().add(result);
                                                
                                                resultKw = task.getResultKw();
                                                result = task.getResult();
                                                if((result.size() == 1) && (remoteMethods.size() == 1) && (result.get(0) instanceof WampList)) {
                                                    result = (WampList)result.get(0);
                                                }   
                                                
                                            } else if(remoteMethods.size() > 1) {
                                                completeCallback.progress(id, null, result, resultKw);
                                                result = null;
                                                resultKw = null;
                                            }
                                            
                                            if(barrier.decrementAndGet() <= 0) {
                                                completeCallback.resolve(id, null, result, resultKw);
                                            } 
                                        }

                                        @Override
                                        public void progress(Object... progressParams) {
                                            completeCallback.progress(progressParams);
                                        }

                                        @Override
                                        public void reject(Object... errors) {
                                            Long invocationId = (Long)errors[0];
                                            WampDict details = (WampDict)errors[1];
                                            String   errorURI = (String)errors[2];
                                            WampList args = (errors.length > 3) ? (WampList)errors[3] : null;
                                            WampDict argsKw = (errors.length > 4) ? (WampDict)errors[4] : null;
                                            WampProtocol.sendError(clientSocket, WampProtocol.CALL, task.getCallID(), details, errorURI, args, argsKw);
                                        }
                                    });

                                    remoteInvocations.add(remoteInvocation);
                                    remoteInvocation.call();
                                }
                                return null;
                            }

                            @Override
                            public void cancel(WampDict cancelOptions) {
                                for(WampAsyncCall invocation : remoteInvocations) {
                                    invocation.cancel(cancelOptions);
                                }
                            }


                        };
                }
            }
        }
        
        
        System.out.println("Method not implemented: " + methodName);
        throw new WampException(null, WampException.ERROR_PREFIX+".method_unknown", null, null);
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
                WampBroker.publishMetaEvent(WampProtocol.newId(), topic, WampMetaTopic.SUBSCRIBER_ADDED, metaEvent, null);
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
                WampBroker.publishMetaEvent(WampProtocol.newId(), topic, WampMetaTopic.SUBSCRIBER_REMOVED, metaEvent, null);
            }

            clientSocket.removeSubscription(subscription.getId());
            subscription.removeSocket(clientSocket.getSessionId());
            if(subscription.getSocketsCount() == 0) {
                topic.removeSubscription(subscription.getId());
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
        WampCalleeRegistration registration = app.getRegistration(registrationId);
        if(registration == null) {
            throw new WampException(null, "wamp.error.registration_not_found", null, null);
        } else {
            clientSocket.removeRpcRegistration(registrationId);
            registration.removeRemoteMethod(clientSocket.getSessionId());
            //rpcsByName.remove(method.getProcedureURI());
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
                WampProtocol.sendPublished(clientSocket, requestId, publicationId);
            }
        
            WampBroker.publishEvent(publicationId, topic, payload, payloadKw, options.getEligible(), options.getExcluded(), (options.hasDiscloseMe()? clientSocket.getSessionId() : null));
        }
    }
    
    public void onEvent(WampSocket serverSocket, WampList request) throws Exception     
    {
        
    }

    
}
