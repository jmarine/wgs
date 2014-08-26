package org.wgs.wamp.transport.http.longpolling;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampProtocol;



//TODO: COMPLETE LONG-POLLING SERVLET IMPLEMENTATION
//@WebServlet(urlPatterns = "/wamp2servlet", displayName = "wamp2servlet", asyncSupported = true)
public final class WampLongPollingServlet extends HttpServlet 
{
    private static String WAMP_SESSION_ID_ATTRIBUTE = "wamp2_sid";
    private static ConcurrentHashMap<String, AsyncContext> asyncContexts = new ConcurrentHashMap<String, AsyncContext>();

    
    private WampApplication app;
    
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        this.app = new WampApplication(WampApplication.WAMPv2, config.getServletContext().getContextPath());
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException 
    {
        AsyncContext actx = req.startAsync();
        actx.setTimeout(40000);
        if(req.getSession() == null) {
            req.getSession(true).setAttribute(WAMP_SESSION_ID_ATTRIBUTE, String.valueOf(WampProtocol.newGlobalScopeId()));
        }

        actx.addListener(new AsyncListener() {
            public void onStartAsync(AsyncEvent event) throws IOException {
                String sessionId = getSessionId(event);
                asyncContexts.put(sessionId, event.getAsyncContext());                            
            }

            public void onError(AsyncEvent event) throws IOException {
                String sessionId = getSessionId(event);
                asyncContexts.remove(sessionId,event.getAsyncContext());
            }

            public void onComplete(AsyncEvent event) throws IOException {
                String sessionId = getSessionId(event);
                asyncContexts.remove(sessionId,event.getAsyncContext());
            }

            public void onTimeout(AsyncEvent event) throws IOException {
                String sessionId = getSessionId(event);
                HttpServletResponse res = (HttpServletResponse)event.getAsyncContext().getResponse();

                PrintWriter pw = res.getWriter();
                pw.write("[3, 0, 0, \"*************\"]");   // Send a HEARTBEAT ?

                res.setStatus(HttpServletResponse.SC_OK);
                res.setContentType("application/json");
                
                asyncContexts.remove(sessionId,event.getAsyncContext());
            }                        

            private String getSessionId(AsyncEvent event) {
                HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();                            
                return (String)req.getSession().getAttribute(WAMP_SESSION_ID_ATTRIBUTE);
            }
        });
    }



    public void sendMsg(Long sessionId, String msg) {
        try {
            AsyncContext actx = asyncContexts.get(sessionId.toString());
            HttpServletResponse res = (HttpServletResponse)actx.getResponse();

            res.getWriter().write(msg);
            res.setStatus(HttpServletResponse.SC_OK);
            res.setContentType("application/json");

            actx.complete();
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }


}
