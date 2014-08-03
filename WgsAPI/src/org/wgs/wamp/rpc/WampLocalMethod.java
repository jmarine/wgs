package org.wgs.wamp.rpc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import org.jdeferred.Deferred;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.ProgressCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampException;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampResult;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;



public class WampLocalMethod extends WampMethod
{
    private Method method;
    private WampModule module;
    private WampApplication app;
    
    
    public WampLocalMethod(String uri, WampModule module, Method method)
    {
        super(uri);
        this.method = method;
        this.module = module;
        this.app = module.getWampApplication();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Promise<WampResult, WampException, WampResult> invoke(final WampCallController task, WampSocket clientSocket, WampList args, WampDict argsKw, WampCallOptions options) throws Exception
    {
        int argCount = 0;
        
        final DeferredObject<WampResult,WampException,WampResult> deferred = new DeferredObject<WampResult,WampException,WampResult>();
        task.setRemoteInvocationsCompletionCallback(deferred);
        
        ArrayList<Object> params = new ArrayList<Object>();
        for(Class paramType : method.getParameterTypes()) {
            if(paramType.isInstance(clientSocket)) {  // WampSocket parameter info
                params.add(clientSocket);
            } else if(paramType.isInstance(app)) {    // WampApplication parameter info
                params.add(app);
            } else if(WampCallController.class.isAssignableFrom(paramType)) {
                params.add(task);                    
            } else if(WampCallOptions.class.isAssignableFrom(paramType)) {
                params.add(options);
            } else if(WampList.class.isAssignableFrom(paramType)) {
                Object list = args.subList(argCount, args.size());
                params.add(list);    // Only a list with the rest of the received arguments
                argCount = args.size();                    
            } else if(WampDict.class.isAssignableFrom(paramType)) {
                Object nextParam = (argCount < args.size())? args.get(argCount) : null;
                if(nextParam != null && paramType.isInstance(nextParam)) {
                    params.add(nextParam);
                    argCount++;
                } else {
                    params.add(argsKw);  
                }
            } else if(paramType.isEnum()) {
                String text = (String)args.get(argCount++);
                if(text == null) params.add(null);
                else params.add(Enum.valueOf(paramType, text));
            } else {
                Object val = args.get(argCount++);
                params.add(val);
            }
        }

        
        try {
            Object result = method.invoke(this.module, params.toArray());
            if(result == null || !(result instanceof Promise)) {
                WampResult wampResult = wrapToWampResult(task.getCallID(), result);
                deferred.resolve(wampResult);
            } else {
                // adapt generic Promise to Promise<WampResult,WampException,WampResult>:
                
                Promise plainPromise = (Promise)result;
                plainPromise.done(new DoneCallback() {
                    @Override
                    public void onDone(Object r) {
                        WampResult wampResult = wrapToWampResult(task.getCallID(), r);
                        deferred.resolve(wampResult);
                    }
                });
                
                plainPromise.progress(new ProgressCallback() {
                    @Override
                    public void onProgress(Object p) {
                        WampDict details = new WampDict();
                        details.put("progress", true);
                        WampResult wampProgress = wrapToWampResult(task.getCallID(), p);
                        wampProgress.setDetails(details);
                        deferred.notify(wampProgress);
                    }
                });                
                
                plainPromise.fail(new FailCallback() {
                    @Override
                    public void onFail(Object f) {
                        deferred.reject(wrapToWampException(task.getCallID(), f));
                    }
                });

            }
            
        } catch(Exception ex) {
            deferred.reject(wrapToWampException(task.getCallID(), ex));
        }
        
        return deferred.promise();
    }
    
    private WampException wrapToWampException(Long callId, Object ex) 
    {
        if(ex != null && ex instanceof Throwable) {
            return new WampException(callId, null, "wamp.error.local_invocation", null, null);
        } else {
            return new WampException(callId, null, "wamp.error.local_invocation", new WampList(ex), null);
        }
    }
    
    
    private WampResult wrapToWampResult(Long callId, Object result) 
    {
        if(result != null && result instanceof WampResult) {
            return (WampResult)result;
        } else {
            WampResult wampResult = new WampResult(callId);
            if(result instanceof WampDict) {
                wampResult.setArgsKw((WampDict)result);
            } else if(result instanceof WampList) {
                wampResult.setArgs((WampList)result);
            } else {
                wampResult.setArgs(new WampList(result));
            }
            return wampResult;
        }
        
    }
    
}
