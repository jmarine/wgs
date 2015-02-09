package org.wgs.wamp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.wgs.wamp.topic.WampCluster;
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
    private HashMap<Long,Collection<Method>> eventListeners;  // by subscriptionId


    public WampModule(WampApplication app) 
    {
        String moduleName = app.normalizeModuleName(getModuleName());
        this.app = app;
        this.eventListeners = new HashMap<Long,Collection<Method>>();

        for(Method method : this.getClass().getMethods()) {
            WampRPC rpc = method.getAnnotation(WampRPC.class);
            if(rpc != null) {
                String name = rpc.name();
                if(name.length() == 0) name = method.getName();
                name = moduleName + name;
                app.registerLocalRPC(rpc.match(), name, new WampLocalMethod(name,this,method));
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
    
    public void addSubscriptionMethod(Long subscriptionId, Method method)
    {
        Collection<Method> methods = eventListeners.get(subscriptionId);
        if(methods == null) methods = new ArrayList<Method>();
        methods.add(method);
        eventListeners.put(subscriptionId, methods);
    }
    
    
    public void onConnect(WampSocket clientSocket) throws Exception { }
    
    public void onChallenge(WampSocket clientSocket, String authMethod, WampDict details) throws Exception { }
    
    public void onSessionEstablished(WampSocket clientSocket, WampDict details) { 
        this.eventListeners = new HashMap<Long,Collection<Method>>();        
    }
    
    public void onSessionEnd(WampSocket clientSocket) { 
    }
    
    public void onDisconnect(WampSocket clientSocket) throws Exception { }

    @SuppressWarnings("unchecked")
    public Promise<WampResult, WampException, WampResult> onCall(final WampCallController task, final WampSocket clientSocket, String methodName, final WampList args, final WampDict argsKw, final WampCallOptions options) throws Exception 
    {
        WampMethod method = app.searchLocalRPC(methodName);
        if(method != null) {
            return method.invoke(task,clientSocket,args,argsKw,options);
            
        } else {  
            
            final WampRealm realm = WampRealm.getRealm(clientSocket.getRealm());
            final List<WampRemoteMethod> remoteMethods = realm.getRemoteRPCs(clientSocket.getRealm(), methodName, options, clientSocket.getSessionId());
            
            if(remoteMethods.isEmpty()) {
                System.out.println("No remote method: " + methodName);
                throw new WampException(null, WampException.ERROR_PREFIX+".no_remote_method", null, null);
                
            } else {
                final Deferred<WampResult,WampException,WampResult> deferred = new DeferredObject<WampResult,WampException,WampResult>();

                task.setPendingInvocationCount(remoteMethods.size());
                task.setRemoteInvocationsCompletionCallback(deferred);
                 
                for(final WampRemoteMethod remoteMethod : remoteMethods) {
                    Promise<WampResult,WampException,WampResult> remoteInvocation = remoteMethod.invoke(task,clientSocket,args,argsKw,options);
                    remoteInvocation.done(new DoneCallback<WampResult>() {
                        @Override
                        public void onDone(WampResult wampResult) {
                            if(!clientSocket.supportsProgressiveCallResults() || options.getRunMode() != WampCallOptions.RunModeEnum.progressive || remoteMethods.size() <= 1) {
                                synchronized(task) {
                                    task.incrementRemoteInvocationResults();
                                    task.getResultKw().putAll(wampResult.getArgsKw());
                                    task.getResult().add(wampResult.getArgs());
                                }
                            } else {
                                deferred.notify(wampResult);
                            }

                            task.removeRemoteInvocation(remoteMethod.getRemotePeer().getSessionId(), wampResult.getRequestId());
                        }
                    });

                    remoteInvocation.progress(new ProgressCallback<WampResult>() {
                        @Override
                        public void onProgress(WampResult progress) {
                            //synchronized(task) 
                            {
                                if(!task.isCancelled()) {
                                    deferred.notify(progress);
                                }
                            }
                        }
                    });

                    remoteInvocation.fail(new FailCallback<WampException>() {
                        @Override
                        public void onFail(WampException error) {
                            //synchronized(task) 
                            {
                                if(!task.isCancelled()) {
                                    task.cancel(null);
                                }
                            }
                        }
                     });

                }


                return deferred.promise();
               
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
                WampBroker.publishMetaEvent(clientSocket.getRealm(), WampProtocol.newGlobalScopeId(), topic, WampMetaTopic.SUBSCRIBER_ADDED, metaEvent, null);
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
                WampBroker.publishMetaEvent(clientSocket.getRealm(), WampProtocol.newGlobalScopeId(), topic, WampMetaTopic.SUBSCRIBER_REMOVED, metaEvent, null);
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
    
    public void onRegister(WampSocket clientSocket, Long registrationId, String methodName, WampCalleeRegistration registration, WampMatchType matchType, String methodUriOrPattern, WampList request) throws Exception
    {
        WampDict options = (WampDict)request.get(2);
        String calleeRealm = clientSocket.getRealm();
        if(options.has("_cluster_peer_realm")) calleeRealm = options.getText("_cluster_peer_realm");
        Long calleeSessionId = options.getLong("_cluster_peer_sid");

        WampRemoteMethod remoteMethod = new WampRemoteMethod(registration.getId(), methodName, clientSocket, calleeSessionId, matchType, options);
        registration.addRemoteMethod(clientSocket.getSessionId(), remoteMethod);
        
        clientSocket.addRpcRegistration(registration);
        
        if(!"cluster".equals(clientSocket.getRealm())) {
            WampCluster.registerClusteredRPC(WampRealm.getRealm(calleeRealm), registration, remoteMethod);
        }
    }
    
    public void onUnregister(WampSocket clientSocket, WampCalleeRegistration calleeRegistration) throws Exception
    {
        Long registrationId = calleeRegistration.getId();
        WampRealm realm = WampRealm.getRealm(calleeRegistration.getRealmName());
        WampCalleeRegistration registration = realm.getRegistration(registrationId);
        if(registration == null) {
            throw new WampException(null, "wamp.error.registration_not_found", null, null);
        } else {
            
            clientSocket.removeRpcRegistration(registrationId);
            WampRemoteMethod remoteMethod = registration.removeRemoteMethod(clientSocket);
            //rpcsByName.remove(method.getProcedureURI());
            
            if(!"cluster".equals(clientSocket.getRealm())) {
                WampCluster.unregisterClusteredRPC(realm, registration, remoteMethod);
            }

            synchronized(this.app)  // TODO: avoid synchronization by WampApplication
            {
                WampDict interruptDetails = new WampDict();
                for(Long invocationId : clientSocket.getInvocationIDs()) {  // critical section
                    WampCallController task = clientSocket.removeInvocationController(invocationId);
                    task.removeRemoteInvocation(clientSocket.getSessionId(), invocationId);
                    try { 
                        WampProtocol.sendInterruptMessage(clientSocket, invocationId, interruptDetails); 
                    } catch(Exception ex) { 
                        System.out.println("onUnregister: Error: " + ex.getMessage());
                    }
                }
            }
            
        }
    }    
    
    public void onPublish(WampSocket clientSocket, WampTopic topic, WampList request) throws Exception 
    {
        Long publicationId = WampProtocol.newSessionScopeId(clientSocket);
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
        Collection<Method> methods = eventListeners.get(subscriptionId);
        if(methods != null) {
            for(Method method : methods) {
                try { method.invoke(this, serverSocket, subscriptionId, publicationId, details, payload, payloadKw); }
                catch(Exception ex) { }
            }
        }
    }

    
}
