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
    private ArrayNode  arguments;
    private ObjectNode argumentsKw;
    private WampCallOptions callOptions;
    private Long callID;
    private ArrayNode result = null;
    private ObjectNode resultKw = null;
    
    
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
        String procedureURI = clientSocket.normalizeURI(request.get(3).asText());
        WampModule module = app.getWampModule(procedureURI, app.getDefaultWampModule());

        int callMsgType = request.get(0).asInt();
        int callResponseMsgType = (callMsgType == 2) ? 3 : 73;
        int callErrorMsgType = (callMsgType == 2) ? 4 : 74;

        callID  = request.get(1).asLong();
        if(callID == null || callID.equals("")) {
            WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, WampException.WAMP_GENERIC_ERROR_URI, "CallID not present", null);
            return;
        }        
        
        clientSocket.addRpcController(callID, this);
        
        try {
            if (module == null) {
                throw new Exception("ProcURI not implemented");
            }

            ObjectMapper mapper = new ObjectMapper();
            arguments = mapper.createArrayNode();
            argumentsKw = mapper.createObjectNode();
            setResult(mapper.createArrayNode());
            setResultKw(mapper.createObjectNode());
            if(clientSocket.getWampVersion() >= WampApplication.WAMPv2 && request.size() > 2) {
                callOptions = new WampCallOptions((ObjectNode) request.get(2));
                if (request.get(4) instanceof ArrayNode) {
                    arguments = (ArrayNode) request.get(4);
                } else {
                    arguments.add(request.get(4));
                }
                argumentsKw = (ObjectNode) request.get(5);
            } else {
                for (int i = 3; i < request.size(); i++) {
                    arguments.add(request.get(i));
                }
            }
            
            if (callOptions == null) {
                callOptions = new WampCallOptions(null);
            }

            Object response = module.onCall(this, clientSocket, procedureURI, arguments, callOptions);
            if(response != null && response instanceof WampCall) {
                cancellableCall = (WampCall)response;
                response = cancellableCall.call();
            }
            if(response != null) {
                if (response instanceof ObjectNode) {
                    setResultKw((ObjectNode) response);

                } else if (response instanceof ArrayNode) {
                    setResult((ArrayNode) response);
                    
                } else {
                    getResult().add(mapper.valueToTree(response));
                }
            }
            if (!isCancelled()) {
                WampProtocol.sendCallResult(clientSocket, callResponseMsgType, callID, getResult(), getResultKw());
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
                logger.log(Level.FINE, "Error calling method " + procedureURI + ": " + wex.getErrorDesc());
            } else {
                if (!isCancelled()) {
                    WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, WampException.WAMP_GENERIC_ERROR_URI, "Error calling method " + procedureURI, ex.getMessage());
                }
                logger.log(Level.SEVERE, "Error calling method " + procedureURI, ex);
            }
        } finally {
            clientSocket.removeRpcController(callID);
            if (isCancelled()) {
                WampProtocol.sendCallError(clientSocket, callErrorMsgType, callID, WampApplication.WAMP_ERROR_URI + ".CanceledByCaller", "RPC cancelled by caller: " + callID, null);
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

    /**
     * @return the arguments
     */
    public ArrayNode getArguments() {
        return arguments;
    }

    /**
     * @param arguments the arguments to set
     */
    public void setArguments(ArrayNode arguments) {
        this.arguments = arguments;
    }

    /**
     * @return the argumentsKw
     */
    public ObjectNode getArgumentsKw() {
        return argumentsKw;
    }


    /**
     * @return the options
     */
    public WampCallOptions getOptions() {
        return callOptions;
    }


    /**
     * @return the callID
     */
    public Long getCallID() {
        return callID;
    }

    /**
     * @return the result
     */
    public ArrayNode getResult() {
        return result;
    }

    /**
     * @param result the result to set
     */
    public void setResult(ArrayNode result) {
        this.result = result;
    }

    /**
     * @return the resultKw
     */
    public ObjectNode getResultKw() {
        return resultKw;
    }

    /**
     * @param resultKw the resultKw to set
     */
    public void setResultKw(ObjectNode resultKw) {
        this.resultKw = resultKw;
    }
    
}
