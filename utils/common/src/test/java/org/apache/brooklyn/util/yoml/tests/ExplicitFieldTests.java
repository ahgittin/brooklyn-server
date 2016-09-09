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
package org.apache.brooklyn.util.yoml.tests;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.yoml.YomlSerializer;
import org.apache.brooklyn.util.yoml.serializers.AllFieldsExplicit;
import org.apache.brooklyn.util.yoml.serializers.ExplicitField;
import org.apache.brooklyn.util.yoml.tests.YomlBasicTests.Shape;
import org.apache.brooklyn.util.yoml.tests.YomlBasicTests.ShapeWithSize;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests that explicit fields can be set at the outer level in yaml. */
public class ExplicitFieldTests {

    public static YomlSerializer explicitFieldSerializer(String yaml) {
        return (YomlSerializer) YomlTestFixture.newInstance().read("{ fields: "+yaml+" }", "java:"+ExplicitField.class.getName()).lastReadResult;
    }

    protected static YomlTestFixture simpleExplicitFieldFixture() {
        return YomlTestFixture.newInstance().
            addType("shape", Shape.class, MutableList.of(explicitFieldSerializer("{ fieldName: name }")));
    }
    
    static String SIMPLE_IN_WITHOUT_TYPE = "{ name: diamond, fields: { color: black } }";
    static Shape SIMPLE_OUT = new Shape().name("diamond").color("black");
    
    static String SIMPLE_IN_NAME_ONLY_WITHOUT_TYPE = "{ name: diamond }";
    static Shape SIMPLE_OUT_NAME_ONLY = new Shape().name("diamond");
    
    @Test
    public void testReadExplicitField() {
        simpleExplicitFieldFixture().
        read( SIMPLE_IN_WITHOUT_TYPE, "shape" ).
        assertResult( SIMPLE_OUT );
    }
    @Test
    public void testWriteExplicitField() {
        simpleExplicitFieldFixture().
        write( SIMPLE_OUT, "shape" ).
        assertResult( SIMPLE_IN_WITHOUT_TYPE );
    }
    
    @Test
    public void testReadExplicitFieldNameOnly() {
        simpleExplicitFieldFixture().
        read( SIMPLE_IN_NAME_ONLY_WITHOUT_TYPE, "shape" ).
        assertResult( SIMPLE_OUT_NAME_ONLY );
    }
    @Test
    public void testWriteExplicitFieldNameOnly() {
        simpleExplicitFieldFixture().
        write( SIMPLE_OUT_NAME_ONLY, "shape" ).
        assertResult( SIMPLE_IN_NAME_ONLY_WITHOUT_TYPE );
    }

    static String SIMPLE_IN_WITH_TYPE = "{ type: shape, name: diamond, fields: { color: black } }";

    @Test
    public void testReadExplicitFieldNoExpectedType() {
        simpleExplicitFieldFixture().
        read( SIMPLE_IN_WITH_TYPE, null ).
        assertResult( SIMPLE_OUT);
    }
    @Test
    public void testWriteExplicitFieldNoExpectedType() {
        simpleExplicitFieldFixture().
        write( SIMPLE_OUT, null ).
        assertResult( SIMPLE_IN_WITH_TYPE );
    }

    protected static YomlTestFixture commonExplicitFieldFixtureKeyNameAlias() {
        return commonExplicitFieldFixtureKeyNameAlias("");
    }
    protected static YomlTestFixture commonExplicitFieldFixtureKeyNameAlias(String extra) {
        return YomlTestFixture.newInstance().
            addType("shape", Shape.class, MutableList.of(
                explicitFieldSerializer("{ fieldName: name, keyName: shape-name, alias: my-name"+extra+" }")));
    }

    static String COMMON_IN_KEY_NAME = "{ shape-name: diamond, fields: { color: black } }";
    static String COMMON_IN_ALIAS = "{ my-name: diamond, fields: { color: black } }";
    static Shape COMMON_OUT = new Shape().name("diamond").color("black");
    static String COMMON_IN_DEFAULT = "{ fields: { color: black } }";
    static Shape COMMON_OUT_DEFAULT = new Shape().name("bob").color("black");
    static String COMMON_IN_NO_NAME = "{ fields: { color: black } }";
    static Shape COMMON_OUT_NO_NAME = new Shape().color("black");

    @Test
    public void testCommonKeyName() {
        commonExplicitFieldFixtureKeyNameAlias().
        reading( COMMON_IN_KEY_NAME, "shape" ).
        writing( COMMON_OUT, "shape" ).
        doReadWriteAssertingJsonMatch();
    }

    @Test
    public void testCommonAlias() {
        commonExplicitFieldFixtureKeyNameAlias().
        read( COMMON_IN_ALIAS, "shape" ).assertResult(COMMON_OUT).
        write( COMMON_OUT, "shape" ).assertResult(COMMON_IN_KEY_NAME);
    }

    @Test
    public void testCommonDefault() {
        commonExplicitFieldFixtureKeyNameAlias(", defaultValue: { type: string, value: bob }").
        reading( COMMON_IN_DEFAULT, "shape" ).
        writing( COMMON_OUT_DEFAULT, "shape" ).
        doReadWriteAssertingJsonMatch();
    }

    @Test
    public void testNameNotRequired() {
        commonExplicitFieldFixtureKeyNameAlias().
        reading( COMMON_IN_NO_NAME, "shape" ).
        writing( COMMON_OUT_NO_NAME, "shape" ).
        doReadWriteAssertingJsonMatch();
    }

    @Test
    public void testNameRequired() {
        try {
            YomlTestFixture x = commonExplicitFieldFixtureKeyNameAlias(", constraint: required")
            .read( COMMON_IN_NO_NAME, "shape" );
            Asserts.shouldHaveFailedPreviously("Returned "+x.lastReadResult+" when should have thrown");
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, "name", "required");
        }
    }

    @Test
    public void testAliasConflictNiceError() {
        try {
            YomlTestFixture x = commonExplicitFieldFixtureKeyNameAlias().read( 
                "{ my-name: name-from-alias, shape-name: name-from-key }", "shape" );
            Asserts.shouldHaveFailedPreviously("Returned "+x.lastReadResult+" when should have thrown");
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, "name-from-alias", "my-name", "name-from-key");
        }
    }

    protected static YomlTestFixture extended0ExplicitFieldFixture(List<? extends YomlSerializer> extras) {
        return commonExplicitFieldFixtureKeyNameAlias(", defaultValue: { type: string, value: bob }").
            addType("shape-with-size", "{ type: \"java:"+ShapeWithSize.class.getName()+"\", interfaceTypes: [ shape ] }", 
                MutableList.copyOf(extras).append(explicitFieldSerializer("{ fieldName: size, alias: shape-size }")) );
    }
    
    protected static YomlTestFixture extended1ExplicitFieldFixture() {
        return extended0ExplicitFieldFixture( MutableList.of(
                explicitFieldSerializer("{ fieldName: name, keyName: shape-w-size-name }")) ); 
    }
    
    @Test
    public void testExplicitFieldSerializersAreCollected() {
        YomlTestFixture ytc = extended1ExplicitFieldFixture();
        Set<YomlSerializer> serializers = MutableSet.of();
        ytc.tr.collectSerializers("shape-with-size", serializers, MutableSet.<String>of());
        Assert.assertEquals(serializers.size(), 3, "Wrong serializers: "+serializers);
    }
    
    String EXTENDED_IN_1 = "{ type: shape-with-size, shape-w-size-name: diamond, size: 2, fields: { color: black } }";
    Object EXTENDED_OUT_1 = new ShapeWithSize().size(2).name("diamond").color("black");

    @Test
    public void testExtendedKeyNameIsUsed() {
        extended1ExplicitFieldFixture().
        reading( EXTENDED_IN_1, null ).
        writing( EXTENDED_OUT_1, "shape").
        doReadWriteAssertingJsonMatch();
    }

    @Test
    public void testInheritedAliasIsUsed() {
        String json = "{ type: shape-with-size, my-name: diamond, size: 2, fields: { color: black } }";
        extended1ExplicitFieldFixture().
        read( json, null ).assertResult( EXTENDED_OUT_1 ).
        write( EXTENDED_OUT_1, "shape-w-size" ).assertResult(EXTENDED_IN_1);
    }

    String EXTENDED_IN_ORIGINAL_KEYNAME = "{ type: shape-with-size, shape-name: diamond, size: 2, fields: { color: black } }";
    
    @Test
    public void testOverriddenKeyNameNotUsed() {
        try {
            YomlTestFixture x  = extended1ExplicitFieldFixture().read(EXTENDED_IN_ORIGINAL_KEYNAME, null);
            Asserts.shouldHaveFailedPreviously("Returned "+x.lastReadResult+" when should have thrown");
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, "shape-name", "diamond");
        }
    }

    String EXTENDED_TYPEDEF_NEW_ALIAS = "{ fieldName: name, alias: new-name }";
    
    @Test
    public void testInheritedKeyNameIsUsed() {
        extended0ExplicitFieldFixture( MutableList.of(
            explicitFieldSerializer(EXTENDED_TYPEDEF_NEW_ALIAS)) )
            .read(EXTENDED_IN_ORIGINAL_KEYNAME, null).assertResult(EXTENDED_OUT_1)
            .write(EXTENDED_OUT_1).assertResult(EXTENDED_IN_ORIGINAL_KEYNAME);
    }

    @Test
    public void testOverriddenAliasIsRecognised() {
        String json = "{ type: shape-with-size, new-name: diamond, size: 2, fields: { color: black } }";
        extended0ExplicitFieldFixture( MutableList.of(
            explicitFieldSerializer(EXTENDED_TYPEDEF_NEW_ALIAS)) )
            .read( json, null ).assertResult( EXTENDED_OUT_1 )
            .write( EXTENDED_OUT_1, "shape-w-size" ).assertResult(EXTENDED_IN_ORIGINAL_KEYNAME);
    }
    
    String EXTENDED_TYPEDEF_NEW_DEFAULT = "{ fieldName: name, defaultValue: { type: string, value: bob } }";
    Object EXTENDED_OUT_NEW_DEFAULT = new ShapeWithSize().size(2).name("bob").color("black");
    
    @Test
    public void testInheritedKeyNameIsUsedWithNewDefault() {
        String json = "{ size: 2, fields: { color: black } }";
        extended0ExplicitFieldFixture( MutableList.of(
            explicitFieldSerializer(EXTENDED_TYPEDEF_NEW_DEFAULT)) )
            .write(EXTENDED_OUT_NEW_DEFAULT, "shape-with-size").assertResult(json)
            .read(json, "shape-with-size").assertResult(EXTENDED_OUT_NEW_DEFAULT);
    }
    
    @Test
    public void testInheritedAliasIsNotUsedIfRestricted() {
        // same as testInheritedAliasIsUsed -- except fails because we say aliases-inherited: false
        String json = "{ type: shape-with-size, my-name: diamond, size: 2, fields: { color: black } }";
        try {
            YomlTestFixture x  = extended0ExplicitFieldFixture( MutableList.of(
                explicitFieldSerializer("{ fieldName: name, keyName: shape-w-size-name, aliasesInherited: false }")) )
                .read( json, null );
            Asserts.shouldHaveFailedPreviously("Returned "+x.lastReadResult+" when should have thrown");
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, "my-name", "diamond");
        }
    }

    @Test
    public void testFieldNameAsAlias() {
        String json = "{ type: shape-with-size, name: diamond, size: 2, fields: { color: black } }";
        extended0ExplicitFieldFixture( MutableList.of(
            explicitFieldSerializer("{ fieldName: name, keyName: shape-w-size-name }")) )
            .read( json, null ).assertResult( EXTENDED_OUT_1 )
            .write( EXTENDED_OUT_1 ).assertResult( EXTENDED_IN_1 );
    }

    @Test
    public void testFieldNameAsAliasExcludedWhenStrict() {
        String json = "{ type: shape-with-size, name: diamond, size: 2, fields: { color: black } }";
        try {
            YomlTestFixture x  = extended0ExplicitFieldFixture( MutableList.of(
                explicitFieldSerializer("{ fieldName: name, keyName: shape-w-size-name, aliasesStrict: true }")) )
                .read( json, null );
            Asserts.shouldHaveFailedPreviously("Returned "+x.lastReadResult+" when should have thrown");
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, "name", "diamond");
        }
    }

    String EXTENDED_IN_1_MANGLED = "{ type: shape-with-size, shapeWSize_Name: diamond, size: 2, fields: { color: black } }";
    
    @Test
    public void testFieldNameMangled() {
        extended0ExplicitFieldFixture( MutableList.of(
            explicitFieldSerializer("{ fieldName: name, keyName: shape-w-size-name }")) )
            .read( EXTENDED_IN_1_MANGLED, null ).assertResult( EXTENDED_OUT_1 )
            .write( EXTENDED_OUT_1 ).assertResult( EXTENDED_IN_1 );
    }

    @Test
    public void testFieldNameManglesExcludedWhenStrict() {
        try {
            YomlTestFixture x  = extended0ExplicitFieldFixture( MutableList.of(
                explicitFieldSerializer("{ fieldName: name, keyName: shape-w-size-name, aliasesStrict: true }")) )
                .read( EXTENDED_IN_1_MANGLED, null );
            Asserts.shouldHaveFailedPreviously("Returned "+x.lastReadResult+" when should have thrown");
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, "shapeWSize_Name", "diamond");
        }
    }

    static String SIMPLE_IN_ALL_FIELDS_EXPLICIT = "{ color: black, name: diamond }";
    @Test public void testAllFieldsExplicit() {
        YomlTestFixture y = YomlTestFixture.newInstance().
            addType("shape", Shape.class, MutableList.of(new AllFieldsExplicit()));
        
        y.read( SIMPLE_IN_ALL_FIELDS_EXPLICIT, "shape" ).assertResult( SIMPLE_OUT ).
        write( SIMPLE_OUT, "shape" ).assertResult( SIMPLE_IN_ALL_FIELDS_EXPLICIT );
    }
    
}