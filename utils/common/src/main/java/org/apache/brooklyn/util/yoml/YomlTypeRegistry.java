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
package org.apache.brooklyn.util.yoml;

import org.apache.brooklyn.util.guava.Maybe;

public interface YomlTypeRegistry {

    Object newInstance(String type, Yoml yoml);
    
    /** Absent if unknown type; throws if type is ill-defined or incomplete. */
    Maybe<Object> newInstanceMaybe(String type, Yoml yoml);
    
    /** Returns the most-specific Java type implied by the given type in the registry,
     * or a maybe wrapping any explanatory error if the type is not available in the registry.
     * <p>
     * This is needed so that the right deserialization strategies can be applied for
     * things like collections and enums.
     */
    Maybe<Class<?>> getJavaTypeMaybe(String typeName);

    /** Return the best known type name to describe the given java instance */
    String getTypeName(Object obj);
    /** Return the type name to describe the given java class */ 
    <T> String getTypeNameOfClass(Class<T> type);

    /** Return custom serializers that shoud be used when deserializing something of the given type,
     * typically also looking at serializers for its supertypes */
    Iterable<YomlSerializer> getSerializersForType(String typeName);
    
}