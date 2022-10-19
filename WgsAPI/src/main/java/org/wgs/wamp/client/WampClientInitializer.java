package org.wgs.wamp.client;

import java.util.HashMap;
import java.util.Set;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HandlesTypes;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampModule;
import org.wgs.wamp.annotation.WampRouterConfig;


@SuppressWarnings("unchecked")
@HandlesTypes({WampModule.class})
public class WampClientInitializer implements ServletContainerInitializer 
{
    public static HashMap<String,WampClient> clients = new HashMap<String,WampClient>();
    public static HashMap<String,WampRouterConfig> configs = new HashMap<String,WampRouterConfig>();

    @Override
    public void onStartup(Set<Class<?>> set, ServletContext sc) throws ServletException {
        for(Class cls : set) {
            WampRouterConfig config = (WampRouterConfig)cls.getAnnotation(WampRouterConfig.class);
            if(config != null) {
                System.out.println("Detected WampRouterConfig in module: " + cls.getName());
                String key = config.url() + "/" + config.user() + "@" + config.realm();
                configs.put(key, config);
                
                try {
                    WampClient client = clients.get(key);
                    if(client == null) client = new WampClient(config.url());
                    client.getWampApplication().registerWampModule( (WampModule)cls.getConstructor(WampApplication.class).newInstance(client.getWampApplication()) );
                    clients.put(key, client);

                } catch(Exception ex) { 
                    System.err.println("WampClientInitializer: Error in registration: " + ex.getMessage());
                }
                    
            }
        }
        
        for(String key : clients.keySet()) {
            WampClient client = clients.get(key);
            WampRouterConfig config = configs.get(key);

            try {
                client.connect();
                client.hello(config.realm(), config.user(), config.password(), config.digestPasswordMD5());
                System.out.println("Started WAMP client with " + config.url());
            } catch(Exception ex)  {
                System.err.println("WampClientInitializer: Error in connection: " + ex.getClass() + ": " + ex.getMessage());
                ex.printStackTrace();
            }

        }
    }

}
