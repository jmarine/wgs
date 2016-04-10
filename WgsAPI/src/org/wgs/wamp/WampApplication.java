package org.wgs.wamp;

import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.websocket.CloseReason;
import org.jdeferred.Deferred;
import org.wgs.security.OpenIdConnectUtils;
import org.wgs.security.User;
import org.wgs.security.WampCRA;
import org.wgs.util.Social;
import org.wgs.wamp.api.WampAPI;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampInvocation;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampApplication 
{
    public  static final int WAMPv1 = 1;
    public  static final int WAMPv2 = 2;   

    private static final ConcurrentHashMap<String, WampApplication> apps = new ConcurrentHashMap<String, WampApplication>();
    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    private int     wampVersion;
    private String  path;
    private AtomicBoolean started;
    private Map<String,WampModule> modules;
    private WampModule defaultModule;
    private ExecutorService executorService;
    private ConcurrentHashMap<Long,WampSocket> sockets;    
    
    private TreeMap<String,WampMethod> rpcsByName;
    private TreeMap<String,WampMethod> rpcsByPattern;
    private Map<String,Set<Long>> wampSessionsByUserId = new ConcurrentHashMap<String,Set<Long>>();


    public static void registerWampApplication(int version, String path, WampApplication app)
    {
        apps.put(getAppKey(version, path), app);
    }
    
    public static synchronized WampApplication getInstance(int version, String path)
    {
        String key = getAppKey(version, path);
        WampApplication app = apps.get(key);
        if(app == null) {
            app = new WampApplication(version, path);
        }
        return app;
    }
    
    private static String getAppKey(int version, String path)
    {
        String key = path + "_" + version;
        return key;
    }
    
    
    public WampApplication(int version, String path)
    {
        try {
            InitialContext ctx = new InitialContext();
            executorService = (ExecutorService)ctx.lookup("concurrent/WampRpcExecutorService");
        } catch(Exception ex) { }
        
        this.started = new AtomicBoolean(false);
        this.wampVersion = version;
        this.path = path;
        
        this.sockets = new ConcurrentHashMap<Long,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        this.rpcsByName = new TreeMap<String,WampMethod>();
        this.rpcsByPattern = new TreeMap<String,WampMethod>();

        registerWampApplication(version, path, this);
        registerWampModules();
        
        this.defaultModule = new WampModule(this);
        
    }

    
    public String getPath() 
    {
        return path;
    }
    

    public void registerWampModules()
    {
        this.registerWampModule(new WampAPI(this));
    }
    
    public int getWampVersion() {
        return wampVersion;
    }
    
    public void setWampVersion(int version) {
        this.wampVersion = version;
    }
    
    public boolean start() {
        if(!started.getAndSet(true)) {
            WampCluster.startApplicationNode();
            return true;
        } else {
            return false;
        }
    }
    


    
    public WampModule getDefaultWampModule() {
        return defaultModule;
    }
    
    public Collection<WampModule> getWampModules()
    {
        return modules.values();
    }
    
    public WampModule getWampModule(String moduleName, WampModule defaultModule)
    {
        WampModule module = null;
        while(module == null && moduleName != null) {
            module = modules.get(normalizeModuleName(moduleName));
            if(module == null) {
                int pos = moduleName.lastIndexOf('.');
                if(pos != -1) moduleName = moduleName.substring(0, pos);
                else moduleName = null;
            }
        } 
        if(module == null) {
            module = defaultModule;
        }
        return module;
    }
    
    public String getServerId() 
    {
        return "wgs-server-2.0-alpha1";
    }


    public WampSocket getSocketById(Long socketId)
    {
        if(socketId == null) return null;
        else return sockets.get(socketId);
    }        
    
    
    public void onWampOpen(WampSocket clientSocket) 
    {
        sockets.put(clientSocket.getSocketId(), clientSocket);
    }
    
    
    public void onWampSessionStart(WampSocket clientSocket, String realm, WampDict helloDetails)
    {
        clientSocket.setGoodbyeRequested(false);
        clientSocket.setVersionSupport(WampApplication.WAMPv2);
        clientSocket.setRealm(realm);
        clientSocket.setHelloDetails(helloDetails);
        
        WampList authMethods = new WampList();
        if(helloDetails != null && helloDetails.has("authmethods")) {
            Object methods = helloDetails.get("authmethods");
            if(methods instanceof String) {
                authMethods.add(methods);
            } else if(methods instanceof WampList) {
                authMethods = (WampList)methods;
            }
        }
        
        if(authMethods.size() == 0) {
            authMethods.add("anonymous");
        } 
        
        for(int i = 0; i < authMethods.size(); i++) {
            try { 
                String authMethod = authMethods.getText(i);
                if(authMethod.equalsIgnoreCase("anonymous")) {
                    clientSocket.setAuthMethod("anonymous");
                    onUserLogon(clientSocket, null, WampConnectionState.ANONYMOUS, helloDetails);
                    onWampSessionEstablished(clientSocket, clientSocket.getSocketId(), clientSocket.getHelloDetails());
                    WampProtocol.sendWelcomeMessage(this, clientSocket);
                    break;
                } else if(authMethod.equalsIgnoreCase("ticket")) {
                    String authId = helloDetails.getText("authid");
                    if(authId.startsWith("wgs-")) {
                        clientSocket.setAuthMethod("ticket");
                        WampProtocol.sendChallengeMessage(clientSocket, authMethod, null);
                        break;
                    }
                    
                } else if(authMethod.equalsIgnoreCase("cookie")) {
                    // TODO
                } else if(authMethod.equalsIgnoreCase("wampcra") 
                        && helloDetails.has("authid") && helloDetails.getText("authid") != null) {
                    String authId = helloDetails.getText("authid");

                    WampDict challenge = WampCRA.getChallenge(clientSocket, authId);

                    WampProtocol.sendChallengeMessage(clientSocket, authMethod, challenge);
                    break;
                    
                } else if(authMethod.equals("oauth2")) {
                    String url = null;
                    WampDict extra = null;
                    String clientName = helloDetails.getText("_oauth2_client_name");
                    String redirectUrl = helloDetails.getText("_oauth2_redirect_uri");
                    String subject = helloDetails.getText("_oauth2_subject");

                    if(subject != null && subject.length() > 0) {
                        try { url = OpenIdConnectUtils.getAuthURL(clientName, redirectUrl, subject, null); }
                        catch(Exception ex) { }
                    }

                    extra = OpenIdConnectUtils.getProviders(clientName, redirectUrl);                        
                    if(url != null) extra.put("_oauth2_provider_url", url);

                    WampProtocol.sendChallengeMessage(clientSocket, authMethod, extra);
                    break;
                }
                
            } catch(WampException ex) {
                WampProtocol.sendAbortMessage(clientSocket, ex.getErrorURI(), null);
                break;
                
            } catch(Exception ex) { 
                WampProtocol.sendAbortMessage(clientSocket, "wamp.error.authentication_failed", ex.getMessage());
                System.err.println("Error: " +ex.getClass().getName() + ":" + ex.getMessage());
                ex.printStackTrace();
                break;
            }
        }
        
    }
    
    
    private void processAuthenticationMessage(WampSocket clientSocket, String signature, WampDict extra)
    {
        boolean welcomed = false;
        String authmethod = clientSocket.getAuthMethod();
        
        try {
            if(authmethod.equals("anonymous")) {
                onUserLogon(clientSocket, null, WampConnectionState.ANONYMOUS, extra);
                welcomed = true;
            } else if(authmethod.equals("ticket") && signature.equals(WampCluster.brokerId)) {
                User clusterNodeUser = new User();
                clusterNodeUser.setName("clusternode");
                clusterNodeUser.setUid(clientSocket.getHelloDetails().getText("authid").substring(4));                
                clientSocket.setAuthMethod("ticket");
                clientSocket.setAuthProvider("jms-cluster");
                onUserLogon(clientSocket, clusterNodeUser, WampConnectionState.AUTHENTICATED, extra);
                welcomed = true;
            } else if(authmethod.equals("cookie")) {
                // TODO
            } else if(authmethod.equals("wampcra")) {
                WampCRA.verifySignature(this, clientSocket, signature, extra);
                welcomed = true;
            } else if(authmethod.equals("oauth2")) {
                String code = signature;
                clientSocket.setAuthMethod(authmethod);
                clientSocket.setAuthProvider(extra.getText("authprovider"));
                OpenIdConnectUtils.verifyCodeFlow(this, clientSocket, code, extra);
                welcomed = true;
            } 
            
            if(!welcomed) {
                throw new Exception("authentication failed");
            } else {
                onWampSessionEstablished(clientSocket, clientSocket.getSocketId(), clientSocket.getHelloDetails());
                WampProtocol.sendWelcomeMessage(this, clientSocket);
            } 
            
        } catch(Exception ex) {
            logger.warning("WampApplication.processAuthenticationMessage: challenge error: " + ex.getMessage());
            WampProtocol.sendAbortMessage(clientSocket, "wamp.error.authentication_failed", "error verifying authentication challege");
        }
        

    }
    
    
    public void onWampSessionEstablished(WampSocket clientSocket, Long sessionId, WampDict details) 
    {
        clientSocket.setWampSessionId(sessionId);        

        for(WampModule module : modules.values()) {
            try { 
                module.onWampSessionEstablished(clientSocket, details); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
            }
        }     
        
    }
    

    public void onWampSessionEnd(WampSocket clientSocket) 
    {
        if(clientSocket.getWampSessionId() != null) {
            
            // First remove subscriptions to topic patterns:
            for(WampSubscription subscription : clientSocket.getSubscriptions()) {
                if(subscription.getOptions().getMatchType() != WampMatchType.exact) {  // prefix or wildcards
                    WampBroker.unsubscribeClientFromTopic(this, clientSocket, null, subscription.getId());
                }
            }

            // Then, remove remaining subscriptions to single topics:
            for(WampSubscription subscription : clientSocket.getSubscriptions()) {
                WampBroker.unsubscribeClientFromTopic(this, clientSocket, null, subscription.getId());
            }      

            // Notify modules:
            for(WampModule m : modules.values()) {
                try { 
                    m.onWampSessionEnd(clientSocket); 
                } catch(Exception ex) {
                    logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
                }
            }         

            // Remove RPC registrations
            clientSocket.setWampSessionId(null);
            

            WampModule module = getDefaultWampModule();            
            for(WampCalleeRegistration registration : clientSocket.getRpcRegistrations()) {
                try {
                    module.onUnregister(clientSocket, registration);
                } catch(Exception ex) { }
            }


            if(clientSocket.getInvocationIDs().size() > 0) {
                clientSocket.clearInvocations();
            }

            
            onUserLogout(clientSocket);
        }
        
        clientSocket.setRealm(null);
        
    }
    
    
    
    public void onWampMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long requestType = request.getLong(0);
        if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "RECEIVED MESSAGE TYPE: " + requestType);

        switch(requestType.intValue()) {
            case WampProtocol.HELLO:
                String realmName = request.getText(1);
                WampDict helloDetails = (request.size() > 2) ? (WampDict)request.get(2) : null;
                onWampSessionStart(clientSocket, realmName, helloDetails);
                break;
            case WampProtocol.ABORT:
                onWampSessionEnd(clientSocket);
                break;
            case WampProtocol.AUTHENTICATE:
                String signature = request.getText(1);
                WampDict extra = (request.size() > 2) ? (WampDict)request.get(2) : null;
                processAuthenticationMessage(clientSocket, signature, extra);
                break;
            case WampProtocol.GOODBYE:
                onWampSessionEnd(clientSocket);
                WampProtocol.sendGoodbyeMessage(clientSocket, "wamp.close.normal", null);
                break;
            case WampProtocol.ERROR:
                // FIXME: the current implementation only expects invocation errors
                processInvocationError(clientSocket, request);
                break;
            case WampProtocol.SUBSCRIBE:
                Long requestId1 = request.getLong(1);
                WampDict subOptionsNode = (request.size() > 2) ? (WampDict)request.get(2) : null;
                WampSubscriptionOptions subOptions = new WampSubscriptionOptions(subOptionsNode);
                String subscriptionTopicName = request.getText(3);
                WampBroker.subscribeClientWithTopic(this, clientSocket, requestId1, subscriptionTopicName, subOptions);
                break;
            case WampProtocol.UNSUBSCRIBE:
                Long requestId2 = (request.size() > 1) ? request.getLong(1) : null;
                Long subscriptionId2 = (request.size() > 2) ? request.getLong(2) : null;
                if(requestId2 == null || subscriptionId2 == null)  {
                    WampProtocol.sendErrorMessage(clientSocket, WampProtocol.UNSUBSCRIBE, requestId2, null, "wamp.error.protocol_violation", null, null);
                } else {
                    WampBroker.unsubscribeClientFromTopic(this, clientSocket, requestId2, subscriptionId2);
                }
                break;
            case WampProtocol.PUBLISH:
                WampBroker.processPublishMessage(this, clientSocket, request);
                break;                
            case WampProtocol.EVENT:
                processEventMessage(this, clientSocket, request);
                break;
            case WampProtocol.REGISTER:
                String registrationRealmName = clientSocket.getRealm();
                WampDict options = (WampDict)request.get(2);
                if(options.has("_cluster_peer_realm")) registrationRealmName = options.getText("_cluster_peer_realm");
                WampRealm registrationRealm = WampRealm.getRealm(registrationRealmName);
                registrationRealm.processRegisterMessage(this, clientSocket, request);
                break;
            case WampProtocol.UNREGISTER:
                Long registrationId = request.getLong(2);
                WampCalleeRegistration unregisterCalleeRegistration = WampRealm.getRegistration(registrationId);
                WampRealm unregistrationRealm = WampRealm.getRealm(unregisterCalleeRegistration.getRealmName());
                unregistrationRealm.processUnregisterMessage(this, clientSocket, request);
                break;                
            case WampProtocol.CALL:
                processCallMessage(clientSocket, request);
                break;
            case WampProtocol.CANCEL_CALL:
                processCancelCallMessage(clientSocket, request);
                break;
            case WampProtocol.YIELD:  // INVOCATION RESULT
                processInvocationResult(clientSocket, request);
                break;
                
            default:
                logger.log(Level.SEVERE, "Request type not implemented: {0}", new Object[]{requestType});
        }


    }
    
    
    public void onWampClose(WampSocket clientSocket, CloseReason reason) 
    {
        if(clientSocket != null) {

            onWampSessionEnd(clientSocket);
            
            clientSocket.close(reason);
            clientSocket.setState(WampConnectionState.OFFLINE);
            
            sockets.remove(clientSocket.getSocketId());
            
            logger.log(Level.FINEST, "Socket disconnected: {0}", new Object[] {clientSocket.getSocketId()});
        }

    }
    
    public String normalizeModuleName(String moduleName) 
    {
        int schemaPos = moduleName.indexOf(':');
        if(schemaPos != -1) moduleName = moduleName.substring(schemaPos+1);
        if(!moduleName.endsWith(".")) moduleName = moduleName + ".";
        return moduleName;
    }
    
    public void registerWampModule(WampModule module)
    {
        try {
            modules.put(normalizeModuleName(module.getModuleName()), module);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "WgsEndpoint: Error registering WGS module", ex);
        }        
    }
    
    
    public void onUserLogon(WampSocket socket, User user, WampConnectionState state, WampDict data)
    {
        socket.setUserPrincipal(user);
        socket.setState(state);
        
        if(user != null) {
            Set<Long> sessions = wampSessionsByUserId.get(user.getUid());
            if(sessions == null) {
                sessions = new HashSet<Long>();
                wampSessionsByUserId.put(user.getUid(), sessions);
            }
            sessions.add(socket.getSocketId());
        }
        
        if(user!= null && data.has("_notification_channel")) {
            String appClientName = data.getText("_oauth2_client_name");
            String notificationChannel = data.getText("_notification_channel");
            if(appClientName != null && notificationChannel != null) {
                Social.setUserPushChannel(user, appClientName, notificationChannel);
            }
        }          
    }
    
    public void onUserLogout(WampSocket socket)
    {
        Principal principal = socket.getUserPrincipal();
        socket.setUserPrincipal(null);
        socket.setState(WampConnectionState.ANONYMOUS);
        
        if(principal != null && principal instanceof User) {
            User user = (User)principal;
            Set<Long> sessions = wampSessionsByUserId.get(user.getUid());
            sessions.remove(socket.getSocketId());
            if(sessions.size() == 0) {
                wampSessionsByUserId.remove(user.getUid());
            }
        }
    }    
    
    public Set<Long> getSessionsByUser(User user)
    {
        return wampSessionsByUserId.get(user.getUid());
    }
    
    
    public void registerLocalRPC(WampMatchType matchType, String name, WampMethod rpc)
    {
        if(matchType == WampMatchType.exact) {
            this.rpcsByName.put(name, rpc);
        } else {
            if(matchType == WampMatchType.prefix) name = name + "..";
            String regExp = WampBroker.getPatternRegExp(matchType, name);
            this.rpcsByPattern.put(regExp, rpc);
        }
    }
    
    public void unregisterLocalRPC(WampMatchType matchType, String name)
    {
        if(matchType == WampMatchType.exact) {
            this.rpcsByName.remove(name);
        } else {
            if(matchType == WampMatchType.prefix) name = name + "..";
            String regExp = WampBroker.getPatternRegExp(matchType, name);
            this.rpcsByPattern.remove(regExp);
        }
    }

    public WampMethod searchLocalRPC(String name)
    {
        WampMethod method = rpcsByName.get(name);
        if(method == null && this.rpcsByPattern.size() > 0) {
            for(String regExp : this.rpcsByPattern.keySet()) {
                if(WampBroker.isUriMatchingWithRegExp(name, regExp)) {
                    return this.rpcsByPattern.get(regExp);
                }
            }
        }
        return method;
    }
    
    public WampMethod getLocalRPC(WampMatchType matchType, String name)
    {
        if(matchType == WampMatchType.exact) {
            return rpcsByName.get(name);
        } else {
            if(matchType == WampMatchType.prefix && !name.endsWith("..")) name = name + "..";
            String regExp = WampBroker.getPatternRegExp(matchType, name);
            return this.rpcsByPattern.get(regExp);
        }
        
    }

    
    public WampList getAllRpcNames(String realmName)
    {
        WampRealm realm = WampRealm.getRealm(realmName);
        WampList names = realm.getRpcNames();
        for(String name : rpcsByName.keySet()) {
            names.add(name);
        }
        return names;
    }

    
    public void processEventMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception
    {
        Long subscriptionId = request.getLong(1);        
        Long publicationId = request.getLong(2);
        WampDict details = (WampDict)request.get(3);
        WampList payload = (request.size() > 4) ? (WampList)request.get(4) : new WampList();
        WampDict payloadKw = (request.size() > 5)? (WampDict)request.get(5) : new WampDict();
        
        for(WampModule module : this.modules.values()) {
            module.onEvent(clientSocket, subscriptionId, publicationId, details, payload, payloadKw);
        }
    }

    
    private void processCancelCallMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long callID  = request.getLong(1);
        WampDict cancelOptions = (WampDict)request.get(2);
        WampCallController call = clientSocket.getCallController(callID);
        if(call != null) call.cancel(cancelOptions, null);
        else WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CANCEL_CALL, callID, null, "wamp.error.unknown_call", null, null);
    }
    
    private void processCallMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long callID = request.getLong(1);
        WampCallOptions options = new WampCallOptions((WampDict)request.get(2));
        String procedureURI = request.getText(3);
        WampList arguments = new WampList();
        WampDict argumentsKw = new WampDict();
        if(request.size()>4) {
            if (request.get(4) instanceof WampList) {
                arguments = (WampList) request.get(4);
            } else {
                arguments.add(request.get(4));
            }
        }
        if(request.size()>5) {
            argumentsKw = (WampDict)request.get(5);
        }

        WampCallController call = new WampCallController(this, clientSocket, callID, procedureURI, options, arguments, argumentsKw);
        clientSocket.addCallController(callID, call);

        if(executorService == null || call.isRemoteMethod()) {  
            // Ordering guarantees (RPC).
            call.run();

        } else {
            executorService.submit(call);
        }
        
    }
    

    private void processInvocationResult(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.getLong(1);
        WampResult result = new WampResult(invocationId);
        result.setDetails((WampDict)request.get(2));
        result.setArgs((request.size() > 3) ? (WampList)request.get(3) : null);
        result.setArgsKw((request.size() > 4) ? (WampDict)request.get(4) : null);

        WampInvocation invocation = providerSocket.getInvocation(invocationId);        
        if(invocation != null) 
        {
            Deferred<WampResult, WampException, WampResult> deferred = invocation.getAsyncCallback();
            try {
                if(result.isProgressResult()) {
                    deferred.notify(result);
                } else {
                    deferred.resolve(result);
                }
                
            } catch(Exception ex) {
                throw ex;
            }
        }

    }

    
    private void processInvocationError(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.getLong(2);
        WampDict options = new WampDict();
        String errorURI = request.getText(4);
        WampList args = (request.size() > 5) ? (WampList)request.get(5) : null;
        WampDict argsKw = (request.size() > 6) ? (WampDict)request.get(6) : null;
        WampException error = new WampException(invocationId, options, errorURI, args, argsKw);
        WampInvocation invocation = providerSocket.getInvocation(invocationId);        
        if(invocation != null) {
            Deferred<WampResult, WampException, WampResult> callback = invocation.getAsyncCallback();
            if(!callback.isRejected()) callback.reject(error); 
            providerSocket.removeInvocation(invocationId);
        }
    }
    

}
