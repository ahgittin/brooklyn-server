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
package org.apache.brooklyn.entity.machine;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class MachineEntityRebindTest extends RebindTestFixtureWithApp {

    @Test
    public void testRebindToMachineEntity() throws Exception {
        SshMachineLocation loc = mgmt().getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost")
                .configure(SshMachineLocation.SSH_TOOL_CLASS, RecordingSshTool.class.getName()));
        MachineEntity machine = origApp.createAndManageChild(EntitySpec.create(MachineEntity.class));
        origApp.start(ImmutableList.of(loc));
        EntityAsserts.assertAttributeEqualsEventually(machine, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        rebind();
        
        Entity machine2 = mgmt().getEntityManager().getEntity(machine.getId());
        EntityAsserts.assertAttributeEqualsEventually(machine2, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }
}
