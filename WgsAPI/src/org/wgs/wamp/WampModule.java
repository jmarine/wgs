package org.wgs.wamp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.wgs.util.MessageBroker;


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

                        final Promise completePromise = new Promise()
                        {
                            @Override
                            public void resolve(Object... results) {
                                WampList result = new WampList();
                                WampDict resultKw = new WampDict();
                                if(!clientSocket.supportProgressiveCalls() || options.getRunMode() != WampCallOptions.RunModeEnum.progressive) {
                                    result = task.getResult();
                                    resultKw = task.getResultKw();
                                }
                                WampProtocol.sendCallResult(clientSocket, task.getCallID(), result, resultKw);
                            }

                            @Override
                            public void progress(Object... progressParams) {
                                WampList progress = (WampList)progressParams[0];
                                WampDict progressKw = (WampDict)progressParams[1];
                                if(clientSocket.supportProgressiveCalls() && options.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                                    WampProtocol.sendCallProgress(clientSocket, task.getCallID(), progress, progressKw);
                                } else {
                                    task.getResult().add(progress);
                                    task.getResultKw().putAll(progressKw);
                                }
                            }

                            @Override
                            public void reject(Object... errors) {
                                WampProtocol.sendCallError(clientSocket, task.getCallID(), (String)errors[0], null, errors[1]);
                            }                            
                        };
                                
                        return new WampAsyncCall(completePromise) {

                            @Override
                            public Void call() throws Exception {
                                for(final WampRemoteMethod method : remoteMethods) {
                                    WampAsyncCall remoteInvocation = (WampAsyncCall)method.invoke(task,clientSocket,args,argsKw,options);
                                    remoteInvocation.setPromise(new Promise() {
                                        @Override
                                        public void resolve(Object... results) {
                                            WampList progress = (WampList)results[0];
                                            WampDict progressKw = (WampDict)results[1];
                                            if(clientSocket.supportProgressiveCalls() && options.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                                                WampProtocol.sendCallProgress(clientSocket, task.getCallID(), progress, progressKw);
                                            } else {
                                                task.getResult().add(progress);
                                                task.getResultKw().putAll(progressKw);
                                            }
                                            
                                            if(barrier.decrementAndGet() <= 0) {
                                                completePromise.resolve(null, null);
                                            }
                                        }

                                        @Override
                                        public void progress(Object... progressParams) {
                                            completePromise.progress(progressParams);
                                        }

                                        @Override
                                        public void reject(Object... errors) {
                                            WampProtocol.sendCallError(clientSocket, task.getCallID(), (String)errors[0], null, errors[1]);
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
        
        

        throw new WampException(WampException.ERROR_PREFIX+".method_unknown", "Method not implemented: " + methodName);
    }
    
    public void onSubscribe(WampSocket clientSocket, WampTopic topic, WampSubscription subscription, WampSubscriptionOptions options) throws Exception { 
        long sinceN = 0L;       // options.getSinceN();
        long sinceTime = 0L;    // options.getSinceTime();
        MessageBroker.subscribeMessageListener(topic, sinceTime, sinceN);
        topic.addSubscription(subscription);
        clientSocket.addSubscription(subscription);
        if(options != null && options.hasMetaEvents()) {
            if(options.hasEventsEnabled()) {
                WampServices.publishMetaEvent(WampProtocol.newId(), topic, WampMetaTopic.SUBSCRIBER_ADDED, clientSocket.toWampObject(), null);
            }
        }
    }

    public void onUnsubscribe(WampSocket clientSocket, Long subscriptionId, WampTopic topic) throws Exception { 
        WampSubscription subscription = topic.getSubscription(subscriptionId);
        if(subscription.getSocket(clientSocket.getSessionId()) != null) {
            WampSubscriptionOptions options = subscription.getOptions();
            if(options!=null && options.hasMetaEvents() && options.hasEventsEnabled()) {
                WampObject metaEvent = clientSocket.toWampObject();
                WampServices.publishMetaEvent(WampProtocol.newId(), topic, WampMetaTopic.SUBSCRIBER_REMOVED, metaEvent, null);
            }

            clientSocket.removeSubscription(subscription.getId());
            subscription.removeSocket(clientSocket.getSessionId());
            if(subscription.getSocketsCount() == 0) {
                topic.removeSubscription(subscription.getId());
            
                if(topic.getSubscriptionCount() == 0) {
                    MessageBroker.unsubscribeMessageListener(topic);
                }
            }
        }
    }
    
    public void onRegister(Long registrationId, WampSocket clientSocket, WampCalleeRegistration registration, MatchEnum matchType, String methodUriOrPattern, WampList request) throws Exception
    {
        Long requestId = request.get(1).asLong();
        WampDict options = (WampDict)request.get(2);
        
        WampRemoteMethod remoteMethod = new WampRemoteMethod(registration.getId(), clientSocket, matchType, options);
        registration.addRemoteMethod(clientSocket.getSessionId(), remoteMethod);
    }
    
    public void onUnregister(WampSocket clientSocket, Long requestId, Long registrationId) throws Exception
    {
        WampCalleeRegistration registration = app.getRegistration(registrationId);
        if(registration == null) {
            throw new WampException("wamp.error.registration_not_found","registrationId doesn't exists");
        } else {
            registration.removeRemoteMethod(clientSocket.getSessionId());
            //rpcsByName.remove(method.getProcedureURI());
            if(requestId != null) WampProtocol.sendRegisteredMessage(clientSocket, requestId, registrationId);
        }
    }    
    
    public void onPublish(WampSocket clientSocket, WampTopic topic, WampList request) throws Exception 
    {
        Long publicationId = WampProtocol.newId();
        WampPublishOptions options = new WampPublishOptions();
        Long requestId = null;
        WampObject event = null;
        
        if(request.get(0).asLong() == 30) {
            // WAMP v2
            requestId = request.get(1).asLong();
            event = request.get(4);
            options.init((WampDict)request.get(2));
            if(options.hasExcludeMe()) {
                Set<Long> excludedSet = options.getExcluded();
                if(excludedSet == null) excludedSet = new HashSet<Long>();
                excludedSet.add(clientSocket.getSessionId());
            }
        } else {
            // WAMP v1
            event = (WampObject)request.get(2);
            if(request.size() == 4) {
                // Argument 4 could be a BOOLEAN(excludeMe) or JSONArray(excludedIds)
                try {
                    boolean excludeMe = request.get(3).asBoolean();
                    options.setExcludeMe(excludeMe);                    
                    if(excludeMe) {
                        HashSet<Long> excludedSet = new HashSet<Long>();
                        excludedSet.add(clientSocket.getSessionId());
                    }
                } catch(Exception ex) {
                    HashSet<Long> excludedSet = new HashSet<Long>();
                    WampList excludedArray = (WampList)request.get(3);
                    for(int i = 0; i < excludedArray.size(); i++) {
                        excludedSet.add(excludedArray.get(i).asLong());
                    }
                    options.setExcluded(excludedSet);
                }
            } else if(request.size() == 5) {
                HashSet<Long> excludedSet = new HashSet<Long>();
                HashSet<Long> eligibleSet = new HashSet<Long>();
                WampList excludedArray = (WampList)request.get(3);
                for(int i = 0; i < excludedArray.size(); i++) {
                    excludedSet.add(excludedArray.get(i).asLong());
                }
                WampList eligibleArray = (WampList)request.get(4);
                for(int i = 0; i < eligibleArray.size(); i++) {
                    eligibleSet.add(eligibleArray.get(i).asLong());
                }
                options.setExcluded(excludedSet);
                options.setEligible(eligibleSet);
            }
        }
        
        WampServices.publishEvent(publicationId, clientSocket.getSessionId(), topic, event, options);
        
        if(requestId != null) WampProtocol.sendPublished(clientSocket, requestId, publicationId);
    }

    
}
