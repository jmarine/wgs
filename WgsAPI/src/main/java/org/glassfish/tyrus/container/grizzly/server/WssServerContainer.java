package org.glassfish.tyrus.container.grizzly.server;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.Servlet;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.tyrus.container.grizzly.server.GrizzlyServerContainer;
import org.glassfish.tyrus.container.grizzly.server.WebSocketAddOn;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.ServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;



public class WssServerContainer extends GrizzlyServerContainer
{
    private Properties serverProperties;
    private HttpServer server;

    
    public WssServerContainer(Properties serverProperties) 
    { 
        this.server = new HttpServer();
        this.serverProperties = serverProperties;
    }
    
    public void addServlet(String contextPath, String servletMapping, Class<? extends Servlet> servletClass)
    {
        WebappContext context = new WebappContext("WebappContext", contextPath);
        ServletRegistration registration = context.addServlet("ServletContainer", servletClass);
        registration.addMapping(servletMapping);
        context.deploy(server);               
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

        return new TyrusServerContainer((Set<Class<?>>) null) {

            private final WebSocketEngine engine = TyrusWebSocketEngine.builder(this).incomingBufferSize(incommingBufferSize).build();

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
                
                if (rootPath != null) {
                    ServerConfiguration config = server.getServerConfiguration();
                    config.addHttpHandler(new CustomHttpHandler(rootPath), "/");
                }
                
                String hostIP = System.getenv("OPENSHIFT_DIY_IP");
                if(hostIP == null) hostIP = NetworkListener.DEFAULT_NETWORK_HOST;
                
                NetworkListener listener = new NetworkListener("grizzly", hostIP, new PortRange(port));
                server.addListener(listener);
                
                server.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);  // forever
                server.getListener("grizzly").registerAddOn(new WebSocketAddOn(this, "/"));
                
                String wssPort = serverProperties.getProperty("wss-port");
                if(wssPort != null) {
                    SSLContextConfigurator sslContextConfig = new SSLContextConfigurator(); 
                    sslContextConfig.createSSLContext(true);
                    sslContextConfig.setKeyStoreFile(serverProperties.getProperty("wss-key-store", "keystore.jks"));
                    sslContextConfig.setTrustStoreFile(serverProperties.getProperty("wss-trust-store", "cacerts.jks"));
                    sslContextConfig.setKeyStorePass(serverProperties.getProperty("wss-key-store-password", "changeit"));
                    sslContextConfig.setTrustStorePass(serverProperties.getProperty("wss-trust-store-password", "changeit"));
                    sslContextConfig.setKeyManagerFactoryAlgorithm("PKIX");

                    NetworkListener networkListener = new NetworkListener("wss", hostIP, Integer.parseInt(wssPort));
                    networkListener.setSecure(true); 
                    networkListener.setSSLEngineConfig(new SSLEngineConfigurator(sslContextConfig, false, false, false));
                    networkListener.getKeepAlive().setIdleTimeoutInSeconds(-1);  // forever
                    networkListener.registerAddOn(new WebSocketAddOn(this, "/"));
                    //networkListener.registerAddOn(new Http2AddOn());   // wait until Java 9 (ALPN support required)

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


class CustomHttpHandler extends StaticHttpHandler
{
    private static final Logger LOGGER = Logger.getLogger(CustomHttpHandler.class.toString());

    public CustomHttpHandler(String docRoot) 
    {
        super(docRoot);
        setFileCacheEnabled(false);
    }
    
    protected boolean handle(String uri, Request request, Response response) throws Exception 
    {
        boolean found = false;

        final File[] fileFolders = docRoots.getArray();
        if (fileFolders == null) {
            return false;
        }

        File resource = null;

        for (int i = 0; i < fileFolders.length; i++) {
            final File webDir = fileFolders[i];
            // local file
            resource = new File(webDir, uri);
            final boolean exists = resource.exists();
            final boolean isDirectory = resource.isDirectory();

            if (exists && isDirectory) {
                final File f = new File(resource, "/index.html");
                if (f.exists()) {
                    resource = f;
                    found = true;
                    break;
                }
            }

            if (isDirectory || !exists) {
                found = false;
            } else {
                found = true;
                break;
            }
        }

        if (!found) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File not found {0}", resource);
            }
            return false;
        }

        assert resource != null;
        
        /* If it's not HTTP GET - return method is not supported status
        if (!Method.GET.equals(request.getMethod())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File found {0}, but HTTP method {1} is not allowed",
                        new Object[] {resource, request.getMethod()});
            }
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
            response.setHeader(Header.Allow, "GET");
            return true;
        }
        */
        
        pickupContentType(response, resource.getPath());
        
        addToFileCache(request, response, resource);
        StaticHttpHandler.sendFile(response, resource);

        return true;
    }    
}