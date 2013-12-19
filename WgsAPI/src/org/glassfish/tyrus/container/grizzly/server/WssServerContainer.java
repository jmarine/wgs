package org.glassfish.tyrus.container.grizzly.server;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.ServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.wgs.wamp.WampApplication;



public class WssServerContainer extends GrizzlyServerContainer
{
    private Properties serverProperties;
    
    public WssServerContainer(Properties serverProperties) 
    { 
        this.serverProperties = serverProperties;
    }
    
    
    @Override
    public ServerContainer createContainer(final Map<String, Object> properties) {

        final Object o = (properties == null ? null : properties.get(TyrusWebSocketEngine.INCOMING_BUFFER_SIZE));

        final Integer incommingBufferSize;
        if (o instanceof Integer) {
            incommingBufferSize = (Integer) o;
        } else {
            incommingBufferSize = null;
        }

        // TODO
        return new TyrusServerContainer((Set<Class<?>>) null) {

            private final WebSocketEngine engine = new TyrusWebSocketEngine(this, incommingBufferSize);

            private HttpServer server;
            private String contextPath = "";

            @Override
            public void register(Class<?> endpointClass) throws DeploymentException {
                engine.register(endpointClass, contextPath);
            }

            @Override
            public void register(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
                engine.register(serverEndpointConfig, contextPath);
            }
            
            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }
            
            @Override
            public void start(String rootPath, int port) throws IOException, DeploymentException {

                server = HttpServer.createSimpleServer(rootPath, port);
                server.getListener("grizzly").registerAddOn(new WebSocketAddOn(this));

                String wssPort = serverProperties.getProperty("wss-port");
                if(wssPort != null) {
                    SSLContextConfigurator sslContextConfig = new SSLContextConfigurator(); 
                    sslContextConfig.createSSLContext();
                    sslContextConfig.setKeyStoreFile(serverProperties.getProperty("wss-key-store", "keystore.jks"));
                    sslContextConfig.setTrustStoreFile(serverProperties.getProperty("wss-trust-store", "cacerts.jks"));
                    sslContextConfig.setKeyStorePass(serverProperties.getProperty("wss-key-store-password", "changeit"));
                    sslContextConfig.setTrustStorePass(serverProperties.getProperty("wss-trust-store-password", "changeit"));
                    sslContextConfig.setKeyManagerFactoryAlgorithm("SunX509");

                    NetworkListener networkListener = new NetworkListener("wss", server.getListener("grizzly").getHost(), Integer.parseInt(wssPort));
                    networkListener.setSecure(true); 
                    networkListener.setSSLEngineConfig(new SSLEngineConfigurator(sslContextConfig, false, false, false));
                    networkListener.registerAddOn(new WebSocketAddOn(this));

                    server.addListener(networkListener);
                }
                server.start();
            }
        
            @Override
            public void stop() {
                if(server != null) {
                    server.shutdownNow();
                }
                super.stop();                
            }
        };
    }
}
