/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.worker.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.remote.AssociatedEvent;
import org.apache.pekko.remote.AssociationErrorEvent;
import org.apache.pekko.remote.DisassociatedEvent;

public class RemoteEventActor extends AbstractActor {

    private static final Logger logger = LoggerFactory.getLogger(RemoteEventActor.class);

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AssociationErrorEvent.class, aee ->
                        logger.info(aee.getLocalAddress() + "-->" + aee.getRemoteAddress() + ": " + aee.getCause()))
                .match(AssociatedEvent.class, ae ->
                        logger.info(ae.getLocalAddress() + "-->" + ae.getRemoteAddress() + " associated"))
                .match(DisassociatedEvent.class, de ->
                        logger.info(de.getLocalAddress() + "-->" + de.getRemoteAddress() + " disassociated"))
                .matchAny(this::unhandled)
                .build();
    }
}
