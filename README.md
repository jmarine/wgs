WAMP services provider for Java
===============================

### About ###

This repository provides [WAMP](http://wamp.ws/spec) services, and enables the development of new RPCs in Java language.

The code is divided in two projects (compatible with [NetBeans 7.2](http://www.netbeans.org)):
* WampServicesRT: This is a server-side library. It also includes the WampServler class that implements a standalone web server that support websockets communications (based on [Grizzly](http://grizzly.java.net)).
* WampServicesWAR: This is a JavaEE web application with examples that can be integrated with GlassFish 3.1.2+ application server (remember to enable websockets support with the command "asadmin set configs.config.server-config.network-config.protocols.protocol.http-listener-1.http.websockets-support-enabled=true")

### Development guide ###

##### RPC development: #####

It is very easy to add new functions that can be called by WAMP clients:

1) Create a new Java class that extends WampModule. The class must have a constructor with 1 parameter of type [WampApplication](#wampapplication-methods).

2) Overrides the abstract method "getBaseURL" to specify the procURI or the base URI of the procedures that will be implemented (including the "#" separator).

3) Override the "onCall" method to develop the business logic of the service.

4) Finally, attach the modules to the WAMP application context (uri):
* In GlassFish 3.1.2+: specify the canonical name of the classes in the "modules" init-param of the WampServlet in web.xml configuration file (separated by ',')
* Or in WampServer: specify the canonical name of the classes in the "context.yourContextName.modules" property of the "wampservices.properties" configuration file (separated by ',')

Code example:

```java
import com.github.jmarine.wampservices.WampApplication;
import com.github.jmarine.wampservices.WampModule;
import com.github.jmarine.wampservices.WampSocket;
import org.json.JSONArray;

public class MyModule extends WampModule 
{
    private WampApplication wampApp = null;

    public MyModule(WampApplication app) {
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



##### WampApplication methods #####

The WampApplication object represents a WAMP application context (uri), and also provides the following methods to help the development of modules:

* createTopic(String topicFQname): allows to dynamically create a topic usable by WAMP clients.

* createWampErrorArg(String errorURI, String errorDesc): prepares an errorURI and errorDesc arguments for sending a CALLERROR message to the client (see "sendCallResponse" method).

* sendCallResponse(boolean valid, String callID, JSONArray args, WampSocket clientSocket): when the parameter "valid" is true, it sends a CALLRESULT to the client with the callID and results specified in the "args" parameter. Otherwise, it will send a CALLERROR to the client (see "createWampErrorArg" method). 

* publishEvent(WampSocket clientSocketFromSender, String topicName, JSONObject event, boolean excludeMe): broadcasts an EVENT message with the "event" object data to all clients subscribed in the topic (with the possibility to exclude the client socket).

* publishEvent(WampSocket clientSocketFromSender, String topicName, JSONObject event, Set<String> excluded, Set<String> eligible): broadcast and EVENT message with the "event" object data to the "eligible" list of clients (sessionIds), with the exception of the clients in the "excluded" list (sessionIds).

