package org.glassfish.tyrus.container.grizzly;

import java.io.IOException;
import java.util.Properties;
import javax.websocket.DeploymentException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.tyrus.core.TyrusEndpoint;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.spi.TyrusServer;
import org.glassfish.tyrus.websockets.WebSocketEngine;


public class WssGrizzlyEngine extends GrizzlyEngine
{
    private WebSocketEngine engine;
    private Properties serverProperties;
    
    public WssGrizzlyEngine(Properties serverProperties) 
    { 
        this.engine = WebSocketEngine.getEngine();
        this.serverProperties = serverProperties;
    }

    @Override
    public TyrusServer createServer(String rootPath, int port) {
        final HttpServer server = HttpServer.createSimpleServer(rootPath, port);
        server.getListener("grizzly").registerAddOn(new WebSocketAddOn());
        
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
            networkListener.registerAddOn(new WebSocketAddOn());

            server.addListener(networkListener);
        }
        return new TyrusServer() {
            @Override
            public void start() throws IOException {
                server.start();
            }

            @Override
            public void stop() {
                server.stop();
            }

            @Override
            public SPIRegisteredEndpoint register(SPIEndpoint endpoint) throws DeploymentException {
                TyrusEndpoint ge = new TyrusEndpoint(endpoint);
                engine.register(ge);
                return ge;
            }

            @Override
            public void unregister(SPIRegisteredEndpoint ge) {
                engine.unregister((TyrusEndpoint) ge);
            }
        };

    }
    
}
