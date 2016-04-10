package org.wgs.wamp.transport.http.longpolling;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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


public class WampLongPollingServlet extends HttpServlet implements AsyncListener
{
    private static ConcurrentHashMap<Long, AsyncContext> asyncContexts = new ConcurrentHashMap<Long, AsyncContext>();
    private static ConcurrentHashMap<Long, LinkedBlockingQueue<Object>> messageQueues = new ConcurrentHashMap<Long, LinkedBlockingQueue<Object>>();
    private static ConcurrentHashMap<Long, Long> socketIdByTransport = new ConcurrentHashMap<Long, Long>();

    private WampApplication application;
    private long pollTimeoutMillis;
    
    
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        System.out.println("WampLongPollingServlet: init");
        super.init(config);
        
        String contextPath = config.getInitParameter("wgs.longpoll.context");
        if(contextPath == null) {
            contextPath = config.getServletContext().getContextPath();
            if(contextPath.endsWith("-longpoll")) contextPath = contextPath.substring(0, contextPath.length() - 9);  // without "-longpoll"
        }
        
        String timeout = config.getInitParameter("wgs.longpoll.timeoutMilliseconds");
        pollTimeoutMillis = (timeout != null) ? Long.parseLong(timeout) : 15000; 
        
        application = WampApplication.getInstance(WampApplication.WAMPv2, contextPath);
        System.out.println("WampLongPollingServlet: initialized on " + contextPath);
    }

    
    public WampApplication getWampApplication()
    {
        return application;
    }
    
 
    public void onApplicationStart(WampApplication app) 
    {
    }
    

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
    {
        if(application.start()) onApplicationStart(application);
        
        String path = request.getPathInfo();
        System.out.println("WampLongPollingServlet: service: path info = " + path);
        
        Long transport = getWampTransport(request);
        Long socketId = (transport != null)? socketIdByTransport.get(transport) : null;
        System.out.println("WampLongPollingServlet: service: socketId=" + socketId);

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Allow-Control-Allow-Methods", "POST,GET,OPTIONS"); 
        
        AsyncContext actx = request.startAsync(request, response);
        actx.addListener(this);
        
  
        WampSocket socket = application.getSocketById(socketId);
        if(path.endsWith("/open")) {
            LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
            socket = new WampLongPollingSocket(application, request, queue);
            socket.init();   
            
            transport = WampProtocol.newGlobalScopeId();
            setWampTransport(request, transport);
            
            socketIdByTransport.put(transport, socket.getSocketId());
            messageQueues.put(transport, queue);
            asyncContexts.put(transport, actx);

            application.onWampOpen(socket);            
            
            // Long-polling OPEN response:
            try {
                WampDict obj = new WampDict();
                obj.put("protocol", socket.getNegotiatedSubprotocol());
                obj.put("transport", transport);
                
                Object msg = WampEncoding.JSON.getSerializer().serialize(obj);
                queue.put(msg);
                
            } catch (Exception ex) {
                Logger.getLogger(WampLongPollingServlet.class.getName()).log(Level.SEVERE, "Error serializing data", ex);
            }
            
            actx.start(new MessageSender(transport, actx, messageQueues.get(transport)));
            
        } else if(path.endsWith("/receive")) {
            
            asyncContexts.put(transport, actx);
            actx.setTimeout(pollTimeoutMillis);
            actx.start(new MessageSender(transport, actx, messageQueues.get(transport)));
            
        } else if(path.endsWith("/send")) {
            
            actx.start(new MessageReceiver(socket, actx));

        } else if(path.endsWith("/close")) {
            
            application.onWampClose(socket, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "wamp.close.normal"));

            messageQueues.remove(transport);
            asyncContexts.remove(transport);
            socketIdByTransport.remove(transport);
            
            response.setContentType("text/plain");            
            response.setStatus(HttpServletResponse.SC_ACCEPTED);  // 202
            actx.complete();
            
        } else {
            
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);  // 400
            actx.complete();
        }
        
    }
    
    
    public void sendMsg(Long transport, Object msg) 
    {
        try {
            System.out.println("About to sending: " + msg);
            AsyncContext actx = asyncContexts.get(transport);
            if(actx != null) {
                HttpServletRequest req = (HttpServletRequest)actx.getRequest();
                System.out.println("WampLongPollingServlet: sendmsg (" + req.getPathInfo() + ")");

                HttpServletResponse res = (HttpServletResponse)actx.getResponse();
                if(msg == null) {
                    res.setStatus(HttpServletResponse.SC_ACCEPTED);  // 202
                    res.setContentType("text/plain");

                } else {
                    res.setStatus(HttpServletResponse.SC_OK);  // 200
                    res.setContentType("application/json");
                    res.getWriter().print(msg);
                }
                actx.complete();
            }
            
        } catch(Exception ex) {
            System.err.println("Error: " + ex.getClass().getName() + ": " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }


    @Override
    public void onStartAsync(AsyncEvent event) throws IOException 
    {
        System.out.println("WampLongPollingServlet: startAsync");
    }

    @Override
    public void onError(AsyncEvent event) throws IOException 
    {
        System.out.println("WampLongPollingServlet: onError");
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        Long transport = getWampTransport(req);        
        asyncContexts.remove(transport, event.getAsyncContext());
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException 
    {
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        System.out.println("WampLongPollingServlet: onComplete (" + req.getPathInfo() + ")");
        
        Long transport = getWampTransport(req);
        asyncContexts.remove(transport, event.getAsyncContext());
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException 
    {
        HttpServletRequest req = (HttpServletRequest)event.getAsyncContext().getRequest();
        System.out.println("WampLongPollingServlet: timeout (" + req.getPathInfo() + ")");
        
        Long transport = getWampTransport(req);
        Object msg = messageQueues.get(transport).poll();
        sendMsg(transport, msg);
    }

    
    private void setWampTransport(HttpServletRequest request, Long transport)
    {
        request.setAttribute("wampTransport", transport);
    }

    private Long getWampTransport(HttpServletRequest request) 
    {
        Long transport = (Long)request.getAttribute("wampTransport");
        if(transport == null) {
            String pathInfo = request.getPathInfo();
            int sessionIdPos = pathInfo.indexOf('/', 1);
            if(sessionIdPos != -1) transport = Long.valueOf(pathInfo.substring(1,sessionIdPos));
            setWampTransport(request, transport);
        }
        return transport;
    }
    
    
    class MessageReceiver implements Runnable
    {
        private WampSocket socket;
        private AsyncContext asyncContext;
        
        MessageReceiver(WampSocket socket, AsyncContext asyncContext)
        {
            this.socket = socket;
            this.asyncContext = asyncContext;
        }
        
        @Override
        public void run() {
            HttpServletRequest request = (HttpServletRequest)asyncContext.getRequest();
            HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();

            try {
                byte binaryBuffer[] = new byte[10240];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try(InputStream stream = request.getInputStream()) {
                    int len;
                    while( (len = stream.read(binaryBuffer)) > 0) {
                        baos.write(binaryBuffer, 0, len);
                    }
                } finally {
                    binaryBuffer = baos.toByteArray();
                }

                WampList wampMessage = null;
                switch(socket.getEncoding()) {
                    case MsgPack:
                    case BatchedMsgPack:
                        wampMessage = (WampList)socket.getEncoding().getSerializer().deserialize(binaryBuffer, 0, binaryBuffer.length);
                        break;
                    
                    case JSON:
                    case BatchedJSON:
                        String charEncoding = request.getCharacterEncoding();
                        String message = new String(binaryBuffer, charEncoding);
                        wampMessage = (WampList)socket.getEncoding().getSerializer().deserialize(message, 0, message.length());
                        break;
                }
            
                application.onWampMessage(socket, wampMessage);
                response.setContentType("text/plain");  // to prevent firefox "no element found" warnings on empty response
                response.setStatus(HttpServletResponse.SC_ACCEPTED);  // 202
                asyncContext.complete();

            } catch (Exception ex) {
                Logger.getLogger(WampLongPollingServlet.class.getName()).log(Level.SEVERE, null, ex);
                response.setContentType("text/plain");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);  // 400
                asyncContext.complete();
            } 
            
        }
        
    }    
    

    class MessageSender implements Runnable
    {
        private Long transport;
        private LinkedBlockingQueue<Object> queue;
        
        
        MessageSender(Long transport, AsyncContext asyncContext, LinkedBlockingQueue<Object> queue)
        {
            this.transport = transport;
            this.queue = queue;
        }
        

        @Override
        public void run() {
            try {
                Object obj = queue.poll(pollTimeoutMillis, TimeUnit.MILLISECONDS);
                sendMsg(transport, obj);
            } catch (Exception ex) {
                Logger.getLogger(WampLongPollingServlet.class.getName()).log(Level.SEVERE, "Poll/send error", ex);
            }
        }
        
    }

}
