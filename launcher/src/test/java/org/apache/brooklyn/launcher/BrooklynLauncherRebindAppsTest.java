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
package org.apache.brooklyn.launcher;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

public class BrooklynLauncherRebindAppsTest extends AbstractBrooklynLauncherRebindTest {

    @Test
    public void testRebindGetsApps() {
        BrooklynLauncher origLauncher = newLauncherForTests();
        origLauncher.start();
        TestApplication origApp = origLauncher.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        origLauncher.terminate();

        BrooklynLauncher newLauncher = newLauncherForTests();
        newLauncher.start();
        assertEquals(Iterables.getOnlyElement(newLauncher.getManagementContext().getApplications()).getId(), origApp.getId());
    }
}
