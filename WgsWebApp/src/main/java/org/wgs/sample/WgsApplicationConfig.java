package org.wgs.sample;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpointConfig;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.transport.http.websocket.WampEndpoint;
import org.wgs.wamp.transport.http.websocket.WampEndpointConfig;


public class WgsApplicationConfig implements ServerApplicationConfig 
{
  @Override
  public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> set) 
  {
    return new HashSet<ServerEndpointConfig>() {
      {
        add(new WampEndpointConfig(WgsEndpoint.class, WampApplication.getInstance(WampApplication.WAMPv2, "/wgs")));
      }
    };
  }

  @Override
  public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> set) 
  {
    return Collections.emptySet();
  }
  

  public static class WgsEndpoint extends WampEndpoint
  {
        @Override
        public void onApplicationStart(WampApplication app) { 
            super.onApplicationStart(app);
            app.registerWampModule(new org.wgs.service.game.Module(app)); 
        }
    }

}
    