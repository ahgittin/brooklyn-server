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
package org.apache.brooklyn.util.yorml.internal;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.javalang.Boxing;
import org.apache.brooklyn.util.javalang.FieldOrderings;
import org.apache.brooklyn.util.javalang.ReflectionPredicates;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;

public class YormlUtils {

    /** true iff k1 and k2 are case-insensitively equal after removing all - and _.
     * Note that the definition of mangling may change.
     * TODO it should be stricter so that "ab" and "a-b" don't match but "aB" and "a-b" and "a_b" do */
    @Beta
    public static boolean mangleable(String k1, String k2) {
        if (k1==null || k2==null) return k1==k2;
        k1 = Strings.replaceAllNonRegex(k1, "-", "");
        k1 = Strings.replaceAllNonRegex(k1, "_", "");
        k2 = Strings.replaceAllNonRegex(k2, "-", "");
        k2 = Strings.replaceAllNonRegex(k2, "_", "");
        return k1.toLowerCase().equals(k2.toLowerCase());
    }

    /** type marker that value can be kept in its as-read form */
    public final static String TYPE_JSON = "json";
    
    public final static String TYPE_STRING = "string"; 
    public final static String TYPE_OBJECT = "object"; 
    public final static String TYPE_MAP = "map"; 
    public final static String TYPE_LIST = "list";
    public final static String TYPE_SET = "set";

    public final static class JsonMarker {
        public static final String TYPE = TYPE_JSON;

        /** true IFF o is a json primitive or map/iterable consisting of pure json items,
         * with the additional constraint that map keys must be strings */
        public static boolean isPureJson(Object o) {
            if (o==null || Boxing.isPrimitiveOrBoxedObject(o)) return true;
            if (o instanceof String) return true;
            if (o instanceof Iterable) {
                for (Object oi: ((Iterable<?>)o)) {
                    if (!isPureJson(oi)) return false;
                }
                return true;
            }
            if (o instanceof Map) {
                for (Map.Entry<?,?> oi: ((Map<?,?>)o).entrySet()) {
                    if (!(oi.getKey() instanceof String)) return false;
                    if (!isPureJson(oi.getValue())) return false;
                }
                return true;
            }
            return false;
        } 
    }

    public static class GenericsParse {
        public String warning;
        public boolean isGeneric = false;
        public String baseType;
        public List<String> subTypes = MutableList.of();
        
        public GenericsParse(String type) {
            if (type==null) return;
            
            baseType = type.trim();
            int genericStart = baseType.indexOf('<');
            if (genericStart > 0) {
                isGeneric = true;
                
                if (!parse(baseType.substring(genericStart))) {
                    warning = "Invalid generic type "+baseType;
                    return;
                }
                
                baseType = baseType.substring(0, genericStart);
            }
        }

        private boolean parse(String s) {
            int depth = 0;
            boolean inWord = false;
            int lastWordStart = -1;
            for (int i=0; i<s.length(); i++) {
                char c = s.charAt(i);
                if (c=='<') { depth++; continue; }
                if (Character.isWhitespace(c)) continue;
                if (c==',' || c=='>') {
                    if (c==',' && depth==0) return false;
                    if (c=='>') { depth--; }
                    if (depth>1) continue;
                    // depth 1 word end, either due to , or due to >
                    if (c==',' && !inWord) return false;
                    subTypes.add(s.substring(lastWordStart, i).trim());
                    inWord = false;
                    continue;
                }
                if (!inWord) {
                    if (depth!=1) return false;
                    inWord = true;
                    lastWordStart = i;
                }
            }
            // finished. expect depth 0 and not in word
            return depth==0 && !inWord;
        }

        public boolean isGeneric() { return isGeneric; }
        public int subTypeCount() { return subTypes.size(); }
    }

    public static <T> List<String> getAllNonTransientNonStaticFieldNames(Class<T> type, T optionalInstanceToRequireNonNullFieldValue) {
        List<String> result = MutableList.of();
        List<Field> fields = Reflections.findFields(type, 
            null,
            FieldOrderings.ALPHABETICAL_FIELD_THEN_SUB_BEST_FIRST);
        Field lastF = null;
        for (Field f: fields) {
            if (ReflectionPredicates.IS_FIELD_NON_TRANSIENT.apply(f) && ReflectionPredicates.IS_FIELD_NON_STATIC.apply(f)) {
                if (optionalInstanceToRequireNonNullFieldValue==null || 
                        Reflections.getFieldValueMaybe(optionalInstanceToRequireNonNullFieldValue, f).isPresentAndNonNull()) {
                    String name = f.getName();
                    if (lastF!=null && lastF.getName().equals(f.getName())) {
                        // if field is shadowed use FQN
                        name = f.getDeclaringClass().getCanonicalName()+"."+name;
                    }
                    result.add(name);
                }
            }
            lastF = f;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getAllNonTransientNonStaticFieldNamesUntyped(Class<?> type, Object optionalInstanceToRequireNonNullFieldValue) {
        return getAllNonTransientNonStaticFieldNames((Class<Object>)type, optionalInstanceToRequireNonNullFieldValue);
    }
}
