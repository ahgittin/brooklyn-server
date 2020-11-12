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
package org.apache.brooklyn.location.winrm;

import com.google.common.base.Function;
import org.apache.brooklyn.util.core.task.ssh.internal.AbstractSshExecTaskFactory;
import org.apache.brooklyn.util.core.task.ssh.internal.PlainSshExecTaskFactory;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;

import java.util.List;

public class PlainWinRmExecTaskFactory<RET> extends AbstractSshExecTaskFactory<PlainSshExecTaskFactory<RET>,RET> {

    /** constructor where machine will be added later */
    public PlainWinRmExecTaskFactory(String ...commands) {
        super(commands);
    }

    /** convenience constructor to supply machine immediately */
    public PlainWinRmExecTaskFactory(WinRmMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }

    /** Constructor where machine will be added later */
    public PlainWinRmExecTaskFactory(List<String> commands) {
        this(commands.toArray(new String[commands.size()]));
    }

    /** Convenience constructor to supply machine immediately */
    public PlainWinRmExecTaskFactory(WinRmMachineLocation machine, List<String> commands) {
        this(machine, commands.toArray(new String[commands.size()]));
    }

    @Override
    public <T2> PlainWinRmExecTaskFactory<T2> returning(ScriptReturnType type) {
        return (PlainWinRmExecTaskFactory<T2>) super.<T2>returning(type);
    }

    @Override
    public <RET2> PlainWinRmExecTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
        return (PlainWinRmExecTaskFactory<RET2>) super.returning(resultTransformation);
    }

    @Override
    public PlainWinRmExecTaskFactory<Boolean> returningIsExitCodeZero() {
        return (PlainWinRmExecTaskFactory<Boolean>) super.returningIsExitCodeZero();
    }

    @Override
    public PlainWinRmExecTaskFactory<String> requiringZeroAndReturningStdout() {
        return (PlainWinRmExecTaskFactory<String>) super.requiringZeroAndReturningStdout();
    }
}


