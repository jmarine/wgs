package org.wgs.wamp;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
    public Object onCall(WampCallController task, WampSocket clientSocket, String methodName, WampList args, WampDict argsKw, WampCallOptions options) throws Exception 
    {
        Collection<WampMethod> methods = app.getRPCs(methodName, options);
        if(methods != null && methods.size() > 0) {
            return methods.iterator().next().invoke(task,clientSocket,args,argsKw,options);
        }

        throw new WampException(WampException.ERROR_PREFIX+".method_unknown", "Method not implemented: " + methodName);
    }
    
    public void onSubscribe(WampSocket clientSocket, WampTopic topic, WampSubscription subscription, WampSubscriptionOptions options) throws Exception { 
        if(!subscription.addSocket(clientSocket)) {
            subscription.getOptions().updateOptions(options);
        } else {
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
    
    public void onRegister(Long registrationId, WampSocket clientSocket, WampRemoteMethod method, MatchEnum matchType, String methodUriOrPattern, WampList request) throws Exception
    {
        Long requestId = request.get(1).asLong();
        WampDict options = (WampDict)request.get(2);
        method.addRemotePeer(registrationId, clientSocket);
    }
    
    public void onUnregister(WampSocket clientSocket, Long requestId, Long registrationId) throws Exception
    {
        WampRemoteMethod registration = app.getRegistration(registrationId);
        if(registration == null) {
            throw new WampException("wamp.error.registration_not_found","registrationId doesn't exists");
        } else {
            registration.removeRemotePeer(registrationId);
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
