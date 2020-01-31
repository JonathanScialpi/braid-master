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

const Proxy = require('braid-client').Proxy;

let corda = new Proxy({
  url: "https://localhost:8080/api/", credentials: {
    username: 'banka', password: 'password'
  }
}, onOpen, onClose, onError, {strictSSL: false});

function onOpen() {
  // console.log(corda.network.getNodeByLegalName.docs());
  printMyInfo(corda)
    .then(() => invokeEchoFlow())
    .then(() => getNotaries())
    .then(() => registerForCashNotifications())
    .then(() => issueCash('$100', 'ref01'))
    .then(() => issueCash('£200', 'ref02'))
    .then(() => {
      console.log('finished');
      process.exit(0);
    }, err => {
      console.error('failed', err);
      process.exit(1);
    });
}

function onClose() {
  console.log("disconnected");
}

function onError(err) {
  console.error("error", err);
}

function printMyInfo() {
  return corda.network.myNodeInfo()
    .then(ni => {
      console.log("\n*** printMyInfo ***\n");
      console.log('network info for node: ', ni);
    });
}

function invokeEchoFlow() {
  "use strict";
  return corda.flows.echoFlow("Echo Message")
    .then(result => {
      console.log('Echo response:', result);
    });
}

function getNotaries() {
  return corda.network.notaryIdentities()
    .then(notaries => {
      console.log("\n*** getNotaries ***\n");
      console.log('notaries', notaries);
      notary = notaries[0];
    })
}

function registerForCashNotifications() {
  console.log('\n*** registerForCashNotifications ***');
  return corda.myService.listenForCashUpdates(onCashNotification)
}

function onCashNotification(update) {
  console.log('\n*** cash notification: ***\n');
  console.log(JSON.stringify(update, null, 2));
}

function issueCash(amount, ref) {
  console.log('\n*** issueCash', amount, ' ***');
  return corda.flows.issueCash(amount, ref, notary)
    .then(result => console.log('\n*** issueCash - txid for ref', ref, result), err => console.error(err));
}