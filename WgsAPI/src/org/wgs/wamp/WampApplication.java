package org.wgs.wamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.websocket.CloseReason;

import org.wgs.wamp.api.WampCRA;
import org.wgs.wamp.rpc.WampCallController;
import org.wgs.wamp.rpc.WampCallOptions;
import org.wgs.wamp.rpc.WampRemoteMethod;
import org.wgs.wamp.rpc.WampCalleeRegistration;
import org.wgs.wamp.rpc.WampAsyncCallback;
import org.wgs.wamp.rpc.WampMethod;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.topic.WampSubscription;
import org.wgs.wamp.topic.WampSubscriptionOptions;
import org.wgs.wamp.types.WampMatchType;
import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.types.WampList;


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
    private ConcurrentHashMap<String,WampCalleeRegistration> calleePatterns;
    private ConcurrentHashMap<Long,WampCalleeRegistration>   calleeRegistrationById;
    private ConcurrentHashMap<String,WampCalleeRegistration> calleeRegistrationByUri;
    
    
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
        this.calleeRegistrationById = new ConcurrentHashMap<Long,WampCalleeRegistration>();
        this.calleeRegistrationByUri = new ConcurrentHashMap<String,WampCalleeRegistration>();
        this.calleePatterns = new ConcurrentHashMap<String,WampCalleeRegistration>();
        
        this.registerWampModule(new WampCRA(this));        
        
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
    
    public void onWampMessage(WampSocket clientSocket, WampList request) throws Exception
    {
        Long requestType = request.getLong(0);
        //logger.log(Level.INFO, "Request type = {0}", new Object[]{requestType});

        switch(requestType.intValue()) {
            case WampProtocol.HELLO:
                clientSocket.setVersionSupport(WampApplication.WAMPv2);                
                clientSocket.setRealm(request.getText(1));
                clientSocket.setHelloDetails((WampDict)request.get(2));
                WampProtocol.sendWelcomeMessage(this, clientSocket);
                break;
            case WampProtocol.WELCOME:
                // processWelcomeMessage(clientSocket, request);
                break;                
            case WampProtocol.GOODBYE:
                clientSocket.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "wamp.close.normal"));
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
                processRegisterMessage(this, clientSocket, request);
                break;
            case WampProtocol.UNREGISTER:
                processUnregisterMessage(this, clientSocket, request);
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
            
            for(WampModule module : modules.values()) {
                try { 
                    module.onDisconnect(clientSocket); 
                } catch(Exception ex) {
                    logger.log(Level.SEVERE, "Error disconnecting client:", ex);
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
    
    
    public void createRPC(String name, WampMethod rpc)
    {
        this.rpcsByName.put(name,rpc);
    }
    
    public void removeRPC(String name)
    {
        this.rpcsByName.remove(name);
    }
    
    public WampMethod getLocalRPCs(String name, WampCallOptions options)
    {

        return rpcsByName.get(name);
    }
    
    public ArrayList<WampRemoteMethod> getRemoteRPCs(String name, WampCallOptions options)
    {
        ArrayList<WampRemoteMethod> retval = new ArrayList<WampRemoteMethod>();

        String partition = null;
        if(options != null && options.getRunOn() == WampCallOptions.RunOnEnum.partition) partition = options.getPartition();

        WampCalleeRegistration reg = calleeRegistrationByUri.get(name);
        if(reg != null) {
            for(WampRemoteMethod remoteMethod : reg.getRemoteMethods()) {
                if(remoteMethod.hasPartition(partition)) {
                    retval.add(remoteMethod);
                }
            }
        }
        
        for(WampCalleeRegistration registration : calleePatterns.values()) {
            if(WampBroker.isUriMatchingWithRegExp(name, registration.getRegExp())) {
                for(WampRemoteMethod remoteMethod : registration.getRemoteMethods()) {
                    if(remoteMethod.hasPartition(partition)) {
                        retval.add(remoteMethod);
                    }
                }
            }
        }
        
        return retval;
    }    
    
    public WampCalleeRegistration getRegistration(Long registrationId)
    {
        return calleeRegistrationById.get(registrationId);
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
    
    public void processRegisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.getLong(1);
        WampDict options = (WampDict)request.get(2);
        String methodUriOrPattern = clientSocket.normalizeURI(request.getText(3));
        WampMatchType matchType = WampMatchType.exact;
        if(options != null && options.has("match")) {
            matchType = WampMatchType.valueOf(options.getText("match").toLowerCase());
            
        }
        
        if(rpcsByName.get(methodUriOrPattern) != null) {  // Don't override system functions
            if(requestId != null) WampProtocol.sendError(clientSocket, WampProtocol.REGISTER, requestId, null, "wamp.error.procedure_already_exists", null, null);
            throw new WampException(null, "wamp.error.procedure_already_exists", null, null);
        }
        
        if(matchType == WampMatchType.prefix && !methodUriOrPattern.endsWith("..")) {
            methodUriOrPattern = methodUriOrPattern + "..";
        }


            
        WampCalleeRegistration registration = calleeRegistrationByUri.get(methodUriOrPattern);
        if(registration == null) {
            Long registrationId = WampProtocol.newId();  
            registration = new WampCalleeRegistration(registrationId, matchType, methodUriOrPattern);
            calleeRegistrationById.put(registrationId, registration);
            calleeRegistrationByUri.put(methodUriOrPattern, registration);
            if(matchType != WampMatchType.exact) calleePatterns.put(methodUriOrPattern, registration);
        }               
        
        try {
            WampModule module = app.getDefaultWampModule();
            module.onRegister(registration.getId(), clientSocket, registration, matchType, methodUriOrPattern, request);

            if(requestId != null) WampProtocol.sendRegisteredMessage(clientSocket, requestId, registration.getId());
            
        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
            if(requestId != null) WampProtocol.sendError(clientSocket, WampProtocol.REGISTER, requestId, null, "wamp.error.not_authorized", null, null);
        }
        
    }    

    public void processUnregisterMessage(WampApplication app, WampSocket clientSocket, WampList request) throws Exception 
    {
        Long requestId = request.getLong(1);
        Long registrationId = request.getLong(2);
        
        try {
            WampCalleeRegistration registration = calleeRegistrationById.get(registrationId);
            WampModule module = app.getDefaultWampModule();
            module.onUnregister(clientSocket, registrationId);
            //calleeRegistrationById.remove(registration.getProcedureURI());
            //calleeRegistrationPatterns.remove(registration.getProcedureURI());
            
            if(requestId != null) WampProtocol.sendUnregisteredMessage(clientSocket, requestId);

        } catch(Exception ex) {
            logger.log(Level.FINE, "Error in publishing to topic", ex);
            if(requestId != null) WampProtocol.sendError(clientSocket, WampProtocol.UNREGISTER, requestId, null, "wamp.error.not_authorized", null, null);
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
        
        if(executorService == null) {
            call.run();
        }
        else {
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
            callback.resolve(invocationId,details,result,resultKw);
            providerSocket.removeAsyncCallback(invocationId);
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
