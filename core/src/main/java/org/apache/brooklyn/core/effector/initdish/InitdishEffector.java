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
package org.apache.brooklyn.core.effector.initdish;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddEffector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.Effectors.EffectorBuilder;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils.CreationResult;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.gson.Gson;

/*

NOTES

# parameters

initializers:
  wc:
    type: ssh
    parameters:
      words:
        type: string
        default: hello world
    command:
      echo $WORDS | wc
    env:
      WORDS: $brooklyn:param("words")
  wc2:
    type: sequence
    parameters:
      words1: string
      words2: string
    collectResultsAsParam: result
    tasks:
    - type: parallel
      tasks:
      - type: effector
        effector: wc
        parameters:
          words: $brooklyn:param("words1")
      - type: effector
        effector: wc
        parameters:
          words: $brooklyn:param("words2")
    - type: add
      args: $brooklyn:param("result")[-1]
    

# semaphores

035-pre-launch-get-semaphore: { acquire-semaphore: { on: $brooklyn:parent(), name: "node-launch", 
    auto-release: true  # means the semaphore will become available when the parent task completes (including cancel) 
    } }
040-launch: { ssh: "service cassandra start" }
045-confirm-service-up: { wait: { sensor: service.inCluster, timeout: 20m } }
050-finish-release-semaphore: semaphore-release



# very long running tasks / callbacks

01-ask-approval:
  ...
02-do-thing:
  ...


long-running-task-callback:
  callback-basename: destroy-thing
  callback-parameters:
  now:
    type: open-ticket
    message: Resize recommended. Proceed?
    options:
      Yes: $brooklyn:param("approved-url")
      No: $brooklyn:param("cancel-url")
  callback-task:
    type: effector
    name: resize
    parameters:
      delta: 1
    
ask-approval-first:
  type: sequential
  tasks:
  - type: create-callback-effector
    basename: resize-approved
    callback:
      ...
  - type: create-callback-effector
    basename: resize-failed
  - type: open-ticket
    message: Resize recommended. Proceed?
    options:
      Yes: $brooklyn:param("result[0]")['url']
      No: $brooklyn:param("result[1]")['url']




effectors:
 -  type: CommandSequenceEffector
    name: start
    impl:                                      
    - sequence_identifier: 0-provision  [optional]
      type: my.ProvisionEffectorTaskFactory
    - seq_id: 1-install
      type: script
      script: |
        echo foo ${entity.config.foo}

                    
            01-install:
                parallel:
                 -  bash: |
                    echo foo ${entity.config.foo}
                 -  copy:
                        from: URL
                        to: ${entity.sensor['run.dir']}/file.txt
            02-loop-entities:
                foreach:
                    expression: $brooklyn:component['x'].descendants
                    var: x
                    command:
                        effector:
                            name: restart
                            parameters:
                                restart_machine: auto
            03-condition:
                conditional:
                    if: $x
                    then:
                        bash: echo yup
                    else:
                    - if: $y
                      then:
                         bash: echo y
                    - bash: echo none
            04-jump:
                goto: 02-install

           
                   


catalog:
  id: my-basic-1
services:
- type: vanilla-software-process
  effectors:
    start:
      0-provision: get-machine
      1-install:
        - copy:
            from:  classpath://foo.tar
            to: /tmp/foo/foo.tar
        - bash:
          - cd /tmp/foo
          - unzip foo.tar
        - bash: foo.sh
      2-launch:
          - cd /tmp/foo ; ./foo.sh
     policy:
     - type: Hook1

    triggers:
    - on: $brooklyn:component("db").sensor("database_url")
       do:
        - bash:
            mysql install table
        - publish_sensor basic-ready true

----

services:
- type: my-basic-2
  effectors:
    start:
      1.5.11-post-install:
         bash: |
echo custom step

      1.7-another-post-install:
      
      
 */
/** Entity initializer which defines an effector which adds tasks using InitD semantics.
 * 
 * @since 0.10.0 */
@Beta
public class InitdishEffector extends AddEffector {
    
    private static final Logger log = LoggerFactory.getLogger(InitdishEffector.class);
    
    public static final ConfigKey<Object> BLUEPRINT_YAML = ConfigKeys.newConfigKey(Object.class, "blueprint_yaml");
    public static final ConfigKey<String> BLUEPRINT_TYPE = ConfigKeys.newStringConfigKey("blueprint_type");
    public static final ConfigKey<Boolean> AUTO_START = ConfigKeys.newBooleanConfigKey("auto_start");
    
    public InitdishEffector(ConfigBag params) {
        super(newEffectorBuilder(params).build());
    }
    
    public InitdishEffector(Map<String,String> params) {
        this(ConfigBag.newInstance(params));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static EffectorBuilder<List<String>> newEffectorBuilder(ConfigBag params) {
        EffectorBuilder<List<String>> eff = (EffectorBuilder) AddEffector.newEffectorBuilder(List.class, params);
        eff.impl(new Body(eff.buildAbstract(), params));
        return eff;
    }

    protected static class Body extends EffectorBody<List<String>> {

        private final Effector<?> effector;
        private final String blueprintBase;
        private final Boolean autostart;

        public Body(Effector<?> eff, ConfigBag params) {
            this.effector = eff;
            String newBlueprint = null;
            Object yaml = params.get(BLUEPRINT_YAML);
            if (yaml instanceof Map) {
                newBlueprint = new Gson().toJson(yaml);
            } else if (yaml instanceof String) {
                newBlueprint = (String) yaml;
            } else if (yaml!=null) {
                throw new IllegalArgumentException(this+" requires map or string in "+BLUEPRINT_YAML+"; not "+yaml.getClass()+" ("+yaml+")");
            }
            String blueprintType = params.get(BLUEPRINT_TYPE);
            if (blueprintType!=null) {
                if (newBlueprint!=null) {
                    throw new IllegalArgumentException(this+" cannot take both "+BLUEPRINT_TYPE+" and "+BLUEPRINT_YAML);
                }
                newBlueprint = "services: [ { type: "+blueprintType+" } ]";
            }
            if (newBlueprint==null) {
                throw new IllegalArgumentException(this+" requires either "+BLUEPRINT_TYPE+" or "+BLUEPRINT_YAML);
            }
            blueprintBase = newBlueprint;
            autostart = params.get(AUTO_START);
        }

        @Override
        public List<String> call(ConfigBag params) {
            params = getMergedParams(effector, params);
            
            String blueprint = blueprintBase;
            if (!params.isEmpty()) { 
                blueprint = blueprint+"\n"+"brooklyn.config: "+
                    new Gson().toJson(params.getAllConfig());
            }

            log.debug(this+" adding children to "+entity()+":\n"+blueprint);
            CreationResult<List<Entity>, List<String>> result = EntityManagementUtils.addChildren(entity(), blueprint, autostart);
            log.debug(this+" added children to "+entity()+": "+result.get());
            return result.task().getUnchecked();
        }
    }
}
