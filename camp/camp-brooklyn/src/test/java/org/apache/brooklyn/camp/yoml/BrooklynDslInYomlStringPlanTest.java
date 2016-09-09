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
package org.apache.brooklyn.camp.yoml;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.typereg.BasicBrooklynTypeRegistry;
import org.apache.brooklyn.util.yoml.annotations.YomlAllFieldsAtTopLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

public class BrooklynDslInYomlStringPlanTest extends AbstractYamlTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BrooklynDslInYomlStringPlanTest.class);
    
    private BasicBrooklynTypeRegistry registry() {
        return (BasicBrooklynTypeRegistry) mgmt().getTypeRegistry();
    }
    
    private void add(RegisteredType type) {
        add(type, false);
    }
    private void add(RegisteredType type, boolean canForce) {
        registry().addToLocalUnpersistedTypeRegistry(type, canForce);
    }
    
    @YomlAllFieldsAtTopLevel
    public static class ItemA {
        String name;
        @Override public String toString() { return super.toString()+"[name="+name+"]"; }
    }
    
    private final static RegisteredType SAMPLE_TYPE_BASE = BrooklynYomlTypeRegistry.newYomlRegisteredType(
        RegisteredTypeKind.BEAN, "item-base", "1", ItemA.class);

    private final static RegisteredType SAMPLE_TYPE_TEST = BrooklynYomlTypeRegistry.newYomlRegisteredType(
        RegisteredTypeKind.BEAN, "item-w-dsl", "1", "{ type: item-base, name: '$brooklyn:self().attributeWhenReady(\"test.sensor\")' }",
        ItemA.class, null, null); 

    @Test
    public void testYomlParserRespectsDsl() throws Exception {
        add(SAMPLE_TYPE_BASE);
        add(SAMPLE_TYPE_TEST);
        
        String yaml = Joiner.on("\n").join(
                "services:",
                "- type: org.apache.brooklyn.core.test.entity.TestEntity",
                "  brooklyn.config:",
                "    test.obj:",
                "      $brooklyn:object-yoml: item-w-dsl");

        Entity app = createStartWaitAndLogApplication(yaml);
        Entity entity = Iterables.getOnlyElement( app.getChildren() );
        
        entity.sensors().set(Sensors.newStringSensor("test.sensor"), "bob");
        Object obj = entity.config().get(ConfigKeys.newConfigKey(Object.class, "test.obj"));
        Assert.assertEquals(((ItemA)obj).name, "bob");
    }

}