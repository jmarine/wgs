package org.wgs.wamp.rpc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;


public class WampCallController implements Runnable 
{
    private static final Logger logger = Logger.getLogger(WampCallController.class.getName());

    private String procedureURI;
    private WampApplication app;
    private WampSocket clientSocket;
    private Future<?> future;
    private WampAsyncCall asyncCall;
    private WampList  arguments;
    private WampDict argumentsKw;
    private WampCallOptions callOptions;
    private Long callID;
    private WampList result;
    private WampDict resultKw;
    private boolean cancelled;
    private boolean done;

    private int remoteInvocationResults;
    private ConcurrentHashMap<Long, WampAsyncCall> remoteInvocations;
    private WampAsyncCallback remoteInvocationsCompletionCallback;
    
    

    public WampCallController(WampApplication app, WampSocket clientSocket, Long callID, String procedureURI, WampCallOptions options, WampList arguments, WampDict argumentsKw) 
    {
        this.app = app;
        this.clientSocket = clientSocket;
        this.callID  = callID;
        this.procedureURI = procedureURI;
        this.callOptions = options;
        this.arguments = arguments;
        this.argumentsKw = argumentsKw;
    }
    
    public synchronized void incrementRemoteInvocationResults()
    {
        remoteInvocationResults++;
    }
    
    public void setRemoteInvocationsCompletionCallback(WampAsyncCallback callback)
    {
        this.remoteInvocationsCompletionCallback = callback;
    }
    
    public synchronized void addRemoteInvocation(Long remoteInvocationId, WampAsyncCall asyncCall)
    {
        if(remoteInvocations == null) { 
            remoteInvocations = new ConcurrentHashMap<Long, WampAsyncCall>();
        }
       
        remoteInvocations.put(remoteInvocationId, asyncCall);
    }
    
    public synchronized WampAsyncCall getRemoteInvocation(Long remoteInvocationId)    
    {
        if(remoteInvocations != null) { 
            WampAsyncCall retval = remoteInvocations.get(remoteInvocationId);
            return retval;
        } else {
            return null;
        }
    }
        
    
    public synchronized WampAsyncCall removeRemoteInvocation(Long remoteInvocationId)    
    {
        if(remoteInvocations != null) { 
            WampAsyncCall retval = remoteInvocations.remove(remoteInvocationId);
            if(!done && remoteInvocations.size() <= 0 && remoteInvocationsCompletionCallback != null) {
                if((result.size() == 1) && (remoteInvocationResults == 1) && (result.get(0) instanceof WampList)) {
                    result = (WampList)result.get(0);
                }   
                
                WampResult wampResult = new WampResult(callID);
                wampResult.setArgs(getResult());
                wampResult.setArgsKw(getResultKw());
                remoteInvocationsCompletionCallback.resolve(wampResult);
            }              
            return retval;
        } else {
            return null;
        }
    }
    
    
    public Set<Long> getRemoteInvocations()
    {
        if(remoteInvocations != null) {
            return remoteInvocations.keySet();
        } else {
            return null;
        }
    }
    
    public WampSocket getClientSocket()
    {
        return clientSocket;
    }
    
    public void setFuture(Future<?> future) {
        this.future = future;
    }
    
    public boolean isRemoteMethod()
    {
        WampMethod method = app.getLocalRPC(procedureURI);        
        return (method == null);
    }
    
    public boolean isCancelled() {
        return !done && cancelled;
    }    
    
    public String getProcedureURI()
    {
        return procedureURI;
    }
    
    public WampAsyncCall getAsyncCall()
    {
        return asyncCall;
    }

    @Override
    public void run() 
    {
        WampModule module = app.getWampModule(procedureURI, app.getDefaultWampModule());
        if(callID == null || callID == 0L) {
            WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, callID, null, WampException.ERROR_PREFIX + ".requestid_unknown", null, null);
            return;
        }        
        
        try {
            if (module == null) {
                throw new Exception("ProcURI not implemented");
            }

            setResult(new WampList());
            setResultKw(new WampDict());
            
            Object response = module.onCall(this, clientSocket, procedureURI, arguments, argumentsKw, callOptions);
            if(logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "WampCallController: " + procedureURI + ": result = " + response);
            
        } catch (Throwable ex) {
            
            if (ex instanceof java.lang.reflect.InvocationTargetException) {
                ex = ex.getCause();
            }
            if (ex instanceof WampException) {
                WampException wex = (WampException) ex;
                if (!isCancelled()) {
                    WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, callID, wex.getDetails(), wex.getErrorURI(), wex.getArgs(), wex.getArgsKw() );
                }
                logger.log(Level.SEVERE, "Error calling method " + procedureURI, wex);
            } else {
                if (!isCancelled()) {
                    System.out.println("Error calling method " + procedureURI + ": " + ex.getMessage());
                    WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, callID, null, WampException.ERROR_PREFIX+".call_error", null, null);
                }
                logger.log(Level.SEVERE, "Error calling method " + procedureURI, ex);
            }
        }
    }
    
    
    public void sendCallResults()
    {
        if(done) {
            logger.severe("New result from completed CALL ID " + callID);
        } else {
            if (isCancelled()) {
                System.out.println("RPC cancelled by caller: " + callID);
                WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, callID, null, WampException.ERROR_PREFIX + ".CanceledByCaller", null, null);
            } else {
                WampProtocol.sendResultMessage(clientSocket, callID, null, getResult(), getResultKw());
                done = true;
            }

            clientSocket.removeCallController(callID);
        }
    }
    
    
    public void cancel(WampDict cancelOptions) {
        if(!done) {
            cancelled = true;

            if (future != null) {
                String cancelMode = cancelOptions.has("mode")? cancelOptions.getText("mode") : null;
                boolean mayInterruptIfRunning = (cancelMode != null) && !cancelMode.equalsIgnoreCase("skip");
                future.cancel(mayInterruptIfRunning);
            }
            
            if(asyncCall != null) {
                asyncCall.cancel(cancelOptions);
            }

            if(remoteInvocations != null) {
                for(Long remoteInvocationId : getRemoteInvocations()) {
                    WampAsyncCall invocation = removeRemoteInvocation(remoteInvocationId);
                    invocation.cancel(cancelOptions);
                }            
            }
        }
    }

    /**
     * @return the arguments
     */
    public WampList getArguments() {
        return arguments;
    }

    /**
     * @param arguments the arguments to set
     */
    public void setArguments(WampList arguments) {
        this.arguments = arguments;
    }

    /**
     * @return the argumentsKw
     */
    public WampDict getArgumentsKw() {
        return argumentsKw;
    }
    
    /**
     * @param argumentsKw the arguments to set
     */
    public void setArgumentsKw(WampDict argumentsKw) {
        this.argumentsKw = argumentsKw;
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
    public WampList getResult() {
        return result;
    }

    /**
     * @param result the result to set
     */
    public void setResult(WampList result) {
        this.result = result;
    }

    /**
     * @return the resultKw
     */
    public WampDict getResultKw() {
        return resultKw;
    }

    /**
     * @param resultKw the resultKw to set
     */
    public void setResultKw(WampDict resultKw) {
        this.resultKw = resultKw;
    }
    
}
