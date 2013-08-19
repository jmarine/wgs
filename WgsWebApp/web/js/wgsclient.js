var WgsState = {
  DISCONNECTED: 0,
  ERROR: 1,
  CONNECTED: 2,
  WELCOMED: 3,
  ANONYMOUS: 4,  
  AUTHENTICATED: 5
};

function WgsClient(u) {
  this.serverWampVersion = 1;
  this.url = u;
  return this;
}

WgsClient.prototype = {
  ws: null,
  sid: null,
  state: WgsClient.DISCONNECTED,
  groups: new Array(),
  calls: new Array(),
  topicHandlers: new Array(),
  patternHandlers: new Array(),
  metaeventHandlers: new Array(),
  
  debug: function(str) {
    console.log(str);
  },
          
  setState: function(state) {
      this.state = state;
  },
          
  getState: function() {
    return this.state;  
  },

  close: function() {
    if(this.ws /* && this.ws.state == this.ws.OPEN */) {
        this.ws.close();
        this.ws = null;
    }
    this.state = WgsState.DISCONNECTED;
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
          
  setServerWampVersion: function(v) {
      this.serverWampVersion = v;
  },
          
  isServerUsingWampVersion: function(v) {
      return (this.serverWampVersion >= v);
  },
  
  hello: function() {
      this.serverSID = this._newid();
      var arr = [];
      arr[0] = 0;  // HELLO
      arr[1] = this.serverSID;
      arr[2] = {};  // HelloDetails
      arr[2].agent = "wgsclient/2.0.0";
      this.send(JSON.stringify(arr));
  },
          
  goodbye: function() {
      var arr = [];
      arr[0] = 2;   // Goodbye
      arr[1] = {};  // GoodbyeDetails
      this.send(JSON.stringify(arr));
  },
 
  prefix: function(str, url) {
      var arr = [];
      arr[0] = 1;  // PREFIX message is deprecated since WAMP v2
      arr[1] = str;
      arr[2] = url;
      this.send(JSON.stringify(arr));
  },
          
  call: function(cmd, args, wamp2OptionsOrWamp1ArgsAsArray) {
      var dfd = $.Deferred();
      var msg = [];
      msg[0] = this.isServerUsingWampVersion(2) ? 16 : 2;  // CALL
      msg[1] = this._newid();
      msg[2] = cmd;
      if(this.isServerUsingWampVersion(2)) {
          msg[3] = (args && (args instanceof Array))? args : [ args ];
          if(wamp2OptionsOrWamp1ArgsAsArray) msg[4] = wamp2OptionsOrWamp1ArgsAsArray;
      } else {
          if(args && (args instanceof Array) && (!wamp2OptionsOrWamp1ArgsAsArray)) {
              for(var i = 0; i < args.length; i++) {
                  msg[3+i] = args[i];
              }
          } else {
              msg[3] = args;
          }
      }
      this.calls[msg[1]] = dfd;
      this.send(JSON.stringify(msg));
      return dfd.promise();
  },  
          
  callCancel: function(callID, options) {
      var arr = [];
      arr[0] = 17;  // CALL_CANCEL
      arr[1] = callID;
      if(options) arr[2] = options;
      this.send(JSON.stringify(arr));      
  },
  
  subscribe: function(topic, event_cb, metaevent_cb, options) {
        var wildcards = false;
        if(!options) options = {};
        options.events = (event_cb != null);
        options.metaevents = (metaevent_cb != null);
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "*";
            options.match = "wildcard";
        }

        if(topic.indexOf("*") != -1) {
            wildcards = true;
            options.match = "wildcard";
            if(!this.patternHandlers[topic]) this.patternHandlers[topic] = [];
        } else {            
            if(!this.topicHandlers[topic]) this.topicHandlers[topic] = [];
        }
        
        if(!this.metaeventHandlers[topic]) this.metaeventHandlers[topic] = [];
        
        if(event_cb) {
            if(wildcards) this.patternHandlers[topic].push(event_cb);
            else this.topicHandlers[topic].push(event_cb);
        }
        if(metaevent_cb) {
            this.metaeventHandlers[topic].push(metaevent_cb);
        }
        if(!options.clientSideOnly) {
            var arr = [];
            arr[0] = this.isServerUsingWampVersion(2) ? 64 : 5;  // SUBSCRIBE
            arr[1] = topic;
            if(this.isServerUsingWampVersion(2)) arr[2] = options;
            this.send(JSON.stringify(arr));
        }
  },
  
  unsubscribe: function(topic, event_cb, metaevent_cb, options) {
        var client = this;
        var indexOfEventHandlerToClear = -1;
        var indexOfMetaHandlerToClear = -1;
        var clientSideOnly = options && options.clientSideOnly;
        
        if(!options) options = {};
        if(options.match && options.match.toLowerCase() == "prefix") {
            topic = topic + "*";
            options.match = "wildcard";
        }
        
        var callbacks = this.topicHandlers[topic];
        if(topic.indexOf("*") != -1) {
            options.match = "wildcard";
            callbacks = this.patternHandlers[topic];
        }
        if(event_cb && callbacks) {
            indexOfEventHandlerToClear = callbacks.indexOf(event_cb);
        }

        if(metaevent_cb && this.metaeventHandlers[topic]) {
            indexOfMetaHandlerToClear = this.metaeventHandlers[topic].indexOf(metaevent_cb);
        }   
        
        
        var _clearHandlers = function() {
            var wildcards = (topic.indexOf("*") != -1);
            if(wildcards) {
                if(event_cb && callbacks && callbacks.length<=1 && indexOfEventHandlerToClear!=-1) delete client.patternHandlers[topic];
                else if(indexOfEventHandlerToClear != -1) client.patternHandlers.splice(indexOfEventHandlerToClear,1);
            } else {
                if(event_cb && callbacks && callbacks.length<=1 && indexOfEventHandlerToClear!=-1) delete client.topicHandlers[topic];
                else if(indexOfEventHandlerToClear != -1) client.topicHandlers.splice(indexOfEventHandlerToClear,1);
            }
            
            if(metaevent_cb && client.metaeventHandlers[topic] && client.metaeventHandlers[topic].length<=1 && indexOfMetaHandlerToClear != -1) delete client.metaeventHandlers[topic];
            else if(indexOfMetaHandlerToClear != -1) client.metaeventHandlers.splice(indexOfMetaHandlerToClear,1);
        }
            
        if(!this.metaeventHandlers[topic] || this.metaeventHandlers[topic].length == 0) {
            _clearHandlers();
        } else {
            // defer metaevent callback deletion, until #left/#error metatopic is received for the subscribed sessionId
            this.metaeventHandlers[topic].push(function(topic2, metatopic, metaevent) {
               if(topic == topic2 && metaevent && metaevent.sessionId == client.sid) {
                   _clearHandlers();
               }
            });
        }        
        
        if(!clientSideOnly) {
            // send unsubscribe message to server (when all eventHandlers or metaeventHandlers are cleared)
            var arr = [];
            arr[0] = this.isServerUsingWampVersion(2) ? 65 : 6;  // Unsubscribe message type
            arr[1] = topic;  
            this.send(JSON.stringify(arr));
        }   

  },
  
  publish: function(topic, event, options) {
      var arr = [];
      arr[0] = this.isServerUsingWampVersion(2) ? 66 : 7;  // PUBLISH
      arr[1] = topic;
      arr[2] = event;
      if(this.isServerUsingWampVersion(2) && options) arr[3] = options;
      this.send(JSON.stringify(arr));
  }, 



  
  authreq: function(authKey, authExtra, callback) {
      if(!authExtra) authExtra = {};
      var args = []
      args[0] = authKey;
      args[1] = authExtra;
      this.call("http://api.wamp.ws/procedure#authreq", args).then(callback,callback);      
  },
          
  auth: function(signature, callback) {
      this.call("http://api.wamp.ws/procedure#auth", signature).then(callback,callback);      
  },          
  
  getUserInfo: function(callback) {
      this.call("https://wgs.org#get_user_info").then(callback,callback);
  },
  
  login: function(user, password, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        onstatechange(state, msg);
        if(state == WgsState.WELCOMED) {
          if(user == null || user.length == 0) {
            client.user = "#anonymous-" + client.sid;
            client.state = WgsState.ANONYMOUS;
            onstatechange(WgsState.ANONYMOUS);              
          } else {
            var authExtra = null; // { salt: "RANDOMTEXT", keylen: 32, iterations: 4096 };
            client.authreq(user, authExtra, function(response) {
                if(typeof(response) == "string") {
                    var challenge = JSON.parse(response);
                    password = CryptoJS.MD5(password).toString();
                    if(challenge.extra && challenge.extra.salt) {
                        var key = CryptoJS.PBKDF2(password, challenge.extra.salt, { keySize: challenge.extra.keylen / 4, iterations: challenge.extra.iterations, hasher: CryptoJS.algo.SHA256 });
                        password = key.toString(CryptoJS.enc.Base64);                        
                    }
                    var signature = CryptoJS.HmacSHA256(response, password).toString(CryptoJS.enc.Base64);
                    client.auth(signature, function(response) {
                        if(response.valid) {
                            client.getUserInfo(function(response) {
                                if(response.valid) {
                                    client.user = user;
                                    client.state = WgsState.AUTHENTICATED;
                                    onstatechange(WgsState.AUTHENTICATED, response);
                                } else {
                                    var errorCode = response.errorURI;
                                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                                }
                            });
                        } else {
                            var errorCode = response.errorURI;
                            onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                        }
                    });
                } else {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                }
            });
          }
        }
      });
  },
  
  
  register: function(user, password, email, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        onstatechange(state, msg);
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.user = user;
            msg.password = CryptoJS.MD5(password).toString();
            msg.email = email;
            client.call("https://wgs.org#register", msg).then(
                function(response) {
                    client.user = response.user;
                    client.state = WgsState.AUTHENTICATED;
                    onstatechange(WgsState.AUTHENTICATED, response);
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                });
        }
      });
  },  
  
  
  openIdConnectProviders: function(redirectUri, callback) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.redirect_uri = redirectUri;
            client.call("https://wgs.org#openid_connect_providers", msg).then(
                function(response) {
                    //client.close();
                    callback(response);
                }, 
                function(response) {
                    client.close();
                    callback(response);
                });
        } 
      });
  },

  openIdConnectLoginUrl: function(principal, redirectUri, notificationChannel, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.principal = principal;
            msg.redirect_uri = redirectUri;
            msg.state = notificationChannel;
            client.call("https://wgs.org#openid_connect_login_url", msg).then(
                function(response) {
                    client.close();
                    document.location.href = response;
                    //window.open(response + "&nonce=" + escape(client.sid), "_blank");
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                });
        }
      });
  },

  
  openIdConnectAuthCode: function(provider, redirectUri, code, notificationChannel, onstatechange) {
      var client = this;
      client._connect(function(state, msg) {
        onstatechange(state, msg);          
        if(state == WgsState.WELCOMED) {
            var msg = Object();
            msg.provider = provider;
            msg.code = code;
            msg.redirect_uri = redirectUri;
            if(notificationChannel) msg.notification_channel = notificationChannel;
            
            client.call("https://wgs.org#openid_connect_auth", msg).then(
                function(response) {
                    client.user = response.user;
                    client.state = WgsState.AUTHENTICATED;
                    onstatechange(WgsState.AUTHENTICATED, response);
                }, 
                function(response) {
                    var errorCode = response.errorURI;
                    onstatechange(WgsState.ERROR, "error:" + errorCode.substring(errorCode.indexOf("#")+1));
                });
        }
      });
  },
  
  
  _connect: function(onstatechange) {
      var client = this;
      this.user = null;
      this.debug("Connecting to url: " + this.url);

      if(this.state >= WgsState.WELCOMED) {
        // REAUTHENTICATION
        onstatechange(WgsState.WELCOMED);
        
      } else {
        // RESET CONNECTION:
        var ws = null; 
        this.ws = null;
        
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
          this.state = WgsState.CONNECTED;
          onstatechange(WgsState.CONNECTED);
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

          if (arr[0] == 0) {  // WELCOME (WAMPv1) / HELLO (WAMPv2)
              client.sid = arr[1];
              if(!isFinite(arr[2])) { // Version in WAMP v1
                  client.setServerWampVersion(2);
                  client.hello();  // WAMPv2
              }
              client.state = WgsState.WELCOMED;
              onstatechange(WgsState.WELCOMED);
          } else if (arr[0] == 3 || arr[0] == 32) {  // CALLRESULT
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
          } else if (arr[0] == 4 || arr[0] == 34) {  // CALLERROR
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
          } else if(arr[0] == 8 || arr[0] == 128) {  // EVENT
              var topicURI = arr[1];
              if(client.topicHandlers[topicURI]) {
                  client.topicHandlers[topicURI].forEach(function(callback) {
                      callback.call(client, arr[2], topicURI);                
                  });
              } else {

                  for(var pattern in client.patternHandlers) {
                      if(client.topicMatchesWithPattern(topicURI,pattern)) {
                          client.patternHandlers[pattern].forEach(function(callback) {
                             callback.call(client, arr[2], topicURI);                
                          });
                      }
                  }
                  // client.debug("topic not found: " + topic);
              }

          } else if(arr[0] == 129) {  // META-EVENT  (not supported by WAMP v1)
              var topicURI = arr[1];
              if(client.metaeventHandlers[topicURI]) {
                  client.metaeventHandlers[topicURI].forEach(function(callback) {
                      var metatopic = arr[2];
                      var metaevent = (arr.length > 2) ? arr[3] : null;
                      callback.call(client, topicURI, metatopic, metaevent);
                  });
              } else {
                  // client.debug("topic not found: " + topic);
              }            

          } else {
              client.debug("Server message not recognized: " + message.type);
          }

        };
      }

  },
  
  listApps: function(filterByDomain, callback) {
      var msg = Object();
      if(filterByDomain) msg.domain = document.domain.toString();

      this.call("https://wgs.org#list_apps", msg).then(function(response) {
          callback(response);
        }, function(response) {
          callback(response);
        });
  },
  
  listGroups: function(appId, filters, callback) {
      var args = [ appId, filters ];
      this.call("https://wgs.org#list_groups", args).then(callback, callback);
  },

  newApp: function(name, domain, version, maxScores, descScoreOrder, min, max, delta, observable, dynamic, alliances, ai_available, roles, callback) {
      var msg = Object();
      msg.name = name;
      msg.domain = domain;
      msg.version = version;
      msg.max_scores = maxScores;
      msg.desc_score_order = descScoreOrder;
      msg.min = min;
      msg.max = max;
      msg.delta = delta;
      msg.observable = observable;      
      msg.dynamic = dynamic;
      msg.alliances = alliances;
      msg.ai_available = ai_available;
      msg.roles = roles;
      
      this.call("https://wgs.org#new_app", msg).then(callback, callback);
  },

  deleteApp: function(appId, callback) {
      var msg = Object();
      msg.app = appId;
          
      this.call("https://wgs.org#delete_app", msg).then(callback, callback);
  },  
  
  setSubscriptionStatus: function(topicURI, newStatus, callback) {
      var args = Array();
      args[0] = topicURI;
      args[1] = newStatus;
      this.call("https://wgs.org#set_subscription_status", args).then(callback,callback);
  },
  
  _update_group_users: function(msg, topicURI) {
      var client = this;
      if(msg.connections) {
          client.groups[msg.gid] = new Object();
          client.groups[msg.gid].members = msg.members;
          client.groups[msg.gid].connections = new Array();
          msg.connections.forEach(function(con) { 
              client.groups[msg.gid].connections[con.sid] = con;
          });
      } else if(msg.cmd == "user_joined" || msg.cmd == "group_updated") {
          var gid = msg.gid;
          
          if(isFinite(msg.slot)) msg.members = [ msg ];
          else if(msg.members) client.groups[gid].members = new Array();

          if(msg.cmd == "user_joined") client.groups[gid].connections[msg.sid] = msg;
          
          if(msg.members) {
              msg.members.forEach(function(item) {
                  if(item.sid.length > 0) client.groups[gid].connections[item.sid] = item;
                  if(isFinite(item.slot)) client.groups[gid].members[item.slot] = item;
              });
          }
      } else if(msg.cmd == "user_detached") {
          delete client.groups[msg.gid].connections[msg.sid];
          if(msg.members) {
              msg.members.forEach(function(item) {
                  if(isFinite(item.slot)) client.groups[msg.gid].members[item.slot] = item;
              });
          }
      }
  },  
  
  openGroup: function(appId, gid, options, callback) {
      var client = this;
      var args = Array();
      args[0] = appId? appId : null;
      args[1] = gid? gid : null;
      args[2] = options;

      this.call("https://wgs.org#open_group", args).then(function(response) {
          client.subscribe("https://wgs.org#group_event:" + response.gid, client._update_group_users, null, { "clientSideOnly":true} );
          client._update_group_users(response);
          callback(response);
      }, callback);
  },
  
  exitGroup: function(gid, callback) {
      var client = this;
      this.call("https://wgs.org#exit_group", gid).then(callback, callback);
      this.unsubscribe("https://wgs.org#group_event:" + gid, client._update_group_users, null, { "clientSideOnly":true});
      delete this.groups[gid];
  },

  getGroupConnections: function(gid) {
      return this.groups[gid].connections;
  },
          
  getGroupMembers: function(gid) {
      return this.groups[gid].members;
  },
  
  getGroupMember: function(gid,slot) {
      return this.groups[gid].members[slot];
  },

  updateGroup: function(appId, gid, state, data, automatch, hidden, observable, dynamic, alliances, callback) {
      var client = this;
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
     
      this.call("https://wgs.org#update_group", msg).then(function(response) { 
          client._update_group_users(response);
          callback(response);
      }, 
      function(response) { 
          client._update_group_users(response);
          callback(response) 
      } );
  },

  updateMember: function(appId, gid, state, slot, sid, usertype, user, role, team, callback) {
      var client = this;      
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
      this.call("https://wgs.org#update_member", msg).then(function(response) { 
          client._update_group_users(response);
          callback(response);
      }, 
      function(response) { 
          client._update_group_users(response);
          callback(response) 
      } );
  },
  
  sendGroupMessage: function(gid, data, callback) {
      var args = Array();
      args[0] = gid;
      args[1] = data;
      
      this.call("https://wgs.org#send_group_message", args).then(callback, callback);
  },

  sendTeamMessage: function(gid, data, callback) {
      var args = Array();
      args[0] = gid;
      args[1] = data;
      
      this.call("https://wgs.org#send_team_message", args).then(callback, callback);
  },
          
  topicMatchesWithPattern: function(topicURI,pattern) {
      topicURI = topicURI.replace("*", ".*");
      var re = new RegExp(pattern);
      return topicURI.match(re);
  },
  
}
