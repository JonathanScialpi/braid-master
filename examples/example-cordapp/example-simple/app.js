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
import {Proxy} from 'braid-client';

console.log('demo started');

export const corda = new Proxy({
  url: 'https://localhost:8080/api/', credentials: {
    username: 'banka', password: 'password'
  }
}, onOpen, onClose, onError, {strictSSL: false});

let notary;

function onOpen() {
  console.log('opened')
  printMyInfo(corda)
    .then(() => getNotaries())
    .then(() => registerForCashNotifications())
    .then(() => issueCash('$100', 'ref01'))
    .then(() => issueCash('$200', 'ref02'))
    .then(() => console.log('finished'), err => console.error('failed', err));
}

function onClose() {
  console.log('closed');
}

function onError(e) {
  console.error('failed with error;', e);
}

function printMyInfo() {
  return corda.network.myNodeInfo()
    .then(ni => {
      console.log('network info for node: ', ni);
    });
}

function getNotaries() {
  return corda.network.notaryIdentities()
    .then(notaries => {
      console.log('notaries', notaries);
      notary = notaries[0];
    })
}

function registerForCashNotifications() {
  console.log('registered for cash notifications')
  return corda.myService.listenForCashUpdates(onCashNotification)
}

function onCashNotification(update) {
  console.log('cash notification:', update);
}

function issueCash(amount, ref) {
  console.log('issuing', amount)
  return corda.flows.issueCash(amount, ref, notary)
    .then(result => console.log('txid for ref', ref, result), err => console.error(err));
}