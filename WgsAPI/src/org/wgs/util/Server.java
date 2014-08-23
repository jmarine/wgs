/**
 * Standalone Java WAMP implementation.
  *
 * @author Jordi Mariné Fort
 */

package org.wgs.util;

import org.wgs.wamp.topic.JmsServices;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40;
import org.glassfish.tyrus.container.grizzly.server.WssServerContainer;
import org.glassfish.tyrus.server.TyrusServerContainer;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.topic.WampBroker;
import org.wgs.wamp.transport.http.websocket.WampEndpoint;
import org.wgs.wamp.transport.http.websocket.WampEndpointConfig;


public class Server 
{
    private static ExecutorService execService;
    private static ScheduledExecutorService scheduledExecService;
    private static TyrusServerContainer tyrusServerContainer;
    
    
    private static Properties getServerConfig(String[] args) throws IOException, FileNotFoundException
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
        return serverConfig;
    }
    
    
    private static void setupDataSources(InitialContext ctx, Properties serverConfig) throws NamingException
    {
        String databases = serverConfig.getProperty("databases");
        if(databases != null) {
            StringTokenizer tokenizer = new StringTokenizer(databases, ",");
            while(tokenizer.hasMoreTokens()) {
                String db = tokenizer.nextToken();
                String jndi = serverConfig.getProperty("database." + db + ".jndi");
                String host = serverConfig.getProperty("database." + db + ".host");
                if(host == null) {
                    EmbeddedConnectionPoolDataSource40 ds = new EmbeddedConnectionPoolDataSource40();
                    ds.setDatabaseName(serverConfig.getProperty("database." + db + ".path"));
                    ds.setCreateDatabase("create");
                    ctx.bind(jndi, ds);
                } else {
                    String password = serverConfig.getProperty("database." + db + ".password");
                    org.apache.derby.jdbc.ClientConnectionPoolDataSource40 ds = new org.apache.derby.jdbc.ClientConnectionPoolDataSource40();
                    ds.setServerName(host);
                    ds.setPortNumber(Integer.parseInt(serverConfig.getProperty("database." + db + ".port")));
                    ds.setDatabaseName(db);
                    ds.setCreateDatabase("create");
                    ds.setUser(serverConfig.getProperty("database." + db + ".user"));
                    ds.setPassword(serverConfig.getProperty("database." + db + ".password"));
                    ctx.bind(jndi, ds);
                }
            } 
        }    

    }
    
    private static TyrusServerContainer setupWampContexts(Properties serverConfig) throws Exception
    {
        //System.setProperty("webocket.servercontainer.classname", "org.glassfish.tyrus.TyrusContainerProvider");
        //ServerContainer websocketServerContainer = ContainerProvider.getServerContainer();
        //websocketServerContainer.publishServer(WampEndpoint.class);
        
        String docroot = serverConfig.getProperty("docroot");

        WssServerContainer tyrusContainer = new WssServerContainer(serverConfig);
        TyrusServerContainer server = (TyrusServerContainer)tyrusContainer.createContainer(null);

        //Set<ServerEndpointConfig> dynamicallyAddedEndpointConfigs = new HashSet<ServerEndpointConfig>();

        String contexts = serverConfig.getProperty("contexts");
        if(contexts != null) {
            StringTokenizer tkContexts = new StringTokenizer(contexts, ",");
            while(tkContexts.hasMoreTokens()) {
                String context = tkContexts.nextToken();
                String uri = serverConfig.getProperty("context." + context + ".uri");

                System.out.println("Creating WAMP context URI: " + uri);

                int wampVersion = Integer.parseInt(serverConfig.getProperty("context." + context + ".wampVersion", "1"));
                WampApplication wampApplication = new WampApplication(wampVersion, uri);
                //tyrusServerConfig = tyrusServerConfig.endpoint(WampEndpoint.class, uri);
                //server.publishServer(WampEndpoint.class  /* , uri */ );

                String topics = serverConfig.getProperty("context." + context + ".topics");
                if(topics != null) {
                    StringTokenizer tkTopics = new StringTokenizer(topics, ",");
                    while(tkTopics.hasMoreTokens()) {
                        String topic = tkTopics.nextToken();
                        System.out.println("> Creating topic at "+uri+": " + topic);
                        WampBroker.createTopic(wampApplication, topic, null);
                    }
                }                    

                String modules = serverConfig.getProperty("context." + context + ".modules");
                if(modules != null) {
                    StringTokenizer tkModules = new StringTokenizer(modules, ",");
                    while(tkModules.hasMoreTokens()) {
                        String moduleClass = tkModules.nextToken();
                        System.out.println("> Registering module at "+uri+": " + moduleClass);
                        WampModule module = (WampModule)Class.forName(moduleClass).getConstructor(WampApplication.class).newInstance(wampApplication);                        
                        wampApplication.registerWampModule(module);
                    }
                }

                // register the application
                // server.deploy(new WampEndpointConfig(wampApplication, WampEndpoint.class));
                server.register(new WampEndpointConfig(WampEndpoint.class, wampApplication));
                
            }
        }

        //Set<Class<?>> emptyClassSet = new HashSet<Class<?>>();
        //TyrusServerContainer server = new TyrusServerContainer(tyrusServer, "", emptyClassSet, emptyClassSet, dynamicallyAddedEndpointConfigs);
        //server.publishServer(WampApplication.class);

        server.start(docroot, Integer.parseInt(serverConfig.getProperty("ws-port", "8080")));

        return server;
    }

    
        
    public static void main(String[] args) throws Exception 
    {
        start(getServerConfig(args));
    }
    
    
    public static void start(final Properties serverConfig) throws Exception 
    {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, serverConfig.getProperty("java.naming.factory.initial", "org.apache.naming.java.javaURLContextFactory"));
        System.setProperty(Context.URL_PKG_PREFIXES, serverConfig.getProperty("java.naming.factory.url.pkgs", "org.apache.naming"));
        
        InitialContext ctx = new InitialContext();
        ctx.createSubcontext("concurrent");
        ctx.createSubcontext("jms");
        ctx.createSubcontext("jdbc");
        ctx.createSubcontext("java:");
        ctx.createSubcontext("java:comp");
        ctx.createSubcontext("java:/comp");
        ctx.createSubcontext("java:/comp/env");
        ctx.createSubcontext("java:/comp/env/jdbc");
        
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
           @Override
           public void run() {
                System.out.println("Signal received from shutdown hook...");
                Server.stop(serverConfig);
           }
          });        
        
        
        try {
            // Configure JNDI factories:
            execService = Executors.newCachedThreadPool();
            scheduledExecService = Executors.newScheduledThreadPool(100);
            ctx.bind("concurrent/WampRpcExecutorService", execService);
            ctx.bind("java:comp/DefaultManagedExecutorService", execService);
            ctx.bind("java:comp/DefaultManagedScheduledExecutorService", scheduledExecService);
            
            JmsServices.start(serverConfig);

            setupDataSources(ctx, serverConfig);

            // Start WAMP applications:
            tyrusServerContainer = setupWampContexts(serverConfig);        

            // Wait manual termination:
            if(System.getenv("OPENSHIFT_APP_NAME") == null) {
                System.out.println("Press any key to quit server");
                Thread wait = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try { 
                            System.in.read(); 
                            synchronized(Server.class) {
                                Server.class.notify();
                            }
                        } catch(IOException ex) { }
                    }
                });
                wait.setDaemon(true);
                wait.start();
            }
            
            synchronized(Server.class) {
                Server.class.wait();  // Required OpenShift environment (it doesn't wait for key press)
            }  
            
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ":" + ex.getMessage());
            ex.printStackTrace();
            
        } finally {
            System.exit(0);  // run shutdown hooks
        }
        
    }
     
    public static void stop(Properties serverConfig) 
    {    
        System.out.println("Shutting down...");


        try {
            JmsServices.stop();
        } catch (Exception ex) {
            System.err.println("OpenMQ broker shutdown error: " + ex.getMessage());
            ex.printStackTrace();
        }

        if(execService != null) {
            try { 
                execService.shutdown();
            } catch (Exception ex) {
                System.err.println("Executor service shutdown error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        if(scheduledExecService != null) {
            try { 
                execService.shutdown();
            } catch (Exception ex) {
                System.err.println("Scheduled Executor service shutdown error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }


        if(tyrusServerContainer != null) {
            try { 
                tyrusServerContainer.stop();
            } catch (Exception ex) {
                System.err.println("WebSocket ServerContainer shutdown error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        // stop embedded derby database
        String wgsDbPath = serverConfig.getProperty("database.WgsDB.path");
        if(wgsDbPath != null) {
            try { 
                int paramsOffset = wgsDbPath.indexOf(";");
                String params = (paramsOffset != -1) ? wgsDbPath.substring(paramsOffset) : "";
                DriverManager.getConnection("jdbc:derby:" + params + ";shutdown=true");
            } catch (SQLException se) {
                if (( (se.getErrorCode() == 50000) && ("XJ015".equals(se.getSQLState()) ))) {
                    System.out.println("Derby has been shut down normally");
                } else {
                    System.err.println("Derby did not shut down normally (error code = " + se.getErrorCode() +", SQL state = " + se.getSQLState() + "): " + se.getMessage());
                    se.printStackTrace();
                }
            }
        }

        System.out.println("WGS server stopped.");

    }
    
    
}
