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

const $ = require('jquery');
const ServiceProxy = require('./braid-service-proxy');

$(document).ready(() => {
  const url = "http://localhost:8080/api";
  const app = new App(url);
});

class App {
  constructor(url) {
    const thisObj = this;
    this.service = new ServiceProxy(url, () => {
      thisObj.onOpen()
    }, () => {
      thisObj.onClose()
    });
  }

  onOpen() {
    console.log("opened")
    this.service.login({username: 'admin', password: 'admin'})
      .then(() => {
        console.log('login succeeded')
      }, (err) => console.error('login failed', err))
      .then(() => {
        this.service.time(this.onTime)
      });
  }

  onTime(time) {
    $('#time').text(time)
  }

  onClose() {
    console.log("closed");
  }
}