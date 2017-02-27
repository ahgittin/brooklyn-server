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

import java.util.Collection;
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
import org.apache.brooklyn.util.yorml.serializers.ExplicitField.FieldConstraint;

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
    
    private final Map<String,String> keyNames = MutableMap.of();
    private final Map<String,Boolean> aliasesInheriteds = MutableMap.of();
    private final Map<String,Boolean> aliasesStricts = MutableMap.of();
    private final Map<String,Set<String>> aliases = MutableMap.of();
    private final Set<String> fieldsDone = MutableSet.of();
    private final Map<String,FieldConstraint> fieldsConstraints = MutableMap.of();
    private final Map<String,YormlSerializer> defaultValueForFieldComesFromSerializer = MutableMap.of();
    private final Map<String,Object> defaultValueOfField = MutableMap.of();
    
    public String getKeyName(String fieldName) {
        return Maybe.ofDisallowingNull(keyNames.get(fieldName)).orNull();
    }
    public void setKeyNameIfUnset(String fieldName, String keyName) {
        if (keyName==null) return;
        if (keyNames.get(fieldName)!=null) return;
        keyNames.put(fieldName, keyName);
    }
    public void setAliasesInheritedIfUnset(String fieldName, Boolean aliasesInherited) {
        if (aliasesInherited==null) return;
        if (aliasesInheriteds.get(fieldName)!=null) return;
        aliasesInheriteds.put(fieldName, aliasesInherited);
    }
    public boolean isAliasesStrict(String fieldName) {
        return Boolean.TRUE.equals(aliasesStricts.get(fieldName));
    }
    public void setAliasesStrictIfUnset(String fieldName, Boolean aliasesStrict) {
        if (aliasesStrict==null) return;
        if (aliasesStricts.get(fieldName)!=null) return;
        aliasesStricts.put(fieldName, aliasesStrict);
    }
    public void addAliasIfNotDisinherited(String fieldName, String alias) {
        addAliasesIfNotDisinherited(fieldName, MutableList.of(alias));
    }
    public void addAliasesIfNotDisinherited(String fieldName, List<String> aliases) {
        if (Boolean.FALSE.equals(aliasesInheriteds.get(fieldName))) {
            // no longer heritable
            return;
        }
        Set<String> aa = this.aliases.get(fieldName);
        if (aa==null) {
            aa = MutableSet.of();
            this.aliases.put(fieldName, aa);
        }
        if (aliases==null) return;
        for (String alias: aliases) aa.add(alias);
    }
    public Collection<? extends String> getAliases(String fieldName) {
        Set<String> aa = this.aliases.get(fieldName);
        if (aa==null) return MutableSet.of();
        return aa;
    }

    public Maybe<FieldConstraint> getConstraint(String fieldName) {
        return Maybe.ofDisallowingNull(fieldsConstraints.get(fieldName));
    }
    public void setConstraintIfUnset(String fieldName, FieldConstraint constraint) {
        if (constraint==null) return;
        if (fieldsConstraints.get(fieldName)!=null) return;
        fieldsConstraints.put(fieldName, constraint);
    }
    @Override
    public void checkCompletion(YormlContext context) {
        List<String> incompleteRequiredFields = MutableList.of();
        for (Map.Entry<String,FieldConstraint> fieldConstraint: fieldsConstraints.entrySet()) {
            FieldConstraint v = fieldConstraint.getValue();
            if (v!=null && FieldConstraint.REQUIRED==v && !fieldsDone.contains(fieldConstraint.getKey())) {
                incompleteRequiredFields.add(fieldConstraint.getKey());
            }
        }
        if (!incompleteRequiredFields.isEmpty()) {
            throw new YormlException("Missing one or more explicitly required fields: "+Strings.join(incompleteRequiredFields, ", "), context);
        }
    }

    public boolean isFieldDone(String fieldName) {
        return fieldsDone.contains(fieldName);
    }
    public void setFieldDone(String fieldName) {
        fieldsDone.add(fieldName);
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