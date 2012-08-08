var WsgState = {
  DISCONNECTED: 0,
  ERROR: 1,
  CONNECTED: 2,
  WELCOMED: 3,
  AUTHENTICATED: 4
};

function WsgClient(u) {
  this.url = u;
  return this;
}

WsgClient.prototype = {
  ws: null,
  sid: null,
  onstatechange: null,
  calls: new Array(),
  topics: new Array(),
  
  debug: function(str) {
    console.log(str);
  },

  close: function() {
    if(this.ws /* && this.ws.state == this.ws.OPEN */) {
        this.ws.close();
        this.ws = null;
    }
  },

  send: function(msg) {
      if(!this.ws || this.ws.readyState != 1) {
         this.debug("Websocket is not avaliable for writing");
      } else {
         this.ws.send(msg);
      }
  },
  
  _newid: function() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
        return v.toString(16);
    });
  },
  
 
  prefix: function(str, url) {
      var arr = [];
      arr[0] = 1;  // PREFIX
      arr[1] = str;
      arr[2] = url;
      this.send(JSON.stringify(arr));
  },
 
  call: function(cmd, msg) {
      var dfd = $.Deferred();
      var arr = [];
      arr[0] = 2;  // CALL
      arr[1] = this._newid();
      arr[2] = cmd;
      arr[3] = msg;
      msg.cmd = cmd;
      this.calls[arr[1]] = dfd;
      this.send(JSON.stringify(arr));
      return dfd.promise();
  },  
  
  subscribe: function(topic, callback, clientSideOnly) {
        if(!this.topics[topic]) {
            this.topics[topic] = [];
        }
        this.topics[topic].push(callback);
        if(!clientSideOnly && this.topics[topic].length == 1) {
            // sends subscription to server when 1st callback subscribed
            var arr = [];
            arr[0] = 5;  // SUBSCRIBE
            arr[1] = topic;
            this.send(JSON.stringify(arr));
        }
  },
  
  unsubscribe: function(topic, callback, clientSideOnly) {
        var callbacks = this.topics[topic];
        if(callbacks) {
            var index = callbacks.indexOf(callback);
            if(index != -1) callbacks.splice(index,1);
            if(callbacks.length == 0) {
                delete this.topics[topic];
                if(!clientSideOnly) {
                    // send unsubscription to server (when no callbacks available)
                    var arr = [];
                    arr[0] = 6;  // UNSUBSCRIBE
                    arr[1] = topic;    
                    this.send(JSON.stringify(arr));
                }
            }
        }
  },
  
  publish: function(topic, event) {
      var arr = [];
      arr[0] = 7;  // PUBLISH
      arr[1] = topic;
      arr[2] = event;
      this.send(JSON.stringify(arr));
  }, 
  
  
  login: function(nick, password, onstatechange) {
      var client = this;
      this._connect(function(state, msg) {
        if(state == WsgState.WELCOMED) {
            var msg = Object();
            msg.nick = nick;
            msg.password = password;  // hash_sha1(password : this.sid)
            this.prefix("wsg", "https://github.com/jmarine/wampservices/wsgservice#");
            this.call("wsg:login", msg).then(
                function(response) {
                    client.nick = nick;
                    client.onstatechange(WsgState.AUTHENTICATED);
                }, 
                function(response) {
                    client.onstatechange(WsgState.ERROR, "error:login");
                });
        } else {
            onstatechange(state, msg);
        }
      });
  },
  
  
  register: function(nick, password, email, onstatechange) {
      var client = this;
      this._connect(function(state, msg) {
        if(state == WsgState.WELCOMED) {
            var msg = Object();
            msg.nick = nick;
            msg.password = password;  // hash_sha1(password)
            msg.email = email;
            this.prefix("wsg", "https://github.com/jmarine/wampservices/wsgservice#");
            this.call("wsg:register", msg).then(
                function(response) {
                    client.nick = nick;
                    client.onstatechange(WsgState.AUTHENTICATED);
                }, 
                function(response) {
                    client.onstatechange(WsgState.ERROR, "error:register");
                });
        } else {
            onstatechange(state, msg);
        }
      });
  },  
  
  
  _connect: function(onstatechange) {
      var client = this;
      var ws = null; 
      this.ws = null;
      this.onstatechange = onstatechange;
      this.debug("Connecting to url: " + this.url);

      if ("WebSocket" in window) {
        ws = new WebSocket(this.url);
      } else if ("MozWebSocket" in window) {
        ws = new MozWebSocket(this.url);
      } else {
        this.debug("This Browser does not support WebSockets");
        client.onstatechange(WsgState.ERROR, "error:ws");
        return;
      }

      ws.onopen = function(e) {
        client.debug("A connection to "+this.url+" has been opened.");
        client.ws = ws;
        client.onstatechange(WsgState.CONNECTED);
        //$("#server_url").attr("disabled",true);
        //$("#toggle_connect").html("Disconnect");
      };
   
      ws.onclose = function(e) {
        client.debug("The connection to "+this.url+" was closed.");
        client.onstatechange(WsgState.DISCONNECTED);    
        client.close();
      };

      ws.onerror = function(e) {
        client.debug("WebSocket error: " + e);
        client.onstatechange(WsgState.ERROR, "error:ws");
        client.close();
      };

      ws.onmessage = function(e) {
        client.debug("ws.onmessage: " + e.data);
        var arr = JSON.parse(e.data);

        if (arr[0] == 0) {  // WELCOME
            client.sid = arr[1];
            client.onstatechange(WsgState.WELCOMED, arr);
        } else if (arr[0] == 3) {  // CALLRESULT
            var call = arr[1];
            if(client.calls[call]) {       
                var args = arr[2];
                if(!args) args = {};
                args.valid = true;
                client.calls[call].resolve(args);
                delete client.calls[call];
            } else {
                client.debug("call not found: " + call);
            }
        } else if (arr[0] == 4) {  // CALLERROR
            var call = arr[1];
            if(client.calls[call]) {  
                var args = arr[4];
                if(!args) args = {};
                args.valid = false;
                client.calls[call].reject(args);
                delete client.calls[call];
            } else {
                client.debug("call not found: " + call);
            }            
        } else if(arr[0] == 8) {  // EVENT
            var topicURI = arr[1];
            if(client.topics[topicURI]) {
                client.topics[topicURI].forEach(function(callback) {
                    callback(arr[2], topicURI);                
                });
            } else {
                // client.debug("topic not found: " + topic);
            }
            
        } else {
            client.debug("Server message not recognized: " + message.type);
        }

      };

  },
  
  
  listApps: function(filterByDomain, callback) {
      var msg = Object();
      if(filterByDomain) msg.domain = document.domain.toString();
          
      this.call("wsg:list_apps", msg).then(callback, callback);
  },
  
  listGroups: function(appId, callback) {
      var msg = Object();
      msg.app = appId;
          
      this.call("wsg:list_groups", msg).then(callback, callback);
  },  
  
  newApp: function(name, domain, version, min, max, observable, dynamic, alliances, ai_available, roles, callback) {
      var msg = Object();
      msg.name = name;
      msg.domain = domain;
      msg.version = version;
      msg.min = min;
      msg.max = max;
      msg.observable = observable;      
      msg.dynamic = dynamic;
      msg.alliances = alliances;
      msg.ai_available = ai_available;
      msg.roles = roles;
      
      this.call("wsg:new_app", msg).then(callback, callback);
  },

  deleteApp: function(appId, callback) {
      var msg = Object();
      msg.app = appId;
          
      this.call("wsg:delete_app", msg).then(callback, callback);
  },  
  
  openGroup: function(appId, gid, callback) {
      var msg = Object();
      if(gid) msg.gid = gid;
      if(appId) msg.app = appId;
      
      this.call("wsg:open_group", msg).then(callback, callback);
  },
  
  exitGroup: function(appId, gid, callback) {
      var msg = Object();
      msg.gid = gid;
      msg.app = appId;
           
      this.call("wsg:exit_group", msg).then(callback, callback);
  },

  updateGroup: function(appId, gid, observable, dynamic, alliances, callback) {
      var msg = Object();
      msg.app = appId;
      msg.gid = gid;
      msg.observable = observable;
      msg.dynamic = dynamic;
      msg.alliances = alliances;      
     
      this.call("wsg:update_group", msg).then(callback, callback);
  },

  updateMember: function(appId, gid, slot, sid, usertype, nick, role, team, callback) {
      var msg = Object();
      msg.app = appId;
      msg.gid = gid;
      msg.slot = slot;
      msg.sid = sid;
      msg.nick = nick;
      msg.role = role;
      msg.team = team;
      msg.type = usertype;
     
      this.call("wsg:update_member", msg).then(callback, callback);
  }

}
