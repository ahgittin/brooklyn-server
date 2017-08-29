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
package org.apache.brooklyn.enricher.stock;

import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.util.collections.MutableList;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

public class MathAggregatorFunctionsTest {

    @SuppressWarnings("serial")
    private final TypeToken<Double> doubleTypeToken = new TypeToken<Double>() {};
    
    @Test
    public void testValueIfNone() throws Exception {
        List<Function<Collection<? extends Number>, Integer>> funcs = new ArrayList<>();
        funcs.add(MathAggregatorFunctions.computingSum(null, 999, Integer.class));
        funcs.add(MathAggregatorFunctions.computingAverage(null, 999, Integer.class));
        funcs.add(MathAggregatorFunctions.computingMin(null, 999, Integer.class));
        funcs.add(MathAggregatorFunctions.computingMax(null, 999, Integer.class));
        
        for (Function<Collection<? extends Number>, Integer> func : funcs) {
            assertEquals(func.apply(ImmutableList.<Number>of()), (Integer)999);
        }
    }
    
    @Test
    public void testValueIfNull() throws Exception {
        List<Function<Collection<? extends Number>, Integer>> funcs = new ArrayList<>();
        funcs.add(MathAggregatorFunctions.computingSum(999, null, Integer.class));
        funcs.add(MathAggregatorFunctions.computingAverage(999, null, Integer.class));
        funcs.add(MathAggregatorFunctions.computingMin(999, null, Integer.class));
        funcs.add(MathAggregatorFunctions.computingMax(999, null, Integer.class));
        
        for (Function<Collection<? extends Number>, Integer> func : funcs) {
            assertEquals(func.apply(MutableList.<Number>of(null)), (Integer)999);
        }
    }
    
    @Test
    public void testCastValue() throws Exception {
        List<Function<Collection<? extends Number>, Double>> funcs = new ArrayList<>();
        funcs.add(MathAggregatorFunctions.computingSum(999, null, Double.class));
        funcs.add(MathAggregatorFunctions.computingAverage(999, null, Double.class));
        funcs.add(MathAggregatorFunctions.computingMin(999, null, Double.class));
        funcs.add(MathAggregatorFunctions.computingMax(999, null, Double.class));
        
        for (Function<Collection<? extends Number>, Double> func : funcs) {
            assertEquals(func.apply(MutableList.<Number>of(null)), (Double)999d);
        }
    }
    
    @Test
    public void testCastValueWithTypeToken() throws Exception {
        List<Function<Collection<? extends Number>, Double>> funcs = new ArrayList<>();
        funcs.add(MathAggregatorFunctions.computingSum(999, null, doubleTypeToken));
        funcs.add(MathAggregatorFunctions.computingAverage(999, null, doubleTypeToken));
        funcs.add(MathAggregatorFunctions.computingMin(999, null, doubleTypeToken));
        funcs.add(MathAggregatorFunctions.computingMax(999, null, doubleTypeToken));
        
        for (Function<Collection<? extends Number>, Double> func : funcs) {
            assertEquals(func.apply(MutableList.<Number>of(null)), (Double)999d);
        }
    }
    
    @Test
    public void testSum() throws Exception {
        Function<Collection<? extends Number>, Integer> func = MathAggregatorFunctions.computingSum(null, null, Integer.class);
        assertEquals(func.apply(MutableList.<Number>of(1, 2, 4)), (Integer)7);
        assertEquals(func.apply(MutableList.<Number>of(1, null, 4)), (Integer)5);
    }
    
    @Test
    public void testAverage() throws Exception {
        Function<Collection<? extends Number>, Integer> func = MathAggregatorFunctions.computingAverage(null, null, Integer.class);
        assertEquals(func.apply(MutableList.<Number>of(1, 3, 5)), (Integer)3);
        assertEquals(func.apply(MutableList.<Number>of(1, null, 3)), (Integer)2);
    }
    
    @Test
    public void testMin() throws Exception {
        Function<Collection<? extends Number>, Integer> func = MathAggregatorFunctions.computingMin(null, null, Integer.class);
        assertEquals(func.apply(MutableList.<Number>of(1, 3, 5)), (Integer)1);
        assertEquals(func.apply(MutableList.<Number>of(3, null, 1)), (Integer)1);
    }
    
    @Test
    public void testMax() throws Exception {
        Function<Collection<? extends Number>, Integer> func = MathAggregatorFunctions.computingMax(null, null, Integer.class);
        assertEquals(func.apply(MutableList.<Number>of(1, 3, 5)), (Integer)5);
        assertEquals(func.apply(MutableList.<Number>of(3, null, 1)), (Integer)3);
    }
}
