WAMP services provider for Java
===============================

### About ###

This repository provides [WAMP](http://wamp.ws/spec) services, and enables the development of new RPCs in Java language.

The code is divided in two projects (compatible with [NetBeans 7.2](http://www.netbeans.org)):
* WampServicesRT: This is a server-side library. It also includes the WampServler class that implements a standalone web server that support websockets communications (based on [Grizzly](http://grizzly.java.net)).
* WampServicesWAR: This is a JavaEE web application with examples that can be integrated with GlassFish 3.1.2+ application server (remember to enable websockets support with the command "asadmin set configs.config.server-config.network-config.protocols.protocol.http-listener-1.http.websockets-support-enabled=true")

### Development guide ###

##### RPC development: #####

It is very easy to add new functions that can be called by WAMP clients.
First, you have to create a new Java class that extends WampModule.
It must have a constructor with 1 parameter of type WampApplication (that provides methods to return CALL results/errors, and can broadcast EVENT messages), and override the abstract method "getBaseURL" to specify the procURI or the base URI of the procedures that will be implemented (including the "#" separator).

Then, override the "onCall" method to develop the business logic of the service.
For example:

```java
import com.github.jmarine.wampservices.WampApplication;
import com.github.jmarine.wampservices.WampModule;
import com.github.jmarine.wampservices.WampSocket;
import org.json.JSONArray;

public class MyModule extends WampModule 
{
    private WampApplication wampApp = null;

    public Module(WampApplication app) {
        super(app);
        this.wampApp = app;
    }     

    @Override
    public String getBaseURL() {
        return "http://mycompany.domain/myservice#";
    }
    
    @Override
    public Object onCall(WampSocket socket, String method, JSONArray args) throws Exception 
    {
        if(method.equals("sum")) return sumArguments(args);
        else if(method.equals("multiply")) return multiplyArguments(arg);
        else throw new Exception("method not implemented");
    }

    ...
}
```

Finally, attach the modules to the WAMP application context:
* WampServer: specify the canonical name of the classes in the "context.yourContextName.modules" property of the configuration file "wampservices.properties" (separated by ',')
* GlassFish: specify the canonical name of the classes in the "modules" init-param of the WampServlet (separated by ',')
