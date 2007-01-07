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
package org.apache.openejb.core.stateless;

import javax.naming.NameNotFoundException;

import org.apache.openejb.core.ivm.naming.EncReference;
import org.apache.openejb.core.ivm.naming.Reference;


public class StatelessEncReference extends EncReference {

    public StatelessEncReference(Reference ref) {
        super(ref);
    }

    public void checkOperation(byte operation) throws NameNotFoundException {
/*
        if( operation != Operations.OP_BUSINESS ){
            throw new NameNotFoundException("Operation Not Allowed");
        }        
*/
    }

}
