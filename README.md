WAMP services provider for Java
===============================

Version: 0.1-alpha


About
-----

This repository provides [WAMP](http://wamp.ws/spec) services, and enables the development of new RPCs in Java language.

The code is divided in two projects (compatible with [NetBeans 7.2](http://www.netbeans.org)):
* WampServicesRT: This is a server-side library. It also includes the WampServler class that implements a standalone web server with support of websockets communications (based on [Grizzly](http://grizzly.java.net)).
* WampServicesWAR: This is a JavaEE web application with examples that can be integrated with GlassFish 3.1.2+ application server (remember to enable websockets support with the command "asadmin set configs.config.server-config.network-config.protocols.protocol.http-listener-1.http.websockets-support-enabled=true")


Extra services
--------------

* Game services (wsg): provides user registration/authentication and group joining/communications services for multi-player online games.


Development guide
-----------------

* [RPC development](https://github.com/jmarine/wampservices/wiki/RPC-development)
* [API classes](https://github.com/jmarine/wampservices/wiki/API-classes)

