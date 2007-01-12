/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.core.stateful;

import java.io.Serializable;
import javax.transaction.Transaction;

public class BeanEntry implements Serializable {
    private static final long serialVersionUID = 5940667199866151048L;

    protected final Object bean;
    protected final Object primaryKey;
    protected boolean inQueue = false;
    private long timeStamp;
    private long timeOutInterval;
    protected transient Transaction beanTransaction;

    protected BeanEntry(Object beanInstance, Object primKey, long timeOut) {
        bean = beanInstance;
        primaryKey = primKey;
        beanTransaction = null;
        timeStamp = System.currentTimeMillis();
        timeOutInterval = timeOut;
    }

    protected boolean isTimedOut() {
        if (timeOutInterval == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        return (now - timeStamp) > timeOutInterval;
    }

    protected void resetTimeOut() {
        if (timeOutInterval > 0) {
            timeStamp = System.currentTimeMillis();
        }
    }
}         
