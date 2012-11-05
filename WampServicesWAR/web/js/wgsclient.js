var WgsState = {
  DISCONNECTED: 0,
  ERROR: 1,
  CONNECTED: 2,
  WELCOMED: 3,
  AUTHENTICATED: 4
};

function WgsClient(u) {
  this.url = u;
  return this;
}

WgsClient.prototype = {
  ws: null,
  sid: null,
  groups: new Array(),
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
 
  call: function(cmd, args, isOnlyOneArrayArg) {
      var dfd = $.Deferred();
      var msg = [];
      msg[0] = 2;  // CALL
      msg[1] = this._newid();
      msg[2] = cmd;
      if(args && (args instanceof Array) && (!isOnlyOneArrayArg)) {
          for(var i = 0; i < args.length; i++) {
              msg[3+i] = args[i];
          }
      } else {
          msg[3] = args;
      }
      this.calls[msg[1]] = dfd;
      this.send(JSON.stringify(msg));
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


  openIdConnectLoginUrl: function(provider, redirectUri, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.provider = provider;
            msg.redirect_uri = redirectUri;
            client.prefix("wgs", "https://github.com/jmarine/wampservices/wgs#");
            client.call("wgs:openid_connect_login_url", msg).then(
                function(response) {
                    client.close();
                    document.location.href = response;
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                });
        } else {
            onstatechange(state, msg);
        }
      });
  },

  
  openIdConnectAuthCode: function(provider, redirectUri, code, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.provider = provider;
            msg.redirect_uri = redirectUri;
            msg.code = code;
            client.prefix("wgs", "https://github.com/jmarine/wampservices/wgs#");
            client.call("wgs:openid_connect_auth", msg).then(
                function(response) {
                    client.user = response.user;
                    onstatechange(WgsState.AUTHENTICATED, response);
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                });
        } else {
            onstatechange(state, msg);
        }
      });
  },
  
  login: function(user, password, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.user = user;
            msg.password = password;  // hash_sha1(password : this.sid)
            client.prefix("wgs", "https://github.com/jmarine/wampservices/wgs#");
            client.call("wgs:login", msg).then(
                function(response) {
                    client.user = response.user;
                    onstatechange(WgsState.AUTHENTICATED, response);
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                });
        } else {
            onstatechange(state, msg);
        }
      });
  },
  
  
  register: function(user, password, email, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.user = user;
            msg.password = password;  // hash_sha1(password)
            msg.email = email;
            client.prefix("wgs", "https://github.com/jmarine/wampservices/wgs#");
            client.call("wgs:register", msg).then(
                function(response) {
                    client.user = response.user;
                    onstatechange(WgsState.AUTHENTICATED, response);
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
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
      this.debug("Connecting to url: " + this.url);

      if ("WebSocket" in window) {
        ws = new WebSocket(this.url);
      } else if ("MozWebSocket" in window) {
        ws = new MozWebSocket(this.url);
      } else {
        this.debug("This Browser does not support WebSockets");
        onstatechange(WgsState.ERROR, "error:ws");
        return;
      }

      ws.onopen = function(e) {
        client.debug("A connection to "+this.url+" has been opened.");
        client.ws = ws;
        onstatechange(WgsState.CONNECTED);
        //$("#server_url").attr("disabled",true);
        //$("#toggle_connect").html("Disconnect");
      };
   
      ws.onclose = function(e) {
        client.debug("The connection to "+this.url+" was closed.");
        onstatechange(WgsState.DISCONNECTED);    
        client.close();
      };

      ws.onerror = function(e) {
        client.debug("WebSocket error: " + e);
        onstatechange(WgsState.ERROR, "error:ws");
        client.close();
      };

      ws.onmessage = function(e) {
        client.debug("ws.onmessage: " + e.data);
        var arr = JSON.parse(e.data);

        if (arr[0] == 0) {  // WELCOME
            client.sid = arr[1];
            onstatechange(WgsState.WELCOMED, arr);
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
                var args = {};
                args.valid = false;
                args.errorURI = arr[2];
                if(arr.length >= 4) args.errorDesc = arr[3];
                if(arr.length == 5) args.errorDetails = arr[4];
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

      this.call("wgs:list_apps", msg).then(function(response) {
          callback(response);
        }, function(response) {
          callback(response);
        });
  },
  
  listGroups: function(appId, callback) {
      this.call("wgs:list_groups", appId).then(callback, callback);
  },

  newApp: function(name, domain, version, min, max, delta, observable, dynamic, alliances, ai_available, roles, callback) {
      var msg = Object();
      msg.name = name;
      msg.domain = domain;
      msg.version = version;
      msg.min = min;
      msg.max = max;
      msg.delta = delta;
      msg.observable = observable;      
      msg.dynamic = dynamic;
      msg.alliances = alliances;
      msg.ai_available = ai_available;
      msg.roles = roles;
      
      this.call("wgs:new_app", msg).then(callback, callback);
  },

  deleteApp: function(appId, callback) {
      var msg = Object();
      msg.app = appId;
          
      this.call("wgs:delete_app", msg).then(callback, callback);
  },  
  
  openGroup: function(appId, gid, options, callback) {
      var client = this;
      var args = Array();
      args[0] = appId? appId : null;
      args[1] = gid? gid : null;
      args[2] = options;

      this.call("wgs:open_group", args).then(function(response) {
          var _update_group_users = function(msg, topicURI) {
                if(msg.connections) {
                    client.groups[msg.gid] = new Object();
                    client.groups[msg.gid].connections = new Array();
                    client.groups[msg.gid].members = msg.members;
                    msg.connections.forEach(function(con) { 
                        client.groups[msg.gid].connections[con.sid] = con;
                    });
                } else if(msg.cmd == "user_joined" || msg.cmd == "user_updated") {
                    var gid = msg.gid;
                    if(!msg.updates) msg.updates = [ msg ];
                    msg.updates.forEach(function(item) {
                        client.groups[gid].connections[item.sid] = item;
                        if(item.slot) client.groups[gid].members[item.slot] = item;
                    });
                } else if(msg.cmd == "user_unjoined") {
                    delete client.groups[msg.gid].connections[msg.sid];
                    if(msg.members) {
                        msg.members.forEach(function(member) {
                            client.groups[msg.gid].members[member.slot] = null;
                        });
                    }
                }
            };
          client.subscribe("https://github.com/jmarine/wampservices/wgs#group_event:" + response.gid, _update_group_users, true);
          _update_group_users(response);
          callback(response);
      }, callback);
  },
  
  exitGroup: function(gid, callback) {
      this.call("wgs:exit_group", gid).then(callback, callback);
      this.unsubscribe("https://github.com/jmarine/wampservices/wgs#group_event:" + gid, this._update_group_users, true);
      delete this.groups[gid];
  },

  getGroupConnections: function(gid) {
      return this.groups[gid].connections;
  },
  

  updateGroup: function(appId, gid, state, data, automatch, hidden, observable, dynamic, alliances, callback) {
      var msg = Object();
      msg.app = appId;
      msg.gid = gid;
      msg.automatch = automatch;
      msg.hidden = hidden;
      msg.observable = observable;
      msg.dynamic = dynamic;
      msg.alliances = alliances;      
      if(state) msg.state = state;
      if(data) msg.data  = data;
     
      this.call("wgs:update_group", msg).then(callback, callback);
  },

  updateMember: function(appId, gid, state, slot, sid, usertype, user, role, team, callback) {
      var msg = Object();
      msg.app = appId;
      msg.gid = gid;
      msg.state = state;
      if(!isNaN(slot)) {
        msg.slot = slot;
        msg.sid  = sid;
        msg.user = user;
        msg.role = role;
        msg.team = team;
        msg.type = usertype;
      }
      this.call("wgs:update_member", msg).then(callback, callback);
  },
  
  sendGroupMessage: function(gid, data, callback) {
      var args = Array();
      args[0] = gid;
      args[1] = data;
      
      this.call("wgs:send_group_message", args).then(callback, callback);
  },

  sendTeamMessage: function(gid, data, callback) {
      var args = Array();
      args[0] = gid;
      args[1] = data;
      
      this.call("wgs:send_team_message", args).then(callback, callback);
  }
  
}
