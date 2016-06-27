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
package org.apache.brooklyn.util.yorml.serializers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yorml.YormlContext;
import org.apache.brooklyn.util.yorml.YormlException;
import org.apache.brooklyn.util.yorml.YormlRequirement;
import org.apache.brooklyn.util.yorml.YormlSerializer;

public class ExplicitFieldsBlackboard implements YormlRequirement {

    public static final String KEY = ExplicitFieldsBlackboard.class.getCanonicalName();
        
    public static ExplicitFieldsBlackboard get(Map<Object,Object> blackboard) {
        Object v = blackboard.get(KEY);
        if (v==null) {
            v = new ExplicitFieldsBlackboard();
            blackboard.put(KEY, v);
        }
        return (ExplicitFieldsBlackboard) v;
    }
    
    private final Set<String> fieldsDone = MutableSet.of();
    private final Map<String,Boolean> fieldsRequired = MutableMap.of();
    private final Map<String,YormlSerializer> defaultValueForFieldComesFromSerializer = MutableMap.of();
    private final Map<String,Object> defaultValueOfField = MutableMap.of();
    
    @Override
    public void checkCompletion(YormlContext context) {
        List<String> incompleteRequiredFields = MutableList.of();
        for (Map.Entry<String,Boolean> fieldRequired: fieldsRequired.entrySet()) {
            if (fieldRequired.getValue() && !fieldsDone.contains(fieldRequired.getKey())) {
                incompleteRequiredFields.add(fieldRequired.getKey());
            }
        }
        if (!incompleteRequiredFields.isEmpty()) {
            throw new YormlException("Missing one or more explicitly required fields: "+Strings.join(incompleteRequiredFields, ", "), context);
        }
    }
    public boolean isRequired(String fieldName) {
        return Maybe.ofDisallowingNull(fieldsRequired.get(fieldName)).or(false);
    }

    public boolean isFieldDone(String fieldName) {
        return fieldsDone.contains(fieldName);
    }
    public void setFieldDone(String fieldName) {
        fieldsDone.add(fieldName);
    }

    public void setRequiredIfUnset(String fieldName, Boolean required) {
        if (required==null) return;
        if (fieldsRequired.get(fieldName)!=null) return;
        fieldsRequired.put(fieldName, required);
    }

    public void setUseDefaultFrom(String fieldName, YormlSerializer explicitField, Object defaultValue) {
        defaultValueForFieldComesFromSerializer.put(fieldName, explicitField);
        defaultValueOfField.put(fieldName, defaultValue);
    }
    public boolean shouldUseDefaultFrom(String fieldName, YormlSerializer explicitField) {
        return explicitField.equals(defaultValueForFieldComesFromSerializer.get(fieldName));
    }
    public Maybe<Object> getDefault(String fieldName) {
        if (!defaultValueOfField.containsKey(fieldName)) return Maybe.absent("no default");
        return Maybe.of(defaultValueOfField.get(fieldName));
    }
}
