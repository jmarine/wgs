/**
 * Standalone Java WAMP implementation.
  *
 * @author Jordi Mariné Fort
 */

package org.wgs.util;

import java.io.FileReader;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javax.jms.TopicConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;

import javax.websocket.server.ServerEndpointConfig;

import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.TyrusContainer;
import org.glassfish.tyrus.spi.TyrusServer;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampEndpoint;


public class Server 
{
    public static void main(String[] args) throws Exception 
    {
        String configFileName = "wgs.properties";
        if(args.length > 0) configFileName = args[0];

        FileReader configReader = null;
        Properties serverConfig = new Properties();
        try {
            configReader = new FileReader(configFileName);
            serverConfig.load(configReader);
        } finally {
            if(configReader != null) {
                configReader.close(); 
                configReader=null;
            }            
        }

        // create a Grizzly HttpServer to server static resources from 'webapp', on PORT.
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, serverConfig.getProperty("java.naming.factory.initial", "org.apache.naming.java.javaURLContextFactory"));
        System.setProperty(Context.URL_PKG_PREFIXES, serverConfig.getProperty("java.naming.factory.url.pkgs", "org.apache.naming"));
        
        InitialContext ctx = new InitialContext();
        ctx.createSubcontext("jdbc");
        ctx.createSubcontext("concurrent");
        ctx.createSubcontext("jms");
        
        ExecutorService execService = Executors.newCachedThreadPool();
        ctx.bind("concurrent/WampRpcExecutorService", execService);

        System.out.println("Starting OpenMQ broker...");
        TopicConnectionFactory cf = MessageBroker.startEmbeddedBroker(serverConfig);
        ctx.bind("jms/TopicConnectionFactory", cf);        
        
        
        //System.setProperty("webocket.servercontainer.classname", "org.glassfish.tyrus.TyrusContainerProvider");
        //ServerContainer websocketServerContainer = ContainerProvider.getServerContainer();
        //websocketServerContainer.publishServer(WampEndpoint.class);
        
        TyrusServerContainer server = null;        
        try {
            String docroot = serverConfig.getProperty("docroot");
            
            TyrusContainer tyrusContainer = new org.glassfish.tyrus.container.grizzly.WssGrizzlyEngine(serverConfig);
            TyrusServer tyrusServer = tyrusContainer.createServer(docroot, Integer.parseInt(serverConfig.getProperty("ws-port")));

            //TyrusServerConfiguration tyrusServerConfig = new TyrusServerConfiguration();
            Set<ServerEndpointConfig> dynamicallyAddedEndpointConfigs = new HashSet<ServerEndpointConfig>();
            
            String databases = serverConfig.getProperty("databases");
            if(databases != null) {
                StringTokenizer tokenizer = new StringTokenizer(databases, ",");
                while(tokenizer.hasMoreTokens()) {
                    String db = tokenizer.nextToken();
                    String jndi = serverConfig.getProperty("database." + db + ".jndi");
                    String url = serverConfig.getProperty("database." + db + ".url");
                    EmbeddedConnectionPoolDataSource40 ds = new EmbeddedConnectionPoolDataSource40();
                    ds.setDatabaseName(url);
                    ctx.bind(jndi, ds);
                } 
            }

            String contexts = serverConfig.getProperty("contexts");
            if(contexts != null) {
                StringTokenizer tkContexts = new StringTokenizer(contexts, ",");
                while(tkContexts.hasMoreTokens()) {
                    String context = tkContexts.nextToken();
                    String uri = serverConfig.getProperty("context." + context + ".uri");

                    System.out.println("Creating WAMP context URI: " + uri);
                    
                    int wampVersion = Integer.parseInt(serverConfig.getProperty("context." + context + ".wampVersion", "1"));
                    WampApplication wampApplication = new WampApplication(wampVersion, WampEndpoint.class, uri);
                    //tyrusServerConfig = tyrusServerConfig.endpoint(WampEndpoint.class, uri);
                    //server.publishServer(WampEndpoint.class  /* , uri */ );
                    
                    String topics = serverConfig.getProperty("context." + context + ".topics");
                    if(topics != null) {
                        StringTokenizer tkTopics = new StringTokenizer(topics, ",");
                        while(tkTopics.hasMoreTokens()) {
                            String topic = tkTopics.nextToken();
                            System.out.println("> Creating topic at "+uri+": " + topic);
                            wampApplication.createTopic(topic, null);
                        }
                    }                    
                    
                    String modules = serverConfig.getProperty("context." + context + ".modules");
                    if(modules != null) {
                        StringTokenizer tkModules = new StringTokenizer(modules, ",");
                        while(tkModules.hasMoreTokens()) {
                            String cls = tkModules.nextToken();
                            System.out.println("> Registering module at "+uri+": " + cls);
                            wampApplication.registerWampModule(Class.forName(cls));
                        }
                    }
                    
                    // register the application
                    // server.deploy(wampApplication.getEndpointClass().newInstance(), wampApplication);
                    dynamicallyAddedEndpointConfigs.add(wampApplication);
                }
            }
            
            Set<Class<?>> emptyClassSet = new HashSet<Class<?>>();
            server = new TyrusServerContainer(tyrusServer, "", emptyClassSet, emptyClassSet, dynamicallyAddedEndpointConfigs);
            //server.publishServer(WampApplication.class);
            server.start();
            
            System.out.println("Press any key to stop the server...");
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
            
        } finally {

            try {
                MessageBroker.stopEmbeddedBroker();
            } catch (Exception ex) {
                System.err.println("OpenMQ broker shutdown error: " + ex.getMessage());
                ex.printStackTrace();
            }
            
            if(server != null) {
                try { 
                    server.stop();
                } catch (Exception ex) {
                    System.err.println("WebSocket ServerContainer shutdown error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            
            // stop embedded derby database
            if(ctx.list("jdbc").hasMore()) {
                try { 
                    DriverManager.getConnection("jdbc:derby:;shutdown=true");
                } catch (SQLException se) {
                    if (( (se.getErrorCode() == 50000) && ("XJ015".equals(se.getSQLState()) ))) {
                        System.out.println("Derby has been shut down normally");
                    } else {
                        System.err.println("Derby did not shut down normally (error code = " + se.getErrorCode() +", SQL state = " + se.getSQLState() + "): " + se.getMessage());
                        se.printStackTrace();
                    }
                }
            }
            
        }
    }
    
    
}
