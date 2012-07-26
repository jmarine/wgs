WAMP services provider for Java
===============================

### About ###

The projects provides [WAMP](http://wamp.ws/spec) services, and enables the development of new RPCs in Java language.

The are the following projects:
* WampServicesRT: This is a server-side library, and also includes a standalone server application (based on [Grizzly](http://grizzly.java.net)).
* WampServicesWAR: Resources for WEB examples, and it is also a JavaEE web application example for the integration with GlassFish 3.1.2+ application server (remember to enable websockets support with the command "asadmin set configs.config.server-config.network-config.protocols.protocol.http-listener-1.http.websockets-support-enabled=true")

