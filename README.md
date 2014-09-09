Web Game Services
=================

Version: 2.0-alpha1


About
-----

This project implements the following services:
    
* local user registration/authentication.
* authentication with Google+, Facebook, and OpenID Connect providers.
* lobby services (to create/list/join/automatch multi-player online games).
* real-time presence and communications.
* notifications (... well, this is not yet implemented).

The server-side is developed in Java language, and it can be deployed as a standalone server, or within a JavaEE 7 application server. 
The services are provided with the [WAMP v2](http://wamp.ws) specification (over websocket and long-polling transports).

The project also includes Javascript libraries for browser-based applications clients.


Demo sites
----------
* [WebGL 8x8 board games](http://wgs-jmarine.rhcloud.com/webgl8x8boardgames/).


Documentation
-------------
Visit the project's [wiki](https://github.com/jmarine/wgs/wiki) at https://github.com/jmarine/wgs/wiki

