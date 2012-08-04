WAMP services provider for Java
===============================

Status: alpha


About
-----

This repository provides [WAMP](http://wamp.ws/spec) services, and enables the development of new RPCs in Java language.

The code is divided in two projects (compatible with [NetBeans 7.2](http://www.netbeans.org)):
* WampServicesRT: This is a server-side library. It also includes the WampServler class that implements a standalone web server with support of websockets communications (based on [Grizzly](http://grizzly.java.net)).
* WampServicesWAR: This is a JavaEE web application with examples that can be integrated with GlassFish 3.1.2+ application server (remember to enable websockets support with the command "asadmin set configs.config.server-config.network-config.protocols.protocol.http-listener-1.http.websockets-support-enabled=true")


Development guide
-----------------

### RPC development: ###

It is very easy to add new functions that can be called by WAMP clients:

1) Create a new Java class that extends WampModule. The class must have a constructor with 1 parameter of type [WampApplication](#wampapplication-methods).

2) Overrides the abstract method "getBaseURL" to specify the procURI or the base URI of the procedures that will be implemented (including the "#" separator).

3) To develop new RPCs, define the methods as "public" and with the "WampRPC" annotation (you can use the "name" parameter to change the Java method name for WAMP clients). You can also override the "onCall" method to intercept the RPCs, and add new business logic. 

4) Finally, attach the modules to the WAMP application context (uri):
* In GlassFish 3.1.2+: specify the canonical name of the classes in the "modules" init-param of the WampServlet in web.xml configuration file (separated by ',')
* Or in WampServer: specify the canonical name of the classes in the "context.yourContextName.modules" property of the "wampservices.properties" configuration file (separated by ',')

Code example:

```java
package org.acme.myapp;

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

    @WampRPC(name="sum")
    public int sumArguments( /* WampSocket socket, */ JSONArray args) throws Exception {
        int sum = 0;
        for(int i = 0; i < args.length(); i++) {
           sum += args.getInt(i);
        }
        return sum;
    }

    // NOTE: the method is not public neighter annotated with "WampRPC", 
    // but "onCall" method can intercept the RPC and invoke it.
    private int multiplyArguments( /* WampSocket socket, */ JSONArray args) throws Exception {
        int retval = args.getInt(0);
        for(int i = 1; i < args.length(); i++) {
           retval *= args.getInt(i);
        }
        return retval;
    }

    
    @Override
    public Object onCall(WampSocket socket, String method, JSONArray args) throws Exception {
        if(method.equals("multiply")) return multiplyArguments(arg);
        else return super.onCall(socket, method, args);  // to invoke the methods annotated with "WampRPC"
    }

    ...
}
```

Configuration example:

```html
docroot=../wwwroot

contexts=wamp1
context.wamp1.uri=/wamp1
context.wamp1.modules=org.acme.myapp.MyModule
context.wamp1.topics=https://github.com/jmarine/wampservices#topic1,https://github.com/jmarine/wampservices#topic2

```



### Helper classes: ###

##### WampModule methods #####

This is an abstract class that provides interceptor methods for WAMP events:

* **getBaseURL()**: it must be overriden to return the base URI of the RPCs / topic events to intercept.

* **onConnect(WebSocket client)**: called when a client is connected to the application (URI).

* **onDisconnect(WebSocket client)**: called when a client is disconnected from the application (URI).

* **onCall(WebSocket client, String method, JSONArray args)**: it can be overriden to add new RPCs.

* **onSubscribe(WampSocket client, WampTopic topic)**: it can be overriden to intercept subscription requests to the topics. In this case, remember to call the superclass method before/after your business logic.
(i.e: to send an EVENT to the client, the method should be called before the publication).

* **onUnsubscribe(WampSocket client, WampTopic topic)**: it can be overriden to intercept unsubscription requests to the topics. Also, rembember to cal the superclass method before/after your business logic.

* **onPublish(WampSocket sourceClient, WampTopic topic, JSONObject event, Set<String> excluded, Set<String> eligible)**: it can be overriden to intercept event publications. Remember to call the superclass method before/after your business logic.



##### WampApplication methods #####

It represents a WAMP application context (URI), and also provides the following methods to help the development of modules:

* **createTopic(String topicFQname)**: dinamically creates a new topic to be used by WAMP clients.

* **getTopic(String topicFQname)**: gets a WampTopic by its fully qualified name.



##### WampSocket methods #####

It represents a connection with a WAMP client, and provides the following methods:

* **normalizeURI(String curie)**: converts a CURIE to a fully qualified URI (using PREFIXES registered by the client).

* **publishEvent(WampTopic topic, JSONObject event, boolean excludeMe)**: broadcasts an EVENT message with the "event" object data to all clients subscribed in the topic (with the possibility to exclude the publisher).

* **publishEvent(WampTopic topic, JSONObject event, Set<String> excluded, Set<String> eligible)**: broadcast and EVENT message with the "event" object data to the "eligible" list of clients (sessionIds), with the exception of the clients in the "excluded" list (sessionIds).



##### WampTopic methods ######

It represents a topic for PubSub services, and provides the following methods:

* **getURI()**: gets its topicURI.

* **getSocketIds()**: gets a list of sessionId of clients subscribed to the topic.

* **getSocketId(String sessionId)**: gets the WebSocket of the client with sessionId in case it is subscribed to the topic.



##### WampException methods ######

It allows to propagate error information to clients (to generate CALLERROR messages from RPCs).

* **WampException(errorURI, errorDesc)**: Constructor with errorURI and errorDesc information.

* **WampException(errorURI, errorDesc, errorDetails)**: Constructor with errorURI, errorDesc and errorDetails information.

