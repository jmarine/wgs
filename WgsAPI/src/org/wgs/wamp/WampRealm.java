package org.wgs.wamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampRealm 
{
    private static final Logger logger = Logger.getLogger(WampRealm.class.getName());
    private static HashMap<String,WampRealm> realms = new HashMap<String,WampRealm>();
    
    private String realmName;
    
    private ConcurrentHashMap<String,WampCalleeRegistration> calleePatterns;
    private ConcurrentHashMap<String,WampCalleeRegistration> calleeRegistrationByUri;
    private static ConcurrentHashMap<Long,WampCalleeRegistration> calleeRegistrationById;
    

    static 
    {
        calleeRegistrationById = new ConcurrentHashMap<Long,WampCalleeRegistration>();
    }
    
    public WampRealm(String realmName)
    {
        this.realmName = realmName;
        this.calleeRegistrationByUri = new ConcurrentHashMap<String,WampCalleeRegistration>();
        this.calleePatterns = new ConcurrentHashMap<String,WampCalleeRegistration>();
    }
    
   
    public static Collection<String> getRealmNames()
    {
        return realms.keySet();
    }
    
    public static WampRealm getRealm(String name)
    {
        WampRealm realm = realms.get(name);
        if(realm == null) {
            realm = new WampRealm(name);
            realms.put(name, realm);
        }
        return realm;
    }
    
    
    public String getRealmName()
    {
        return realmName;
    }
    
    public List<WampRemoteMethod> getRemoteRPCs(String realm, String name, WampCallOptions options, Long callerId) throws WampException
    {
        boolean found = false;
        List<WampRemoteMethod> retval = new ArrayList<WampRemoteMethod>();

        String partition = null;
        if(options != null && options.getRunOn() == WampCallOptions.RunOnEnum.partition) partition = options.getPartition();

        WampCalleeRegistration reg = calleeRegistrationByUri.get(name);
        if(reg != null) {
            found = true;
            for(WampRemoteMethod remoteMethod : reg.getRemoteMethods(callerId, options)) {
                if(remoteMethod == null) {
                    logger.severe("Remote Method registration is null");
                } else {
                    if(remoteMethod.hasPartition(partition)) {
                        retval.add(remoteMethod);
                    }
                }
            }
        }

        for(WampCalleeRegistration registration : calleePatterns.values()) {
            if(WampBroker.isUriMatchingWithRegExp(name, registration.getRegExp())) {
                found = true;
                for(WampRemoteMethod remoteMethod : registration.getRemoteMethods(callerId, options)) {
                    if(remoteMethod.hasPartition(partition)) {
                        retval.add(remoteMethod);
                    }
                }
            }
        }

        if(retval.size() == 0) {
            if(found) throw new WampException(null, WampException.ERROR_PREFIX+".no_remote_method", null, null);
            else throw new WampException(null, WampException.ERROR_PREFIX+".method_unknown", null, null);
        }
        
        if(options.getRunOn() == WampCallOptions.RunOnEnum.any) {
            int index = (int)(Math.random() * retval.size());
            retval = retval.subList(index, index+1);
        }
        
        return retval;
    }    
    
    
    public Collection<Long> getRegistrationIds()
    {
        return calleeRegistrationById.keySet();
    }
    
    public static WampCalleeRegistration getRegistration(Long registrationId)
    {
        return calleeRegistrationById.get(registrationId);
    }    
    
    
    public Collection<WampCalleeRegistration> getPatternRegistrations()
    {
        return calleePatterns.values();
    }
        
    
    public WampList getRpcNames()
    {
        WampList names = new WampList();
        for(String name : calleeRegistrationByUri.keySet()) {
            WampCalleeRegistration registration = calleeRegistrationByUri.get(name);
            if(registration.getRemoteMethodsCount() > 0) {
                names.add(name);
            }
        }
        return names;
    }    

    public void processRegisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.getLong(1);
        WampDict options = (WampDict) request.get(2);
        String methodUriOrPattern = request.getText(3);
        WampMatchType matchType = WampMatchType.exact;
        if (options != null && options.has("match")) {
            matchType = WampMatchType.valueOf(options.getText("match").toLowerCase());
        }
        
        if (app.getLocalRPC(matchType, methodUriOrPattern) != null) {
            if (requestId != null) {
                WampProtocol.sendErrorMessage(clientSocket, WampProtocol.REGISTER, requestId, null, "wamp.error.procedure_already_exists", null, null);
            }
            throw new WampException(null, "wamp.error.procedure_already_exists", null, null);
        }
        if (matchType == WampMatchType.prefix && !methodUriOrPattern.endsWith("..")) {
            methodUriOrPattern = methodUriOrPattern + "..";
        }

        String realmName = this.getRealmName();
        WampCalleeRegistration registration = calleeRegistrationByUri.get(methodUriOrPattern);        
        if(registration == null) {
            Long registrationId = WampProtocol.newRouterScopeId();
            registration = new WampCalleeRegistration(realmName, registrationId, matchType, methodUriOrPattern);
            // TODO: move to WampModule:
            calleeRegistrationById.put(registrationId, registration);
            calleeRegistrationByUri.put(methodUriOrPattern, registration);
            if (matchType != WampMatchType.exact) {
                calleePatterns.put(methodUriOrPattern, registration);
            }
        }
        
        try {
            WampModule module = app.getDefaultWampModule();
            module.onRegister(clientSocket, registration.getId(), methodUriOrPattern, registration, matchType, methodUriOrPattern, request);
            if (requestId != null) {
                WampProtocol.sendRegisteredMessage(clientSocket, requestId, registration.getId());
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error registering method", ex);
            if (requestId != null) {
                WampProtocol.sendErrorMessage(clientSocket, WampProtocol.REGISTER, requestId, null, "wamp.error.not_authorized", null, null);
            }
        }
    }

    
    public void processUnregisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.getLong(1);
        Long registrationId = request.getLong(2);
        try {
            WampCalleeRegistration registration = calleeRegistrationById.get(registrationId);
            if(registration == null) {
                throw new Exception("method registration id not found: " + registrationId);
            } else {
                WampModule module = app.getDefaultWampModule();
                module.onUnregister(clientSocket, registration);

                /* FIXME: not thread safe and it should also be executed on session goodbye
                if (registration.getRemoteMethodsCount() == 0) {
                    for (String name : calleeRegistrationByUri.keySet()) {
                        if (WampBroker.isUriMatchingWithRegExp(name, registration.getRegExp())) {
                            calleeRegistrationByUri.remove(name);
                        }
                    }
                    for (String name : calleePatterns.keySet()) {
                        if (WampBroker.isUriMatchingWithRegExp(name, registration.getRegExp())) {
                            calleePatterns.remove(name);
                        }
                    }
                    calleeRegistrationById.remove(registrationId);
                }
                */                            
                
                if (requestId != null) {
                    WampProtocol.sendUnregisteredMessage(clientSocket, requestId);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.FINE, "Error unregistering method", ex);
            if (requestId != null) {
                WampProtocol.sendErrorMessage(clientSocket, WampProtocol.UNREGISTER, requestId, null, "wamp.error.not_authorized", null, null);
            }
        }
    }
    
    
}
