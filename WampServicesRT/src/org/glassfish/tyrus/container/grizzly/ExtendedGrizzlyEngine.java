package org.glassfish.tyrus.container.grizzly;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.tyrus.server.TyrusEndpoint;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeListener;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.spi.TyrusClientSocket;
import org.glassfish.tyrus.spi.TyrusContainer;
import org.glassfish.tyrus.spi.TyrusServer;
import org.glassfish.tyrus.websockets.WebSocketEngine;


public class ExtendedGrizzlyEngine implements TyrusContainer
{
    private boolean secure;
    private WebSocketEngine engine;
    private Properties serverProperties;
    
    public ExtendedGrizzlyEngine(boolean secure, Properties serverProperties) 
    { 
        this.secure = secure;
        this.serverProperties = serverProperties;
        engine = WebSocketEngine.getEngine();
    }

    @Override
    public TyrusServer createServer(String rootPath, int port) {
        final HttpServer server = HttpServer.createSimpleServer(rootPath, port);
        server.getListener("grizzly").registerAddOn(new WebSocketAddOn());
        if(secure) {
            int sslPort = Integer.parseInt(serverProperties.getProperty("ssl-port", "8181"));
            
            SSLContextConfigurator sslContextConfig = new SSLContextConfigurator(); 
            sslContextConfig.createSSLContext();
            sslContextConfig.setKeyStoreFile(serverProperties.getProperty("ssl-key-store", "keystore.jks"));
            sslContextConfig.setTrustStoreFile(serverProperties.getProperty("ssl-trust-store", "cacerts.jks"));
            sslContextConfig.setKeyStorePass(serverProperties.getProperty("ssl-key-store-password", "changeit"));
            sslContextConfig.setTrustStorePass(serverProperties.getProperty("ssl-trust-store-password", "changeit"));
            sslContextConfig.setKeyManagerFactoryAlgorithm("SunX509");
                          
            NetworkListener networkListener = new NetworkListener("ssl", server.getListener("grizzly").getHost(), sslPort);
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

    @Override
    public TyrusClientSocket openClientSocket(String string, ClientEndpointConfig cec, SPIEndpoint spie, SPIHandshakeListener sl, Map<String, Object> map) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
