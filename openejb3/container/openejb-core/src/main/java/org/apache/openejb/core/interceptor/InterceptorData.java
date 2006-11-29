/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.core.interceptor;

/**
 * @version $Rev$ $Date$
 */
public class InterceptorData {
    private final String interceptorClass;
    private final String interceptorMethod;

    public InterceptorData(String interceptorClass, String interceptorMethod) {
        if (interceptorClass == null) throw new NullPointerException("interceptorClass is null");
        if (interceptorMethod == null) throw new NullPointerException("interceptorMethod is null");
        this.interceptorClass = interceptorClass;
        this.interceptorMethod = interceptorMethod;
    }

    public String getInterceptorClass() {
        return interceptorClass;
    }

    public String getInterceptorMethod() {
        return interceptorMethod;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InterceptorData that = (InterceptorData) o;
        return interceptorClass.equals(that.interceptorClass) &&
                interceptorMethod.equals(that.interceptorMethod);

    }

    public int hashCode() {
        int result;
        result = interceptorClass.hashCode();
        result = 29 * result + interceptorMethod.hashCode();
        return result;
    }

    public String toString() {
        return interceptorClass + "." + interceptorMethod;
    }
}
