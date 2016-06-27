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
package org.apache.brooklyn.util.yorml.tests;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.javalang.Boxing;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;
import org.apache.brooklyn.util.yorml.Yorml;
import org.apache.brooklyn.util.yorml.YormlSerializer;
import org.apache.brooklyn.util.yorml.YormlTypeRegistry;

import com.google.common.collect.Iterables;

public class MockYormlTypeRegistry implements YormlTypeRegistry {

    static class MockRegisteredType {
        final String id;
        final String parentType;
        
        final Class<?> javaType;
        final List<YormlSerializer> serializers;
        final Object yamlDefinition;
        
        public MockRegisteredType(String id, String parentType, Class<?> javaType, List<YormlSerializer> serializers, Object yamlDefinition) {
            super();
            this.id = id;
            this.parentType = parentType;
            this.javaType = javaType;
            this.serializers = serializers;
            this.yamlDefinition = yamlDefinition;
        }
    }
    
    Map<String,MockRegisteredType> types = MutableMap.of();
    
    @Override
    public Object newInstance(String typeName, Yorml yorml) {
        MockRegisteredType type = types.get(typeName);
        if (type==null) {
            return null;
        }
        try {
            if (type.yamlDefinition!=null) {
                String parentTypeName = type.parentType;
                if (type.parentType==null && type.javaType!=null) parentTypeName = getDefaultTypeNameOfClass(type.javaType);
                return yorml.readFromYamlObject(type.yamlDefinition, parentTypeName);
            }
            Class<?> javaType = getJavaType(type, null); 
            if (javaType==null) {
                throw new IllegalStateException("Incomplete hierarchy for `"+typeName+"`");
            }
            return javaType.newInstance();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public Class<?> getJavaType(String typeName) {
        return getJavaType(types.get(typeName), typeName);
    }
    
    protected Class<?> getJavaType(MockRegisteredType registeredType, String typeName) {
        Class<?> result = null;
            
        if (result==null && registeredType!=null) result = registeredType.javaType;
        if (result==null && registeredType!=null) result = getJavaType(registeredType.parentType);
        
        if (result==null) result = Boxing.getPrimitiveType(typeName).orNull();
        if (result==null) result = Boxing.getPrimitiveType(typeName).orNull();
        if (result==null && "string".equals(typeName)) result = String.class;
        if (result==null && typeName.startsWith("java:")) {
            typeName = Strings.removeFromStart(typeName, "java:");
            try {
                // TODO use injected loader?
                result = Class.forName(typeName);
            } catch (ClassNotFoundException e) {
                // ignore, this isn't a java type
            }
        }
        return result;
    }
    
    /** simplest type def -- an alias for a java class */
    public void put(String typeName, Class<?> javaType) {
        put(typeName, javaType, null);
    }
    public void put(String typeName, Class<?> javaType, List<YormlSerializer> serializers) {
        types.put(typeName, new MockRegisteredType(typeName, "java:"+javaType.getName(), javaType, null, null));
    }
    
    /** takes a simplified yaml definition supporting a map with a key `type` and optionally other keys */
    public void put(String typeName, String yamlDefinition) {
        put(typeName, yamlDefinition, null);
    }
    public void put(String typeName, String yamlDefinition, List<YormlSerializer> serializers) {
        Object yamlObject = Iterables.getOnlyElement( Yamls.parseAll(yamlDefinition) );
        if (!(yamlObject instanceof Map)) throw new IllegalArgumentException("Mock only supports map definitions");
        Object type = ((Map<?,?>)yamlObject).get("type");
        if (!(type instanceof String)) throw new IllegalArgumentException("Mock requires key `type` with string value");
        ((Map<?,?>)yamlObject).remove("type");
        if (((Map<?,?>)yamlObject).isEmpty()) yamlObject = null;
        Class<?> javaType = getJavaType((String)type);
        if (javaType==null) throw new IllegalArgumentException("Mock cannot resolve parent type `"+type+"` in definition of `"+typeName+"`");
        types.put(typeName, new MockRegisteredType(typeName, (String)type, javaType, serializers, yamlObject));
    }

    @Override
    public String getTypeName(Object obj) {
        return getTypeNameOfClass(obj.getClass());
    }

    @Override
    public <T> String getTypeNameOfClass(Class<T> type) {
        for (Map.Entry<String,MockRegisteredType> t: types.entrySet()) {
            if (type.equals(t.getValue().javaType) && t.getValue().yamlDefinition==null) return t.getKey();
        }
        return getDefaultTypeNameOfClass(type);
    }
    
    protected <T> String getDefaultTypeNameOfClass(Class<T> type) {
        Maybe<String> primitive = Boxing.getPrimitiveName(type);
        if (primitive.isPresent()) return primitive.get();
        if (String.class.equals(type)) return "string";
        // TODO map and list?
        
        return "java:"+type.getName();
    }
}
