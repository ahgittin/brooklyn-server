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

import java.util.List;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.yorml.internal.YormlConfig;
import org.apache.brooklyn.util.yorml.internal.YormlConverter;
import org.apache.brooklyn.util.yorml.serializers.FieldsInMapUnderFields;
import org.apache.brooklyn.util.yorml.serializers.InstantiateTypeFromRegistry;
import org.apache.brooklyn.util.yorml.serializers.InstantiateTypeList;
import org.apache.brooklyn.util.yorml.serializers.InstantiateTypeMap;
import org.apache.brooklyn.util.yorml.serializers.InstantiateTypePrimitive;


public class Yorml {

    final YormlConfig config;
    
    private Yorml(YormlConfig config) { this.config = config; }
    
    public static Yorml newInstance(YormlConfig config) {
        return new Yorml(config);
    }
    
    public static Yorml newInstance(YormlTypeRegistry typeRegistry) {
        return newInstance(typeRegistry, MutableList.<YormlSerializer>of(
            new FieldsInMapUnderFields(),
            new InstantiateTypePrimitive(),
            new InstantiateTypeList(),
            new InstantiateTypeMap(),
            new InstantiateTypeFromRegistry() ));
    }
    
    private static Yorml newInstance(YormlTypeRegistry typeRegistry, List<YormlSerializer> serializers) {
        YormlConfig config = new YormlConfig();
        config.typeRegistry = typeRegistry;
        config.serializersPost.addAll(serializers);
        
        return new Yorml(config);
    }
    
    public YormlConfig getConfig() {
        return config;
    }
    
    public Object read(String yaml) {
        return read(yaml, null);
    }
    public Object read(String yaml, String expectedType) {
        return readFromYamlObject(new org.yaml.snakeyaml.Yaml().load(yaml), expectedType);
    }
    public Object readFromYamlObject(Object yamlObject, String type) {
        return new YormlConverter(config).read( new YormlContextForRead(yamlObject, "", type) );
    }

    public Object write(Object java) {
        return write(java, null);
    }
    public Object write(Object java, String expectedType) {
        return new YormlConverter(config).write( new YormlContextForWrite(java, "", expectedType) );
    }
    
//    public <T> T read(String yaml, Class<T> type) {
//    }
//    public <T> T read(String yaml, TypeToken<T> type) {
//    }
    
}
