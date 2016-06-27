/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.yorml;

import java.util.Map;

import org.apache.brooklyn.util.yorml.internal.YormlConverter;
import org.apache.brooklyn.util.yorml.serializers.YormlSerializerComposition;

/** Describes a serializer which can be used by {@link YormlConverter}.
 * <p>
 * Instances of this class should be thread-safe for use with simultaneous conversions. 
 * Often implementations will extend {@link YormlSerializerComposition} and which stores
 * per-conversion data in a per-method-invocation object. 
 */
public interface YormlSerializer {

    /**
     * modifies yaml object and/or java object and/or blackboard as appropriate,
     * when trying to build a java object from a yaml object,
     * returning true if it did anything (and so should restart the cycle).
     * implementations must NOT return true indefinitely if passed the same instances!
     */ 
    public YormlContinuation read(YormlContextForRead context, YormlConverter converter, Map<Object,Object> blackboard);

    /**
     * modifies java object and/or yaml object and/or blackboard as appropriate,
     * when trying to build a yaml object from a java object,
     * returning true if it did anything (and so should restart the cycle).
     * implementations must NOT return true indefinitely if passed the same instances!
     */   
    public YormlContinuation write(YormlContextForWrite context, YormlConverter converter, Map<Object,Object> blackboard);

    /**
     * generates human-readable schema for a type using this schema.
     */
    public String document(String type, YormlConverter converter);

}
