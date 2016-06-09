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
package org.apache.brooklyn.entity.software.initdish;

public class Sketch2 {

/*

// MOTIVATION

We want a JSON/YAML schema which allows us to do bi-directional serialization to Java with docgen.
That is:
* It is easy for a user to write YAML which generates the objects we care about
* It is easy for a user to read YAML generated from objects
* The syntax of the YAML can be documented automatically from the schema
* JSON can also be read or written (we restrict to the subset of YAML which is isomorphic to JSON)

The focus on ease-of-reading and ease-of-writing differentiates this from other JSON/YAML
serialization processes.  For instance we want to be able to support the following polymorphic
expressions:

shapes:
- type: square
  size: 12
  color: red
- square:  # type implied by key
    size: 12
    color: red
- big_red_square   # type
- red square: { size: 12 }   # string is parsed into words
- red square: 12             # value is taken as a default value in a map

You shouldn't use all of these to express shapes; but depending on the subject matter some
syntaxes may be more natural.  The important thing in this proposal is being *able* to support
them, in a way they can be parsed, they can be generated, and the expected/supported syntax 
can be explained to a user.
   

// INRODUCTORY EXAMPLES

# basic - define a type 'shape' pointing at Shape using no-arg constructor
- id: shape
  # no-arg constructor
  type: java:org.acme.Shape  # where `class Shape { String name; String color; }`

# setting fields -- possible as defining type or instance
# (note, this syntax below would work for either, but henceforth we drop 'id' when showing an instance)
- id: square
  type: shape
  fields:
    # any fields here read/written by direct access by default, or fail if not matched
    name: square
    color: red

* extending class, field overwriting

- id: green-square
  type: square
  fields:
    # map of fields is merged with parent
    color: green


* allowing fields at root (note: automatically the case for 'config keys')

- id: ez-square
  type: square
  serialization:
  - type: explicit-field
    field-name: color
  - type: no-others

then (instance)

- type: ez-square
  color: blue

Serialization takes a list of serializer types.  These are applied in order both for serialization 
and deserialization.

`explicit-field` says to look at the root as well as in the 'fields' block.  It has one required
parameter, field-name, and several optional ones:

  - type: explicit-field
    field-name: color
    key-name: color      # this is used in yaml
    aliases: [ colour ]  # accepted in yaml as a synonym for key-name; `alias` also accepted
    field-type: string   # inferred from java field, but you can constrain further to yaml types
    constraint: required # currently just supports 'required', or blank for none, but reserved for future use
    description: The color of the shape   # text (markdown) 
    serialization:       # optional additional serialization instructions
    - if-string:         # (defined below)
        set-key: field-name


`no-others` says that any unrecognised fields in YAML will force an error prior to the default
deserialization steps (which attempt to write named config and then fields directly, before failing),
and on serialization it will ignore any unnamed fields.

As a convenience if an entry in the list is a string S, the entry is taken as 
`{ type: explicit-field, field-name: S }`.

Thus the following would also be allowed:

  serialization:
  - color
  - type: no-others

At the heart of this YAML serialization is the idea of heavily overloading to permit the most
natural way of writing in different situations. We go a bit overboard in 'serialization' to illustrate
this quite deeply technically. (Feel free to ignore, if you're comfortable with the simple examples.)
If the `serialization` field (which expects a list) is given a map, each <K,V> pair in that map is 
interpreted as follows:
* if V is a map then K is set in that map as 'field-name' (error if field-name is already set)
* if V is not a map then a map is created as { field-name: K, type: V }
Thus you could also write:

  serialization:
    color: { alias: colour, description: "The color of the shape", constraint: required } 

(Note that some serialization types, such as 'no-others', cannot be expressed in this way,
because `field-name` is not supported on that type. This syntax is intended for the common 
case when all fields are settable and we are defining top-level fields.)

Finally if the serialization is given a list, and any entry in the list is a map which
does not define a type, the following rules apply:
* If the entry is a map of size larger than one, the type defaults to explicit-field.  
* If the entry is a map of size one the key is taken as the type and merged with the value
  if the value is a map (or it can be interpreted as such with an if-string on the type)
Thus we can write:

  serialization:
  - field-name: color
    alias: colour
  - no-others:

Note: this has some surprising side-effects in occasional edge cases; consider:

  # BAD: this would try to load a serialization type 'field-name' 
  serialization:
  - field-name: color
  # GOOD options
  serialization:
  - color
  # or
  serialization:
    color:
  # or
  serialization:
  - field-name: color
    alias: colour  

  # BAD: this would define a field `explicitField`, then fail because that field-name is in use
  serialization:
    explicit-field: { field-name: color }
  # GOOD options (in addition to those in previous section, but assuming you wanted to say the type explciitly)
  serialization:
  - explicit-field: { field-name: color }
  # or 
  - explicit-field: color

It does the right thing in most cases, and it serves to illustrate the flexibility of this
approach. In most cases it's probably a bad idea to do this much overloading!  However the
descriptions here will normally be taken from java annotations and not written by hand,
so emphasis is on making it easy-to-read (which overloading does nicely) rather than 
easy-to-write.

Of course if you have any doubt, simply use the long-winded syntax and avoid any convenience syntax:

  serialization:
  - type: explicit-field
    field-name: color
    alias: colour


* name mangling pattern, default conversion for fields:
  wherever pattern is lower-upper-lower (java) <-> lower-dash-lower-lower (yaml)

  fields:
    # corresponds to field shapeColor 
    shape-color: Bob


* accepting lists with generics
  
- field-name: serialization
  field-type: list<serialization>
  serialization:
  
  # transforms `- color` to `- { explicit-field: color }` which will be interpreted again
  - type: if-string
    set-key: explicit-field
    
  # alternative implementation of above (clearer, avoiding re-interpretation)
  # transforms `- color` to `- { type: explicit-field, field-name: color }`
  - type: if-string
    default-key: field-name
    default:
      type: explicit-field

  # transforms `- k: { type: x }` to `{ field-name: k, type: x}` (and `- k: x` to the same)
  - type: map-to-map-list
      key-for-key: field-name
      key-for-string-value: type   # note, only applies if x non-blank
      default:
        type: explicit-field       # required else the entry will be treated as a single-key

  # transforms `- key: { field-name: x }` to `- { type: explicit-field, field-name: x }`
  - type: single-key-maps-in-list
    default-key: type              # NB fails if this key is present in the value which is a map
  
  # applies the given values if not present, running after the items above
  - type: default-maps-in-list
    default:
      type: explicit-field
    
xx default type
xx Finally if the serialization is given a list, and any entry in the list is a map which
does not define a type, the following rules apply:
* If the entry is a map of size larger than one, the type defaults to explicit-field.  
* If the entry is a map of size one the key is taken as the type and merged with the value
  if the value is a map (or it can be interpreted as such with an if-string on the type)




This assumes a set up as:

- id: serialization
  type: java:...Serialization
- id: explicit-field
- id: no-others
- id: if-string  
- id: map-to-map-list
  ...

* accepting maps with generics


* type overloading, if string, if number, if map, if list...  inferring type, or setting diff fields

Consider the definition of 


* setting in java class with annotation
* if-list, if-map, etc
* other constructors, static factory methods
* fields fetched by getters, written by setters
* include/exclude if null/empty/default

- id: square
  type: java:...Square
  supertypes: [ shape ]
  serialization:
  - type: special-field
    field-name: sizePx
    key-name: size
    type: double
    description: The size of the square


  OR

  serialization:
  - type: some-fields-special
    special-fields:
      size:
        type: double
        description: The size of the square

  serialization:
  - type: some-fields-special
    special-fields:
      size: double

// THEN

- id: field-mapping
  
- id: map-serialization
  type: java:...MapSerialization
  fields:
    field-mappings:
    - field: key-type
      aliases: [ key_type, keyType ]
      key: keyType
      type: string
      description: The type that each key must be interpreted as
    - field: value-type
      aliases: [ value_type, valueType ]
      key: valueType
      type: string
      description: The type that each value must be interpreted as

- id: task-factory
  type: java:....TaskFactory

- id: effector-task-factory
  type: java:....ETF

- id: provision
  type: java:...ProvisionTaskFactory


- id: initdish-effector
  type: java:...Initdish
  fields:
  - field: steps
    serialization:
      type: map-serialization
      key-type: string
      value-type: task-factory
      
  
// allowing

- id: print-all
  type: initdish-effector
  steps:
    00-provision: provision
    10-install:
      type: bash
      contents: |
        curl blah
        tar blah
    20-run:
      type: invoke-effector
      effector: launch
      parameters:
        ...

- type: entity
  fields:
  - key: effectors
    yamlType: list
    yamlGenericType: effector
    serializer:
    - type: write-list-field
      field: effectors
- type: effector


  
- id: some-fields-special-serialization
  type: java:...SomeFieldsSpecialSerialization
  fields:
  - regex: .* ????



 */
    
}
