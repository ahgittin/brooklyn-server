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
package org.apache.brooklyn.rest.resources;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags.WrappedStream;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.rest.api.ActivityApi;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.rest.transform.TaskTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ActivityResource extends AbstractBrooklynRestResource implements ActivityApi {

    @Override
    public TaskSummary get(String taskId) {
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null) {
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        }
        checkEntityEntitled(t);

        return TaskTransformer.fromTask(ui.getBaseUriBuilder()).apply(t);
    }

    @Override
    public Map<String, TaskSummary> getAllChildrenAsMap(final String taskId) {
        final Task<?> parentTask = mgmt().getExecutionManager().getTask(taskId);
        if (parentTask == null) {
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        }
        checkEntityEntitled(parentTask);
        return getAllDescendantTasks(parentTask);
    }

    private LinkedHashMap<String, TaskSummary> getAllDescendantTasks(final Task<?> parentTask) {
        final LinkedHashMap<String, TaskSummary> result = Maps.newLinkedHashMap();
        if (!(parentTask instanceof HasTaskChildren)) {
            return result;
        }
        for (final Task<?> childTask : ((HasTaskChildren) parentTask).getChildren()) {
            result.put(childTask.getId(), TaskTransformer.fromTask(ui.getBaseUriBuilder()).apply(childTask));
            result.putAll(getAllDescendantTasks(childTask));
        }
        return result;
    }


    @Override
    public List<TaskSummary> children(String taskId, Boolean includeBackground) {
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null) {
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        }
        checkEntityEntitled(t);

        Set<TaskSummary> result = MutableSet.copyOf(getSubTaskChildren(t));
        if (Boolean.TRUE.equals(includeBackground)) {
            result.addAll(getBackgroundedChildren(t));
        }
        return MutableList.copyOf(result);
    }

    private Collection<? extends TaskSummary> getBackgroundedChildren(Task<?> t) {
        Entity entity = BrooklynTaskTags.getContextEntity(t);
        List<TaskSummary> result = MutableList.of();
        if (entity!=null) {
            Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt().getExecutionManager(), entity);
            for (Task<?> ti: tasks) {
                if (t.equals(ti.getSubmittedByTask())) {
                    result.add(TaskTransformer.fromTask(ui.getBaseUriBuilder()).apply(ti));
                }
            }
        }
        return result;
    }

    private List<TaskSummary> getSubTaskChildren(Task<?> t) {
        if (!(t instanceof HasTaskChildren)) {
            return Collections.emptyList();
        }
        return new LinkedList<TaskSummary>(Collections2.transform(Lists.newArrayList(((HasTaskChildren) t).getChildren()),
                TaskTransformer.fromTask(ui.getBaseUriBuilder())));
    }

    @Override
    public String stream(String taskId, String streamId) {
        Task<?> t = mgmt().getExecutionManager().getTask(taskId);
        if (t == null) {
            throw WebResourceUtils.notFound("Cannot find task '%s'", taskId);
        }
        checkEntityEntitled(t);
        checkStreamEntitled(t, streamId);

        WrappedStream stream = BrooklynTaskTags.stream(t, streamId);
        if (stream == null) {
            throw WebResourceUtils.notFound("Cannot find stream '%s' in task '%s'", streamId, taskId);
        }
        return stream.streamContents.get();
    }

    protected void checkEntityEntitled(Task<?> task) {
        Entity entity = BrooklynTaskTags.getContextEntity(task);
        if (entity != null && !Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
            throw WebResourceUtils.forbidden("User '%s' is not authorized to see activity of entity '%s'",
                    Entitlements.getEntitlementContext().user(), entity);
        }
    }

    protected void checkStreamEntitled(Task<?> task, String streamId) {
        Entity entity = BrooklynTaskTags.getContextEntity(task);
        Entitlements.TaskAndItem<String> item = new Entitlements.TaskAndItem<String>(task, streamId);
        if (entity != null && !Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ACTIVITY_STREAMS, item)) {
            throw WebResourceUtils.forbidden("User '%s' is not authorized to see activity stream of entity '%s'",
                    Entitlements.getEntitlementContext().user(), entity);
        }
    }
}
