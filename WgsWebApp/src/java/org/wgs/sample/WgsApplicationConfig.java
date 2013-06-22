package org.wgs.sample;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampEndpoint;


public class WgsApplicationConfig implements ServerApplicationConfig 
{
  @Override
  public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> set) {
    return new HashSet<ServerEndpointConfig>() {
      {
        add(new WampApplication(WampApplication.WAMPv2, WgsEndpoint.class, "/wgs"));
      }
    };
  }

  @Override
  public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> set) {
    return Collections.emptySet();
  }
  

  public static class WgsEndpoint extends WampEndpoint
  {
        @Override
        public void onApplicationStart(WampApplication app) { 
            super.onApplicationStart(app);
            app.registerWampModule(org.wgs.core.Module.class); 
        }
    }

}
    