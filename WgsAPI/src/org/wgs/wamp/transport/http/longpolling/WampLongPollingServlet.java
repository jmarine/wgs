package org.wgs.wamp.transport.http.longpolling;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import org.wgs.wamp.WampApplication;
import org.wgs.wamp.WampProtocol;
import org.wgs.wamp.WampSocket;
import org.wgs.wamp.encoding.WampEncoding;
import org.wgs.wamp.type.WampDict;
import org.wgs.wamp.type.WampList;



//TODO: COMPLETE LONG-POLLING SERVLET IMPLEMENTATION
//@WebServlet(urlPatterns = "/wamp2servlet", displayName = "wamp2servlet", asyncSupported = true)
public final class WampLongPollingServlet extends HttpServlet implements AsyncListener
{
    private static final  int  POLLING_TIMEOUT = 5000;
   
    
    private static ConcurrentHashMap<String, WampLongPollingSocket> sockets = new ConcurrentHashMap<String, WampLongPollingSocket>();
    private static ConcurrentHashMap<String, AsyncContext> asyncContexts = new ConcurrentHashMap<String, AsyncContext>();
    private static ConcurrentHashMap<String, LinkedBlockingQueue<Object>> messageQueues = new ConcurrentHashMap<String, LinkedBlockingQueue<Object>>();

    
    private WampApplication app;
    
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        System.out.println("WampLongPollingServlet: init");
        super.init(config);
        
        String contextPath = config.getServletContext().getContextPath();
        contextPath = contextPath.substring(0, contextPath.length() - 9);  // without "/longpoll"
        this.app = WampApplication.getInstance(WampApplication.WAMPv2, contextPath);
        System.out.println("WampLongPollingServlet: initialized on " + contextPath);
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
    {
        String path = request.getPathInfo();
        System.out.println("WampLongPollingServlet: service: path info = " + path);
        
        String wampSessionId = getWampSessionId(request);
        System.out.println("WampLongPollingServlet: service: wamp session id = " + wampSessionId);

        AsyncContext actx = request.startAsync(request, response);
        actx.addListener(this);
        
  
        WampLongPollingSocket socket = sockets.get(wampSessionId);
        if(path.endsWith("/open")) {
            LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
            messageQueues.put(wampSessionId, queue);
            
            asyncContexts.put(wampSessionId, actx);
            socket = new WampLongPollingSocket(app, this, wampSessionId, queue);
            socket.init();   
            sockets.put(wampSessionId, socket);
            
            
            WampDict obj = new WampDict();
            obj.put("protocol", "wamp.2.json");
            obj.put("transport", String.valueOf(wampSessionId));
            
            
            try {
                Object msg = WampEncoding.JSON.getSerializer().serialize(obj);
                queue.put(msg);
                app.onWampOpen(socket);
            } catch (Exception ex) {
                Logger.getLogger(WampLongPollingServlet.class.getName()).log(Level.SEVERE, "Error serializing data", ex);
            }
            
            actx.start(new MessageSender(wampSessionId, actx, messageQueues.get(wampSessionId)));
            
        } else if(path.endsWith("/receive")) {
            
            asyncContexts.put(wampSessionId, actx);
            actx.setTimeout(POLLING_TIMEOUT);
            actx.start(new MessageSender(wampSessionId, actx, messageQueues.get(wampSessionId)));
            
        } else if(path.endsWith("/send")) {
            actx.start(new MessageReceiver(socket, actx));

        } else if(path.endsWith("/close")) {
            
            app.onWampClose(sockets.remove(wampSessionId), new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "wamp.close.normal"));
            response.setStatus(202);
            actx.complete();
            
        } else {
            response.setStatus(400);
            actx.complete();
        }
        

    }
    
    public AsyncContext getAsyncContext(String wampSessionId)
    {
        return asyncContexts.get(wampSessionId);
    }

    
    public void sendMsg(String wampSessionId, Object msg) {
        try {
            System.out.println("About to sending: " + msg);
            AsyncContext actx = asyncContexts.get(wampSessionId);
            HttpServletRequest req = (HttpServletRequest)actx.getRequest();
            System.out.println("WampLongPollingServlet: sendmsg (" + req.getPathInfo() + ")");
            
            HttpServletResponse res = (HttpServletResponse)actx.getResponse();
            res.setStatus(HttpServletResponse.SC_OK);
            res.setContentType("application/json");
            res.getWriter().print(msg);
            actx.complete();
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }


    @Override
    public void onStartAsync(AsyncEvent event) throws IOException 
    {
        System.out.println("WampLongPollingServlet: startAsync");
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        String wampSessionId = getWampSessionId(req);
        asyncContexts.put(wampSessionId, event.getAsyncContext());                           
    }

    @Override
    public void onError(AsyncEvent event) throws IOException 
    {
        System.out.println("WampLongPollingServlet: onError");
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        String wampSessionId = getWampSessionId(req);        
        asyncContexts.remove(wampSessionId, event.getAsyncContext());
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException 
    {
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        System.out.println("WampLongPollingServlet: onComplete (" + req.getPathInfo() + ")");
        
        String wampSessionId = getWampSessionId(req);
        asyncContexts.remove(wampSessionId, event.getAsyncContext());
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException 
    {
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        System.out.println("WampLongPollingServlet: timeout (" + req.getPathInfo() + ")");
        
        String wampSessionId = getWampSessionId(req);
        WampSocket socket = sockets.get(wampSessionId);
        WampProtocol.sendHeartbeatMessage(socket, "****discard*****");
        Object msg = messageQueues.get(wampSessionId).poll();
        if(msg != null) sendMsg(wampSessionId, msg);
    }

    private String getWampSessionId(HttpServletRequest request) 
    {
        String wampSessionId = (String)request.getAttribute("wampSessionId");
        if(wampSessionId == null) {
            String pathInfo = request.getPathInfo();
            int sessionIdPos = pathInfo.indexOf("/", 1);
            if(sessionIdPos == -1) wampSessionId = String.valueOf(WampProtocol.newGlobalScopeId());
            else wampSessionId =  pathInfo.substring(1,sessionIdPos);
            request.setAttribute("wampSessionId", wampSessionId);
        }
        return wampSessionId;
    }


    class MessageReceiver implements Runnable
    {
        private String wampSessionId;
        private AsyncContext asyncContext;
        private LinkedBlockingQueue<Object> queue;
        private WampSocket socket;
        
        MessageReceiver(WampSocket socket, AsyncContext asyncContext)
        {
            this.socket = socket;
            this.asyncContext = asyncContext;
        }
        
        @Override
        public void run() {
            StringBuffer buffer = new StringBuffer();
            HttpServletRequest request = (HttpServletRequest)asyncContext.getRequest();
            HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
            
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
                String line;
                while( (line = reader.readLine()) != null) {
                    buffer.append(line);
                }
                
                String message = buffer.toString();
                WampList wampMessage = (WampList)WampEncoding.JSON.getSerializer().deserialize(message, 0, message.length());
                app.onWampMessage(socket, wampMessage);
                response.setStatus(202); 
                asyncContext.complete();

            } catch (Exception ex) {
                Logger.getLogger(WampLongPollingServlet.class.getName()).log(Level.SEVERE, null, ex);
                response.setStatus(400);
                asyncContext.complete();
                
            } finally {
                if(reader != null) {
                    try { reader.close(); }
                    catch(Exception ex) { }
                }
            }
            
            
            
        }
        
    }    
    
    class MessageSender implements Runnable
    {
        private String wampSessionId;
        private AsyncContext asyncContext;
        private LinkedBlockingQueue<Object> queue;
        
        
        MessageSender(String wampSessionId, AsyncContext asyncContext, LinkedBlockingQueue<Object> queue)
        {
            this.wampSessionId = wampSessionId;
            this.asyncContext = asyncContext;
            this.queue = queue;
        }
        

        @Override
        public void run() {
            try {
                Object obj = queue.poll(POLLING_TIMEOUT, TimeUnit.MILLISECONDS);
                if(obj != null) {
                    sendMsg(wampSessionId, obj);
                } 
            } catch (Exception ex) {
                Logger.getLogger(WampLongPollingServlet.class.getName()).log(Level.SEVERE, "Poll/send error", ex);
            }
        }
        
    }

}
