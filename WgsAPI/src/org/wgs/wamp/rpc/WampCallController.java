package org.wgs.wamp.rpc;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdeferred.Deferred;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.type.WampMatchType;


public class WampCallController implements Runnable 
{
    private static final Logger logger = Logger.getLogger(WampCallController.class.getName());

    private String procedureURI;
    private WampApplication app;
    private WampSocket clientSocket;
    private WampList  arguments;
    private WampDict argumentsKw;
    private WampCallOptions callOptions;
    private Long callID;
    private WampList result;
    private WampDict resultKw;
    private boolean cancelled;
    private boolean done;

    private int pendingInvocationCount;
    private int remoteInvocationResults;
    private ConcurrentHashMap<AbstractMap.SimpleEntry<Long,Long>, WampInvocation> remoteInvocations;
    private Deferred<WampResult, WampException, WampResult> remoteInvocationsCompletionCallback;
    
    

    public WampCallController(WampApplication app, WampSocket clientSocket, Long callID, String procedureURI, WampCallOptions options, WampList arguments, WampDict argumentsKw) 
    {
        this.app = app;
        this.clientSocket = clientSocket;
        this.callID  = callID;
        this.procedureURI = procedureURI;
        this.callOptions = options;
        this.arguments = arguments;
        this.argumentsKw = argumentsKw;
        this.remoteInvocations = new ConcurrentHashMap<AbstractMap.SimpleEntry<Long,Long>, WampInvocation>();
    }
    
    public synchronized void incrementRemoteInvocationResults()
    {
        remoteInvocationResults++;
    }
    
    public synchronized void setPendingInvocationCount(int count)
    {
        this.pendingInvocationCount = count;
    }    
    
    public synchronized void decrementPendingInvocationCount()
    {
        pendingInvocationCount--;
    }    
    
    public void setRemoteInvocationsCompletionCallback(Deferred<WampResult, WampException, WampResult> callback)
    {
        this.remoteInvocationsCompletionCallback = callback;
    }
    
    public Deferred<WampResult, WampException, WampResult> getRemoteInvocationsCompletionCallback()
    {
        return this.remoteInvocationsCompletionCallback;
    }
    
    

    public void addRemoteInvocation(Long socketId, Long remoteInvocationId, WampInvocation invocation)
    {
        remoteInvocations.put(new AbstractMap.SimpleEntry<Long,Long>(socketId, remoteInvocationId), invocation);
    }
    
    public WampInvocation getRemoteInvocation(Long socketId, Long remoteInvocationId)    
    {
        return remoteInvocations.get(new AbstractMap.SimpleEntry<Long,Long>(socketId, remoteInvocationId));
    }
    
    public synchronized WampInvocation removeRemoteInvocation(Long socketId, Long remoteInvocationId)    
    {
        WampResult wampResult = null;
        WampInvocation retval = remoteInvocations.remove(new AbstractMap.SimpleEntry<Long,Long>(socketId, remoteInvocationId));

        if(!done && !cancelled && pendingInvocationCount <= 0 && remoteInvocations.size() <= 0 && remoteInvocationsCompletionCallback != null) {
            done = true;

            wampResult = new WampResult(callID);
            wampResult.setArgs(getResult());
            wampResult.setArgsKw(getResultKw());
        }              

        if(wampResult != null) {
            remoteInvocationsCompletionCallback.resolve(wampResult);
        }
        
        return retval;
    }
    
    
    /*
    public Set<AbstractMap.SimpleEntry<Long,Long>> getRemoteInvocations()
    {
        return remoteInvocations.keySet();
    }
    */

    
    public WampSocket getClientSocket()
    {
        return clientSocket;
    }

    /*
    public void setFuture(Future<?> future) {
        this.future = future;
    }
    */
    
    public boolean isRemoteMethod()
    {
        WampMethod method = app.searchLocalRPC(procedureURI);        
        return (method == null);
    }
    
    public boolean isCancelled() {
        return !done && cancelled;
    }    
    
    public String getProcedureURI()
    {
        return procedureURI;
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
            
            Promise<WampResult, WampException, WampResult> promise = module.onCall(this, clientSocket, procedureURI, arguments, argumentsKw, callOptions);
            promise.done(new DoneCallback<WampResult>() {
                @Override
                public void onDone(WampResult wampResult) {
                    WampCallController.this.setResult(wampResult.getArgs());
                    WampCallController.this.setResultKw(wampResult.getArgsKw());
                    WampCallController.this.sendCallResults();
                }
            });

            promise.progress(new ProgressCallback<WampResult>() {
                @Override
                public void onProgress(WampResult progress) {
                    if(!isCancelled()) {
                        if(clientSocket.supportsProgressiveCallResults() && callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                            try {
                                WampDict details = progress.getDetails();
                                if(details == null) details = new WampDict();
                                details.put("progress", true);
                                WampProtocol.sendResultMessage(clientSocket, getCallID(), details, progress.getArgs(), progress.getArgsKw());
                            } catch(Exception ex) { 
                                System.out.println("SEVERE: WampCallController.sendResultMessage: " + ex.getMessage());
                            }
                        } else {
                            getResultKw().putAll(progress.getArgsKw());
                            if(progress.getArgs() != null) {
                                getResult().add(progress.getArgs());
                            }
                        }
                    }
                }
            });

            promise.fail(new FailCallback<WampException>() {
                @Override
                public void onFail(WampException error) {
                    WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, WampCallController.this.getCallID(), error.getDetails(), error.getErrorURI(), error.getArgs(), error.getArgsKw());
                }
            });

            
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
        if (isCancelled()) {
            System.out.println("RPC cancelled by caller: " + callID);
            WampProtocol.sendErrorMessage(clientSocket, WampProtocol.CALL, callID, null, WampException.ERROR_PREFIX + ".CanceledByCaller", null, null);
        } else {
            try {
                WampProtocol.sendResultMessage(clientSocket, callID, null, getResult(), getResultKw());
            } catch(Exception ex) {
                System.out.println("WARN: WampCallController.sendCallResults: error: " + ex.getMessage());
            }
        }

        clientSocket.removeCallController(callID);
    }
    
    
    public void cancel(WampDict cancelOptions) {
        if(!done) {
            cancelled = true;

            if(remoteInvocationsCompletionCallback != null && !remoteInvocationsCompletionCallback.isRejected()) {
                WampException cancelException = new WampException(cancelOptions, "wgs.cancel_invocation", null, null);                
                try { remoteInvocationsCompletionCallback.reject(cancelException); }
                catch(Exception ex) { }
            }          
            
            if(remoteInvocations != null) {
                for(Entry<SimpleEntry<Long,Long>,WampInvocation> entry : remoteInvocations.entrySet()) {
                    Long socketId = entry.getKey().getKey();
                    Long remoteInvocationId = entry.getKey().getValue();
                    WampInvocation invocation = entry.getValue();
                    if(invocation != null) {
                        Deferred<WampResult,WampException,WampResult> deferred = invocation.getAsyncCallback();
                        WampException cancelException = new WampException(remoteInvocationId, cancelOptions, "wgs.cancel_invocation", null, null);
                        if(deferred != null && !deferred.isRejected()) {
                            try { deferred.reject(cancelException); }
                            catch(Exception ex) { }
                            finally {
                                removeRemoteInvocation(socketId, remoteInvocationId);
                            }
                        }
                    }
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
