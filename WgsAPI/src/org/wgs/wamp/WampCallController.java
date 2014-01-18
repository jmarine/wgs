package org.wgs.wamp;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;


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
            WampProtocol.sendCallError(clientSocket, callID, WampException.ERROR_PREFIX + ".requestid_unknown", "CallID not present", null);
            return;
        }        
        
        try {
            if (module == null) {
                throw new Exception("ProcURI not implemented");
            }

            arguments = new WampList();
            argumentsKw = new WampDict();
            setResult(new WampList());
            setResultKw(new WampDict());
            if(clientSocket.getWampVersion() >= WampApplication.WAMPv2 && request.size() > 2) {
                callOptions = new WampCallOptions((WampDict)request.get(2));
                if (request.get(4) instanceof WampList) {
                    arguments = (WampList) request.get(4);
                } else {
                    arguments.add(request.get(4));
                }
                argumentsKw = (WampDict)request.get(5);
            } else {
                for (int i = 3; i < request.size(); i++) {
                    arguments.add(request.get(i));
                }
            }
            
            if (callOptions == null) {
                callOptions = new WampCallOptions(null);
            }

            // TRY LOCAL CALL:
            Object response = module.onCall(this, clientSocket, procedureURI, arguments, argumentsKw, callOptions);
            if(response != null && response instanceof WampAsyncCall) {
                asyncCall = (WampAsyncCall)response;
                if(!asyncCall.hasAsyncCallback()) asyncCall.setAsyncCallback(new WampAsyncCallback() {
                    @Override
                    public void resolve(Object... results) {
                        WampList result = (WampList)results[0];
                        WampDict resultKw = (WampDict)results[1];
                        if(getClientSocket().supportProgressiveCalls() && callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                            setResult(result);
                            setResultKw(resultKw);
                            sendCallResults();                            
                        } else {
                            getResult().add(result);
                            getResultKw().putAll(resultKw);
                            sendCallResults();     
                        }
                    }

                    @Override
                    public void progress(Object... progressParams) {
                        WampList progress = (WampList)progressParams[0];
                        WampDict progressKw = (WampDict)progressParams[1];
                        if(getClientSocket().supportProgressiveCalls() && callOptions.getRunMode() == WampCallOptions.RunModeEnum.progressive) {
                            WampProtocol.sendCallProgress(getClientSocket(), getCallID(), progress, progressKw);
                        } else {
                            getResult().add(progress);
                            getResultKw().putAll(progressKw);
                        }
                    }

                    @Override
                    public void reject(Object... errors) {
                        WampProtocol.sendCallError(clientSocket, callID, (String)errors[0], null, errors[1]);
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
                    WampProtocol.sendCallError(clientSocket, callID, wex.getErrorURI(), wex.getErrorDesc(), wex.getErrorDetails());
                }
                logger.log(Level.FINE, "Error calling method " + procedureURI + ": " + wex.getErrorDesc());
            } else {
                if (!isCancelled()) {
                    WampProtocol.sendCallError(clientSocket, callID, WampException.ERROR_PREFIX+".call_error", "Error calling method " + procedureURI, ex.getMessage());
                }
                logger.log(Level.SEVERE, "Error calling method " + procedureURI, ex);
            }
        }
    }
    
    
    public void sendCallResults()
    {
        if (isCancelled()) {
            WampProtocol.sendCallError(clientSocket, callID, WampException.ERROR_PREFIX + ".CanceledByCaller", "RPC cancelled by caller: " + callID, null);
        } else {
            WampProtocol.sendCallResult(clientSocket, callID, getResult(), getResultKw());
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
