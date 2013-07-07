// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This is a sample app to show how to use our push messaging service.
var channel = null;
var showSite = false;

chrome.alarms.onAlarm.addListener(onAlarm);
chrome.alarms.create('reactivate', {periodInMinutes: 0.15});

function openWgsSite() {
  chrome.app.window.create('wgs_gcm.html?gcmChannelId='+channel, {"hidden":true});
  showSite = false;
}

// This function gets called in the packaged app model on launch.
chrome.app.runtime.onLaunched.addListener(function() {
  console.log("Push Messaging Sample Client Launched!");

  if(channel != null) {
    openWgsSite();
  } else {
    showSite = true;
    pushSetup();
  }
});

/* This function gets called in the packaged app model on launch.
chrome.app.runtime.onRestarted.addListener(function() {
  console.log("Push Messaging Sample Client Restarted!");
  channel = null;
  pushSetup();
});
*/

// This function gets called on Chrome launch.
chrome.runtime.onStartup.addListener(function() {
  console.log("WGS on Chrome Startup");
  pushSetup();
});

function onAlarm(alarm) {
  console.log('Got alarm', alarm);
  if (alarm && alarm.name == 'reactivate') {
    pushSetup();
  }
}

// This function gets called in the packaged app model on install.
// Typically on install you will get the channelId, and send it to your
// server which will send Push Messages.
chrome.runtime.onInstalled.addListener(function() {
  pushSetup();
  console.log("Push Messaging Sample Client installed!");
});

// This function gets called in the packaged app model on shutdown.
// You can override it if you wish to do clean up at shutdown time.
chrome.runtime.onSuspend.addListener(function() {
  channel = null;
  chrome.pushMessaging.onMessage.removeListener(messageCallback);
  console.log("Push Messaging Sample Client onSuspend");
});

// This should only be called once on the instance of chrome where the app
// is first installed for this user. It need not be called every time the
// Push Messaging Client App starts.
function pushSetup() {
  // Start fetching the channel ID (it will arrive in the callback).
  if(channel == null) {
	chrome.pushMessaging.getChannelId(true, channelIdCallback);
  }
  if(!chrome.pushMessaging.onMessage.hasListeners()) {
        chrome.pushMessaging.onMessage.addListener(messageCallback);
  }
}

// This callback recieves the Push Message from the push server.
function messageCallback(message) {
  showPushMessage(message.payload, message.subchannelId.toString());
}


// When the channel ID callback is available, this callback recieves it.
// The push client app should communicate this to the push server app as
// the 'address' of this user and this app (on all instances of Chrome).
function channelIdCallback(message) {
  console.log("Background Channel ID callback seen, channel Id is " + message.channelId);
  channel = message.channelId;
  if(showSite) openWgsSite();
}


// When a Push Message arrives, show it as a text notification (toast)
function showPushMessage(payload, subChannel) {
  var notification = window.webkitNotifications.createNotification(
      'images/logo24.png', 'WGS message',
      "Push message for you! " +
      payload +" [" + subChannel + "]");
  notification.onclick = function() { openWgsSite(); notification.cancel(); }
  notification.show();
  
}
