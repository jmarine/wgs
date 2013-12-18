package org.wgs.wamp;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;


public class WampCallController implements Runnable 
{
    private static final Logger logger = Logger.getLogger(WampCallController.class.getName());
    
    private WampApplication app;
    private WampSocket clientSocket;
    private ArrayNode request;
    private Future<?> future;
    private WampCall<?> cancellableCall;
    
    private boolean cancelled;
    private boolean done;
    

    public WampCallController(WampApplication app, WampSocket clientSocket, ArrayNode request) {
        this.app = app;
        this.clientSocket = clientSocket;
        this.request = request;
    }
    
    public void setFuture(Future<?> future) {
        this.future = future;
    }
    
    public boolean isCancelled() {
        return !done && cancelled;
    }    

    @Override
    public void run() {
        String procURI = clientSocket.normalizeURI(request.get(2).asText());
        String baseURL = procURI;
        String method = "";

        int callMsgType = request.get(0).asInt();
        int callResponseMsgType = (callMsgType == 2) ? 3 : 34;
        int callErrorMsgType = (callMsgType == 2) ? 4 : 36;

        String callID  = request.get(1).asText();
        if(callID == null || callID.equals("")) {
            WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, WampException.WAMP_GENERIC_ERROR_URI, "CallID not present", null);
            return;
        }        
        
        clientSocket.addRpcController(callID, this);
        WampModule module = app.getWampModule(baseURL, null);
        if (module == null) {
            int methodPos = procURI.indexOf("#");
            if (methodPos != -1) {
                baseURL = procURI.substring(0, methodPos + 1);
                method = procURI.substring(methodPos + 1);
                module = app.getWampModule(baseURL, app.getDefaultWampModule());
            }
        }
        try {
            if (module == null) {
                throw new Exception("ProcURI not implemented");
            }
            
            ArrayNode args = null;
            WampCallOptions callOptions = null;
            ObjectMapper mapper = new ObjectMapper();
            if (clientSocket.getWampVersion() >= WampApplication.WAMPv2) {
                args = mapper.createArrayNode();
                if (request.size() > 2) {
                    if (request.get(3) instanceof ArrayNode) {
                        args = (ArrayNode) request.get(3);
                    } else {
                        args.add(request.get(3));
                    }
                }
                if (request.size() > 3) {
                    callOptions = new WampCallOptions((ObjectNode) request.get(4));
                }
            } else {
                args = mapper.createArrayNode();
                for (int i = 3; i < request.size(); i++) {
                    args.add(request.get(i));
                }
            }
            ArrayNode response = null;
            if (callOptions == null) {
                callOptions = new WampCallOptions(null);
            }
            Object result = module.onCall(this, clientSocket, method, args, callOptions);
            if(result != null && result instanceof WampCall) {
                cancellableCall = (WampCall)result;
                result = cancellableCall.call();
            }
            if (result == null || result instanceof ArrayNode) {
                response = (ArrayNode) result;
            } else {
                response = mapper.createArrayNode();
                response.add(mapper.valueToTree(result));
            }
            if (!isCancelled()) {
                WampProtocol.sendCallResult(clientSocket, callResponseMsgType, callID, response);
                done = true;
            }
        } catch (Throwable ex) {
            if (ex instanceof java.lang.reflect.InvocationTargetException) {
                ex = ex.getCause();
            }
            if (ex instanceof WampException) {
                WampException wex = (WampException) ex;
                if (!isCancelled()) {
                    WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, wex.getErrorURI(), wex.getErrorDesc(), wex.getErrorDetails());
                }
                logger.log(Level.FINE, "Error calling method " + method + ": " + wex.getErrorDesc());
            } else {
                if (!isCancelled()) {
                    WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, WampException.WAMP_GENERIC_ERROR_URI, "Error calling method " + method, ex.getMessage());
                }
                logger.log(Level.SEVERE, "Error calling method " + method, ex);
            }
        } finally {
            clientSocket.removeRpcController(callID);
            if (isCancelled()) {
                WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, WampApplication.WAMP_ERROR_URI + "#CanceledByCaller", "RPC cancelled by caller: " + callID, null);
            }
        }
    }
    
    public void cancel(String cancelMode) {
        if(!done) {
            cancelled = true;

            if (future != null) {
                boolean mayInterruptIfRunning = (cancelMode != null) && !cancelMode.equalsIgnoreCase("skip");
                future.cancel(mayInterruptIfRunning);
            }

            if(cancellableCall != null) {
                cancellableCall.cancel(cancelMode);
            }
        }
    }
    
}
