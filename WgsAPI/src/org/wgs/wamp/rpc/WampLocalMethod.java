package org.wgs.wamp.rpc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.WampSocket;



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
    public Object invoke(WampCallController task, WampSocket clientSocket, WampList args, WampDict argsKw, WampCallOptions options) throws Exception
    {
        int argCount = 0;
        
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
                params.add(args.subList(argCount, args.size()));    // Only a list with the rest of the received arguments
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

        return method.invoke(this.module, params.toArray());

    }
    
}
