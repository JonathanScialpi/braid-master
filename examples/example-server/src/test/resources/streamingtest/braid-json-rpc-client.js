/*
 * Copyright 2018 Royal Bank of Scotland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const SockJS = require('sockjs-client');
const Promise = require('promise');

class JsonRPC {
  constructor(url, options) {
    this.url = url;
    this.options = options;
    this.nextId = 1;
    this.state = {};
    this.status = "CLOSED";
    this.onOpen = null;
    this.onClose = null;
    this.socket = new SockJS(this.url, null, this.options);
    const thisObj = this;
    this.socket.onopen = function () {
      thisObj.openHandler();
    }
    this.socket.onclose = function () {
      thisObj.closeHandler();
    }
    this.socket.onmessage = function (e) {
      thisObj.messageHandler(JSON.parse(e.data));
    }
  }

  openHandler() {
    this.status = "OPEN";
    if(this.onOpen) {
      this.onOpen();
    }
  }

  closeHandler() {
    this.status = "CLOSED";
    if(this.onClose) {
      this.onClose();
    }
  }

  messageHandler(message) {
    if(message.hasOwnProperty('id')) {
      if(this.state.hasOwnProperty(message.id)) {
        if(message.hasOwnProperty("error")) {
          this.handleError(message);
        } else {
          this.handleResponse(message);
        }
      } else {
        console.error("couldn't find callback for message identifier " + message.id);
      }
    } else {
      console.warn("received message does not have an identifier", message)
    }
  }

  handleError(message) {
    const state = this.state[message.id];
    if(state.onError) {
      state.onError(new Error(`json rpc error ${message.error.code} with message ${message.error.message}`));
    }
    delete this.state[message.id];
  }

  handleResponse(message) {
    const hasResult = message.hasOwnProperty('result');
    const isCompleted = message.hasOwnProperty('completed');
    if(hasResult) {
      this.handleResultMessage(message);
    }
    if(isCompleted) {
      this.handleCompletionMessage(message);
    }
    if(!hasResult && !isCompleted) {
      this.handleUnrecognisedResponseMessage(message);
    }
  }

  handleResultMessage(message) {
    const state = this.state[message.id];
    if(!state) {
      console.error("could not find state for method " + message.id);
      return
    }
    // console.log("received", message);
    if(state.onNext) {
      state.onNext(message.result);
    }
  }

  handleCompletionMessage(message) {
    const state = this.state[message.id];
    if(state.onCompleted) {
      state.onCompleted();
    }
    delete this.state[message.id];
  }

  handleUnrecognisedResponseMessage(message) {
    console.error("unrecognised json rpc payload", message);
  }

  invoke(method, params) {
    const thisObj = this;
    return new Promise(function (resolve, reject) {
      thisObj.invokeForStream(method, params, resolve, reject, undefined, false);
    });
  }

  invokeForStream(method, params, onNext, onError, onCompleted, streamed) {
    const id = this.nextId++
    if(streamed === undefined) {
      streamed = true;
    }

    const payload = {
      id: id, jsonrpc: "2.0", method: method, params: params, streamed: streamed
    };
    // console.log("payload", payload);
    this.state[id] = {onNext: onNext, onError: onError, onCompleted: onCompleted};
    this.socket.send(JSON.stringify(payload));
    return new CancellableInvocation(this, id);
  }
}

class CancellableInvocation {
  constructor(jsonRPC, id) {
    this.jsonRPC = jsonRPC;
    this.id = id;
  }

  cancel() {
    if(this.jsonRPC.state[id]) {
      const payload = {
        cancel: this.id
      }
      this.jsonRPC.socket.send(payload);
      delete this.jsonRPC.state[id];
    }
  }
}

module.exports = JsonRPC;