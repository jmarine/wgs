package org.wgs.sample;

import org.wgs.wamp.WampApplication;
import org.wgs.wamp.transport.http.longpolling.WampLongPollingServlet;


public class WgsLongPollingServlet extends WampLongPollingServlet
{
    @Override
    public void onApplicationStart(WampApplication app) 
    {
        app.registerWampModule(new org.wgs.service.game.Module(app)); 
    }
    
}
