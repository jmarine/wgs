package org.wgs.service.game;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 *
 * @author jordi
 */
public class RuleEngine 
{
    
    private static ConcurrentLinkedQueue<ScriptEngine> ruleEngines = new ConcurrentLinkedQueue<>();
    
    
    public static ScriptEngine getRuleEngine(Collection<Application> apps) throws Exception
    {
        ScriptEngine ruleEngine = ruleEngines.poll();
        if(ruleEngine == null) {
            ScriptEngineManager factory = new ScriptEngineManager();
            ruleEngine = factory.getEngineByName("JavaScript");  // Graal.js

            ClassLoader cl = RuleEngine.class.getClassLoader();
            ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/move.js"),StandardCharsets.UTF_8));
            ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/game.js"),StandardCharsets.UTF_8));
            //ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/botifarra.js"),StandardCharsets.UTF_8));

            ArrayList<Application> appsToLoad = new ArrayList<Application>();
            appsToLoad.addAll(apps);                
            Collections.sort(appsToLoad); 
            while(!appsToLoad.isEmpty()) {  // Note: inherited classes may fail until super class has been loaded
                Iterator<Application> iter = appsToLoad.iterator();
                while(iter.hasNext()) {
                    try { 
                        Application app = iter.next();
                        String appName = app.getName();
                        int pos = appName.indexOf("-");
                        if(pos != -1) {
                            appName = appName.substring(0, pos);
                        }
                        ruleEngine.eval(new InputStreamReader(cl.getResourceAsStream("META-INF/rules/" + appName +".js"),StandardCharsets.UTF_8)); 
                    } catch(Exception ex) { 
                        // skip exception
                    } finally {
                        iter.remove();
                    }
                    
                }
            }        
        }

        return ruleEngine;
    }  
    
    
    public static void recycleRuleEngine(ScriptEngine ruleEngine)
    {
        if(ruleEngine != null) ruleEngines.offer(ruleEngine);
    }
    
    
}
