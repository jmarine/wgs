package org.wgs.wamp.rpc;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.types.WampDict;
import org.wgs.wamp.WampException;
import org.wgs.wamp.types.WampList;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;



public class WampCallController implements Runnable 
{
    private static final Logger logger = Logger.getLogger(WampCallController.class.getName());

    private String procedureURI;
    private WampApplication app;
    private WampSocket clientSocket;
    private WampList request;
    private Future<?> future;
    private WampAsyncCall asyncCall;
    private WampList  arguments;
    private WampDict argumentsKw;
    private WampCallOptions callOptions;
    private Long callID;
    private WampList result = null;
    private WampDict resultKw = null;
    private boolean cancelled;
    private boolean done;
    

    public WampCallController(WampApplication app, WampSocket clientSocket, WampList request) 
    {
        this.app = app;
        this.clientSocket = clientSocket;
        this.request = request;
        this.procedureURI = clientSocket.normalizeURI(request.getText(3));
    }
    
    
    public WampSocket getClientSocket()
    {
        return clientSocket;
    }
    
    public void setFuture(Future<?> future) {
        this.future = future;
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

        callID  = request.getLong(1);
        if(callID == null || callID == 0L) {
            WampProtocol.sendError(clientSocket, callID, null, WampException.ERROR_PREFIX + ".requestid_unknown", null, null);
            return;
        }        
        
        try {
            if (module == null) {
                throw new Exception("ProcURI not implemented");
            }

            setResult(new WampList());
            setResultKw(new WampDict());
            
            arguments = new WampList();
            argumentsKw = new WampDict();
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
            
            callOptions = new WampCallOptions((WampDict)request.get(2));
            if (callOptions == null) {
                callOptions = new WampCallOptions(null);
            }

            Object response = module.onCall(this, clientSocket, procedureURI, arguments, argumentsKw, callOptions);
            if(response != null && response instanceof WampAsyncCall) {
                asyncCall = (WampAsyncCall)response;
                if(!asyncCall.hasAsyncCallback()) asyncCall.setAsyncCallback(new WampAsyncCallback() {
                    @Override
                    public void resolve(Object... results) {
                        Long id = (Long)results[0];
                        WampDict details = (WampDict)results[1];
                        WampList result = (WampList)results[2];
                        WampDict resultKw = (WampDict)results[3];
                        if(getClientSocket().supportsProgressiveCallResults() && callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                            setResult(result);
                            setResultKw(resultKw);
                            //setDetails(details);
                            sendCallResults();                            
                        } else {
                            getResult().add(result);
                            getResultKw().putAll(resultKw);
                            sendCallResults();     
                        }
                    }

                    @Override
                    public void progress(Object... progressParams) {
                        Long id = (Long)progressParams[0];
                        WampDict details = (WampDict)progressParams[1];
                        WampList progress = (WampList)progressParams[2];
                        WampDict progressKw = (WampDict)progressParams[3];
                        if(getClientSocket().supportsProgressiveCallResults() && callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                            if(details==null) details = new WampDict();
                            details.put("progress", true);
                            WampProtocol.sendResult(getClientSocket(), getCallID(), details, progress, progressKw);
                        } else {
                            getResult().add(progress);
                            getResultKw().putAll(progressKw);
                        }
                    }

                    @Override
                    public void reject(Object... errors) {
                        Long invocationId = (Long)errors[0];
                        WampDict details  = (WampDict)errors[1];
                        String errorURI   = (String)errors[2];
                        WampList args     = (errors.length > 3) ? (WampList)errors[3] : null;
                        WampDict argsKw   = (errors.length > 4) ? (WampDict)errors[4] : null;
                        WampProtocol.sendError(clientSocket, callID, details, "wamp.error.invocation_error", args, argsKw);
                    }
                });
                asyncCall.call();
            } else {
                if(response != null) {
                    if (response instanceof WampDict) {
                        setResultKw((WampDict)response);

                    } else if (response instanceof WampList) {
                        setResult((WampList)response);

                    } else {
                        getResult().add(response);
                    }
                }
                sendCallResults();
            }
        } catch (Throwable ex) {
            
            if (ex instanceof java.lang.reflect.InvocationTargetException) {
                ex = ex.getCause();
            }
            if (ex instanceof WampException) {
                WampException wex = (WampException) ex;
                if (!isCancelled()) {
                    WampProtocol.sendError(clientSocket, callID, wex.getDetails(), wex.getErrorURI(), wex.getArgs(), wex.getArgsKw() );
                }
                logger.log(Level.SEVERE, "Error calling method " + procedureURI, wex);
            } else {
                if (!isCancelled()) {
                    System.out.println("Error calling method " + procedureURI + ": " + ex.getMessage());
                    WampProtocol.sendError(clientSocket, callID, null, WampException.ERROR_PREFIX+".call_error", null, null);
                }
                logger.log(Level.SEVERE, "Error calling method " + procedureURI, ex);
            }
        }
    }
    
    
    public void sendCallResults()
    {
        if (isCancelled()) {
            System.out.println("RPC cancelled by caller: " + callID);
            WampProtocol.sendError(clientSocket, callID, null, WampException.ERROR_PREFIX + ".CanceledByCaller", null, null);
        } else {
            WampProtocol.sendResult(clientSocket, callID, null, getResult(), getResultKw());
            done = true;
        }
        
        clientSocket.removeAsyncCallback(callID);           
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
