package org.wgs.wamp;

import org.wgs.security.OpenIdConnectUtils;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.websocket.CloseReason;
import org.wgs.security.User;
import org.wgs.wamp.api.WampAPI;

import org.wgs.security.WampCRA;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.type.WampConnectionState;
import org.wgs.wamp.type.WampMatchType;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampApplication 
{
    public  static final int WAMPv1 = 1;
    public  static final int WAMPv2 = 2;   

    private static final Logger logger = Logger.getLogger(WampApplication.class.getName());

    private int     wampVersion;
    private String  path;
    private boolean started;
    private Map<String,WampModule> modules;
    private WampModule defaultModule;
    private ExecutorService executorService;
    private ConcurrentHashMap<String,WampSocket> sockets;    
    
    private TreeMap<String,WampMethod> rpcsByName;
    private Map<String,Set<Long>> sessionsByUserId = new ConcurrentHashMap<String,Set<Long>>();
    
    
    public WampApplication(int version, String path)
    {
        try {
            InitialContext ctx = new InitialContext();
            executorService = (ExecutorService)ctx.lookup("concurrent/WampRpcExecutorService");
        } catch(Exception ex) { }
        
        this.wampVersion = version;
        this.path = path;
        
        this.sockets = new ConcurrentHashMap<String,WampSocket>();
        this.modules = new HashMap<String,WampModule>();
        this.rpcsByName = new TreeMap<String,WampMethod>();

        this.registerWampModule(new WampAPI(this));
        
        this.defaultModule = new WampModule(this);
        //WampServices.registerApplication(path, this);
    }
    
    public int getWampVersion() {
        return wampVersion;
    }
    
    public void setWampVersion(int version) {
        this.wampVersion = version;
    }
    
    public synchronized boolean start() {
        boolean val = started;
        started = true;
        return !val;
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
                int pos = moduleName.lastIndexOf(".");
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

    
    public void onWampOpen(WampSocket clientSocket) 
    {
        for(WampModule module : modules.values()) {
            try { 
                module.onConnect(clientSocket); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
            }
        }                
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
        
        if(authMethods == null || authMethods.size() == 0) {
            authMethods.add("anonymous");
        } 
        
        for(int i = 0; i < authMethods.size(); i++) {
            try { 
                String authMethod = authMethods.getText(i);
                if(authMethod.equalsIgnoreCase("anonymous")) {
                    onWampSessionEstablished(clientSocket);
                    break;
                } else if(authMethod.equalsIgnoreCase("cookie")) {
                    // TODO
                } else if(authMethod.equalsIgnoreCase("wampcra") 
                        && helloDetails.has("authkey") && helloDetails.getText("authkey") != null) {
                    String authKey = helloDetails.getText("authkey");
                    WampDict authDetails = new WampDict();
                    if(helloDetails.has("salt")) authDetails.put("salt", helloDetails.getText("salt"));
                    if(helloDetails.has("keylen")) authDetails.put("keylen", helloDetails.getLong("keylen"));
                    if(helloDetails.has("iterations")) authDetails.put("iterations", helloDetails.getLong("iterations"));
                    String authChallenge = WampCRA.getChallenge(clientSocket, authKey, authDetails);

                    WampDict extra = new WampDict();
                    extra.put("authchallenge", authChallenge);
                    WampProtocol.sendChallengeMessage(clientSocket, authMethod, extra);
                    break;
                    
                } else if(authMethod.equalsIgnoreCase("oauth2-providers-list")) {
                    String redirectUrl = helloDetails.getText("_oauth2_redirect_uri");
                    WampDict extra = OpenIdConnectUtils.getProviders(redirectUrl);
                    WampProtocol.sendChallengeMessage(clientSocket, "oauth2", extra);
                    break;
                } else if(authMethod.startsWith("oauth2")) {
                    String subject = helloDetails.getText("_oauth2_subject");
                    String redirectUrl = helloDetails.getText("_oauth2_redirect_uri");
                    String state = helloDetails.getText("_oauth2_state");
                    String url = OpenIdConnectUtils.getAuthURL(redirectUrl, subject, state);

                    WampDict extra = new WampDict();
                    extra.put("_oauth2_provider_url", url);
                    WampProtocol.sendChallengeMessage(clientSocket, authMethod, extra);
                    break;
                }
                
            } catch(WampException ex) {
                WampProtocol.sendAbort(clientSocket, ex.getErrorURI(), null);
                break;
                
            } catch(Exception ex) { 
                WampProtocol.sendAbort(clientSocket, "wamp.error.authentication_failed", ex.getMessage());
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
                onUserLogon(clientSocket, null, WampConnectionState.ANONYMOUS);
                welcomed = true;
            } else if(authmethod.equals("cookie")) {
                // TODO
            } else if(authmethod.equals("wampcra")) {
                WampCRA.verifySignature(this, clientSocket, signature);
                welcomed = true;
            } else if(authmethod.startsWith("oauth2")) {
                String code = signature;
                clientSocket.setAuthMethod(authmethod);
                clientSocket.setAuthProvider(extra.getText("authprovider"));
                OpenIdConnectUtils.verifyCodeFlow(this, clientSocket, code, extra);
                welcomed = true;
            } 
            
        } catch(Exception ex) {
            logger.warning("WampApplication.processAuthenticationMessage: challenge error: " + ex.getMessage());
            welcomed = false;
        }
        
        if(welcomed) {
            onWampSessionEstablished(clientSocket);
        } else {
            WampProtocol.sendAbort(clientSocket, "wamp.error.authentication_failed", "error verifying authentication challege");
        }
    }
    
    
    private void onWampSessionEstablished(WampSocket clientSocket) 
    {
        WampProtocol.sendWelcomeMessage(this, clientSocket);

        // Notify modules:
        for(WampModule module : modules.values()) {
            try { 
                module.onSessionEstablished(clientSocket, clientSocket.getRealm(), clientSocket.getHelloDetails()); 
            } catch(Exception ex) {
                logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
            }
        }                 
    }
    
    public void onWampSessionEnd(WampSocket clientSocket) 
    {
        if(clientSocket.getRealm() != null) {

            // Notify modules:
            for(WampModule module : modules.values()) {
                try { 
                    module.onSessionEnd(clientSocket); 
                } catch(Exception ex) {
                    logger.log(Level.SEVERE, "Error disconnecting socket:", ex);
                }
            }         
        
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

            // Remove RPC registrations
            WampModule module = getDefaultWampModule();            
            for(WampCalleeRegistration registration : clientSocket.getRpcRegistrations()) {
                try {
                    module.onUnregister(clientSocket, registration.getId());
                } catch(Exception ex) { }
            }        

        }
        
        // Clear session realm
        clientSocket.setRealm(null);
    }
    
    public void onWampMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long requestType = request.getLong(0);
        //logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});

        switch(requestType.intValue()) {
            case WampProtocol.HELLO:
                String realmName = request.getText(1);
                WampDict helloDetails = (WampDict)request.get(2);
                onWampSessionStart(clientSocket, realmName, helloDetails);
                break;
            case WampProtocol.WELCOME:
                // processWelcomeMessage(clientSocket, request);
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
                WampProtocol.sendGoodBye(clientSocket, "wamp.close.normal", null);
                onWampSessionEnd(clientSocket);
                break;
            case WampProtocol.HEARTBEAT:
                processHeartbeatMessage(clientSocket, request);
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
                    WampProtocol.sendError(clientSocket, WampProtocol.UNSUBSCRIBE, requestId2, null, "wamp.error.protocol_violation", null, null);
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
                WampRealm registrationRealm = WampRealm.getRealm(clientSocket.getRealm());
                registrationRealm.processRegisterMessage(this, clientSocket, request);
                break;
            case WampProtocol.UNREGISTER:
                WampRealm unregistrationRealm = WampRealm.getRealm(clientSocket.getRealm());
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

            case WampProtocol.INVOCATION:
                // TODO: this server implementation only implements the "dealear" role
                // (it doesn't receive invocatoin messages)
            case WampProtocol.INTERRUPT:
                // TODO: this server implementation only implements the "dealear" role
                // (it doesn't receive interrupt messages)
            default:
                logger.log(Level.SEVERE, "Request type not implemented: {0}", new Object[]{requestType});
        }


    }
    
    
    public void onWampClose(WampSocket clientSocket, CloseReason reason) 
    {
        if(clientSocket != null) {

            clientSocket.close(reason);
            clientSocket.setState(WampConnectionState.OFFLINE);

            onWampSessionEnd(clientSocket);

            onUserLogout(clientSocket);
            
            for(WampModule module : modules.values()) {
                try { 
                    module.onDisconnect(clientSocket); 
                } catch(Exception ex) {
                    logger.log(Level.SEVERE, "Error disconnecting client:", ex);
                }
            }
            
            logger.log(Level.INFO, "Socket disconnected: {0}", new Object[] {clientSocket.getSessionId()});
        }
        
    }
    
    public String normalizeModuleName(String moduleName) 
    {
        int schemaPos = moduleName.indexOf(":");
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
    
    
    public void onUserLogon(WampSocket socket, User user, WampConnectionState state)
    {
        socket.setUserPrincipal(user);
        socket.setState(state);
        if(user != null) {
            Set<Long> sessions = sessionsByUserId.get(user.getUid());
            if(sessions == null) {
                sessions = new HashSet<Long>();
                sessionsByUserId.put(user.getUid(), sessions);
            }
            sessions.add(socket.getSessionId());
        }
    }
    
    public void onUserLogout(WampSocket socket)
    {
        Principal principal = socket.getUserPrincipal();
        socket.setUserPrincipal(null);
        socket.setState(WampConnectionState.ANONYMOUS);
        
        if(principal != null && principal instanceof User) {
            User user = (User)principal;
            Set<Long> sessions = sessionsByUserId.get(user.getUid());
            sessions.remove(socket.getSessionId());
            if(sessions.size() == 0) {
                sessionsByUserId.remove(user);
            }
        }
    }    
    
    public Set<Long> getSessionsByUser(User user)
    {
        return sessionsByUserId.get(user.getUid());
    }
    
    
    public void createRPC(String name, WampMethod rpc)
    {
        this.rpcsByName.put(name,rpc);
    }
    
    public void removeRPC(String name)
    {
        this.rpcsByName.remove(name);
    }

    
    public WampMethod getLocalRPC(String name)
    {
        return rpcsByName.get(name);
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

    
    private void processHeartbeatMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long incomingHeartbeatSeq = request.getLong(1);
        Long outgoingHeartbeatSeq = request.getLong(2);
        clientSocket.setIncomingHeartbeat(outgoingHeartbeatSeq);        
    }
    
    private void processPrefixMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        String prefix = request.getText(1);
        String url = request.getText(2);
	clientSocket.registerPrefixURL(prefix, url);
    }

    private void processHeartBeat(WampSocket clientSocket, WampList request) throws Exception
    {
        Long heartbeatSequenceNo = request.getLong(1);
        clientSocket.setIncomingHeartbeat(heartbeatSequenceNo);
    }

    public void processEventMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception
    {    
        for(WampModule module : this.modules.values()) {
            module.onEvent(clientSocket, request);
        }
    }
    

    
    private void processCancelCallMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long callID  = request.getLong(1);
        WampDict cancelOptions = (WampDict)request.get(2);
        WampCallController call = clientSocket.getRpcController(callID);
        call.cancel(cancelOptions);
    }
    
    private void processCallMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        WampCallController call = new WampCallController(this, clientSocket, request);
        
        if(executorService == null || call.isRemoteMethod()) {  
            // Ordering guarantees (RPC).
            call.run();
            
        } else {
            Future<?> future = executorService.submit(call);
            call.setFuture(future);
        }
        
    }
    

    private void processInvocationResult(WampSocket providerSocket, WampList request) throws Exception
    {
        Long invocationId = request.getLong(1);
        WampAsyncCallback callback = providerSocket.getAsyncCallback(invocationId);
        WampDict details = (WampDict)request.get(2);
        WampList result = (request.size() > 3) ? (WampList)request.get(3) : null;
        WampDict resultKw = (request.size() > 4) ? (WampDict)request.get(4) : null;
        if(details != null && details.has("progress") && details.getBoolean("progress")) {
            callback.progress(invocationId,details,result, resultKw);
        } else {
            try {
                callback.resolve(invocationId,details,result,resultKw);
                providerSocket.removeAsyncCallback(invocationId);
            } catch(Exception ex) {
                throw ex;
            }
        }
    }
    
    private void processInvocationError(WampSocket providerSocket, WampList request) throws Exception
    {
        Long requestType = request.getLong(1);
        Long invocationId = request.getLong(2);
        WampDict options = new WampDict();
        String errorURI = request.getText(4);
        WampList args = (request.size() > 5) ? (WampList)request.get(5) : null;
        WampDict argsKw = (request.size() > 6) ? (WampDict)request.get(6) : null;
        WampAsyncCallback callback = providerSocket.getAsyncCallback(invocationId);
        callback.reject(invocationId, options, errorURI, args, argsKw);
        providerSocket.removeAsyncCallback(invocationId);
    }
    
    

    public String getPath() {
        return path;
    }
    
}
