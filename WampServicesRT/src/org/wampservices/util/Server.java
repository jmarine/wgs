/**
 * Standalone Java WAMP implementation.
  *
 * @author Jordi Mariné Fort
 */

package org.wampservices.util;

import java.io.FileReader;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import javax.naming.Context;
import javax.naming.InitialContext;

import javax.websocket.server.ServerEndpointConfig;

import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.TyrusContainer;
import org.glassfish.tyrus.spi.TyrusServer;
import org.wampservices.WampApplication;


public class Server 
{
    public static void main(String[] args) throws Exception 
    {
        // create a Grizzly HttpServer to server static resources from 'webapp', on PORT.
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
        System.setProperty(Context.URL_PKG_PREFIXES, "org.apache.naming");                    
        InitialContext ctx = new InitialContext();
        ctx.createSubcontext("jdbc");
       
        
        String configFileName = "wampservices.properties";
        if(args.length > 0) configFileName = args[0];
        

        FileReader configReader = null;
        Properties wampConfig = new Properties();
        try {
            configReader = new FileReader(configFileName);
            wampConfig.load(configReader);
        } finally {
            if(configReader != null) {
                configReader.close(); 
                configReader=null;
            }            
        }
        
        //System.setProperty("webocket.servercontainer.classname", "org.glassfish.tyrus.TyrusContainerProvider");
        //ServerContainer websocketServerContainer = ContainerProvider.getServerContainer();
        //websocketServerContainer.publishServer(WampEndpoint.class);
        
        TyrusServerContainer server = null;        
        try {
            String docroot = wampConfig.getProperty("docroot");
            
            TyrusContainer tyrusContainer = new org.glassfish.tyrus.container.grizzly.GrizzlyEngine();
            TyrusServer tyrusServer = tyrusContainer.createServer(docroot, 8080);

            //TyrusServerConfiguration serverConfig = new TyrusServerConfiguration();
            Set<ServerEndpointConfig> dynamicallyAddedEndpointConfigs = new HashSet<ServerEndpointConfig>();
            
            String databases = wampConfig.getProperty("databases");
            if(databases != null) {
                StringTokenizer tokenizer = new StringTokenizer(databases, ",");
                while(tokenizer.hasMoreTokens()) {
                    String db = tokenizer.nextToken();
                    String jndi = wampConfig.getProperty("database." + db + ".jndi");
                    String url = wampConfig.getProperty("database." + db + ".url");
                    EmbeddedConnectionPoolDataSource40 ds = new EmbeddedConnectionPoolDataSource40();
                    ds.setDatabaseName(url);
                    ctx.bind(jndi, ds);
                } 
            }

            String contexts = wampConfig.getProperty("contexts");
            if(contexts != null) {
                StringTokenizer tkContexts = new StringTokenizer(contexts, ",");
                while(tkContexts.hasMoreTokens()) {
                    String context = tkContexts.nextToken();
                    String uri = wampConfig.getProperty("context." + context + ".uri");

                    boolean topicWildcardsEnabled = false;
                    String enableWildcards = wampConfig.getProperty("context." + context + ".enableTopicWildcards");
                    if((enableWildcards != null) && (enableWildcards.toUpperCase().equals("TRUE"))) topicWildcardsEnabled = true;
                    
                    System.out.println("Creating WAMP context URI: " + uri);
                    WampApplication wampApplication = WampApplication.getApplication(uri);
                    //serverConfig = serverConfig.endpoint(WampEndpoint.class, uri);
                    //server.publishServer(WampEndpoint.class  /* , uri */ );
                    
                    String topics = wampConfig.getProperty("context." + context + ".topics");
                    if(topics != null) {
                        StringTokenizer tkTopics = new StringTokenizer(topics, ",");
                        while(tkTopics.hasMoreTokens()) {
                            String topic = tkTopics.nextToken();
                            System.out.println("> Creating topic at "+uri+": " + topic);
                            wampApplication.createTopic(topic, null);
                        }
                    }                    
                    
                    String modules = wampConfig.getProperty("context." + context + ".modules");
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
