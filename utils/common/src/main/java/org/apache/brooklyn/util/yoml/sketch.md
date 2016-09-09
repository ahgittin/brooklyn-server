%
% Licensed to the Apache Software Foundation (ASF) under one
% or more contributor license agreements.  See the NOTICE file
% distributed with this work for additional information
% regarding copyright ownership.  The ASF licenses this file
% to you under the Apache License, Version 2.0 (the
% "License"); you may not use this file except in compliance
% with the License.  You may obtain a copy of the License at
%
%     http://www.apache.org/licenses/LICENSE-2.0
%
% Unless required by applicable law or agreed to in writing,
% software distributed under the License is distributed on an
% "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
% KIND, either express or implied.  See the License for the
% specific language governing permissions and limitations
% under the License.
%

# YOML: The YAML Object Mapping Language

## Motivation

We want a JSON/YAML schema which allows us to do bi-directional serialization to Java with docgen.
That is:
* It is easy for a user to write the YAML which generates the objects they care about
* It is easy for a user to read the YAML generated from data objects
* The syntax of the YAML can be documented automatically from the schema (including code-point-completion)
* JSON can also be read or written (we restrict to the subset of YAML which is isomorphic to JSON)

The focus on ease-of-reading and ease-of-writing differentiates this from other JSON/YAML
serialization processes.  For instance we want to be able to support the following polymorphic
expressions:

```
shapes:
- type: square     # standard, explicit type and fields, but hard to read
  size: 12
  color: red
- square:          # type implied by key
    size: 12
    color: red
- square: 12       # value is taken as a default key in a map
- red_square   # string on its own can be interpreted in many ways but often it's the type
# and optionally (deferred)
- red square: { size: 12 }   # multi-word string could be parsed in many ways (a la css border)
```

Because in most contexts we have some sense of what we are expecting, we can get very efficient
readable representations.

Of course you shouldn't use all of these to express the same type; but depending on the subject 
matter some syntaxes may be more natural than others.  Consider allowing writing:

```
  effectors:   # field in parent, expects list of types 'Effector' 
    say_hi:    # map converts to list treating key as name of effector, expecting type 'Effector' as value
      type: ssh     # type alias 'ssh' when type 'Effector` is needed matches SshEffector type
      parameters:   # field in SshEffector, of type Parameter
      - person      # string given when Parameter expected means it's the parameter name
      - name: hello_word                  # map of type, becomes a Parameter populating fields
        description: how to say hello
        default: hello 
      command: |                          # and now the command, which SshEffector expects
        echo ${hello_word} ${person:-world}
```

The important thing here is not using all of them at the same time (as we did for shape), 
but being *able* to support an author picking the subset that is right for a given situation, 
in a way that they can be parsed, they can be generated, and the expected/supported syntax 
can be documented automatically.
   

## Introductory Examples


### Defining types and instances

You define a type by giving an `id` (how it is known) and an instance definition specifying the parent `type`. 
These are kept in a type registry and can be used when defining other types or instances.
A "type definition" looks like:

```
- id: shape
  definition:
    type: java:org.acme.Shape  # where `class Shape { String name; String color; }`
```

The `java:` prefix is an optional shorthand to allow a Java type to be accessed.
For now this assumes a no-arg constructor.

You can then specify an instance to be created by giving an "instance definition",
referring to a defined `type` and optionally `fields`:

```
- type: shape
  fields:  # optionally
    name: square
    color: red
``` 

Type definitions can also refer to types already defined types and can give an instance definition:

```
- id: red-square
  definition:
    type: shape
    fields:
      # any fields here read/written by direct access by default, or fail if not matched
      name: square
      color: red
```

The heart of YOML is the extensible support for other syntaxes available, described below.
These lead to succinct and easy-to-use definitions, both for people to write and for people to read
even when machine-generated.  The approach also supports documentation and code-point completion.


### Instance definitions

You define an instance to be created by referencing a type in the registry, and optionally specifying fields:

    type: red-square

Or

    type: shape
    fields:
      name: square
      color: red


### Type definitions

You define a new type in the registry by giving an `id` and the instance `definition`:

    id: red-square
    definition:
      type: shape
      fields:
        name: square
        color: red

Where you just want to define a Java class, a shorthand permits providing `type` instead of the `definition`: 

    id: shape
    type: java:org.acme.Shape


### Overwriting fields

Fields can be overwritten, e.g. to get a pink square:

    type: red-square
    fields:
      # map of fields is merged with that of parent
      color: pink

You can do this in type definitions, so you could do this:

```
- id: pink-square
  type: red-square
  definition:
    fields:
      # map of fields is merged with that of parent
      color: pink
```

Although this would be more sensible:

```
- id: square
  definition:
    type: shape
    fields:
      name: square
- id: pink-square
  definition:
    type: square
    fields:
      color: pink
```


### Allowing fields at root

Type definitions also support specifying additional "serializers", the workers which provide
alternate syntaxes. One common one is the `explicit-field` serializer, allowing fields at
the root of an instance definition.  With this type defined:
 
- id: ez-square
  type: square
  serialization:
  - type: explicit-field
    field-name: color

You could skip the `fields` item altogether and write:

```
type: ez-square
color: pink
```

These are inherited, so we'd probably prefer to have these type definitions:

```
- id: shape
  definition:
    type: java:org.acme.Shape  # where `class Shape { String name; String color; }`
  serialization:
  - type: explicit-field
    field-name: name
  - type: explicit-field
    field-name: color
- id: square
  definition:
    type: shape
    name: square
```

## Intermission: On serializers and implementation (can skip)
 
Serialization takes a list of serializer types.  These are applied in order, both for serialization 
and deserialization, and re-run from the beginning if any are applied.

`explicit-field` says to look at the root as well as in the 'fields' block.  It has one required
parameter, field-name, and several optional ones, so a sample usage might look like:

```
  - type: explicit-field
    field-name: color
    key-name: color           # this is used in yaml
    aliases: [ colour ]       # things to accept in yaml as synonyms for key-name; `alias` also accepted
    aliases-strict: false     # if true, means only exact matches on key-name and aliases are accepted, otherwise a set of mangles are applied
    aliases-inherited: true   # if false, means only take aliases from the first explicit-field serializer for this field-name, otherwise any can be used 
    # TODO items below here are still WIP/planned
    field-type: string        # inferred from java field, but you can constrain further to yaml types
    constraint: required      # currently just supports 'required' (and 'null' not allowed) or blank for none (default), but reserved for future use
    description: The color of the shape   # text (markdown) 
    serialization:            # optional additional serialization instructions for this field
    - if-string:              # (defined below)
        set-key: field-name
```


### On overloading (really can skip!)

At the heart of this YAML serialization is the idea of heavily overloading to permit the most
natural way of writing in different situations. We go a bit overboard in 'serialization' to illustrate
below the different strategies. (Feel free to ignore, if you're comfortable with the simple examples.)

First, if the `serialization` field (which expects a list) is given a map, 
the `convert-map-to-list` serializer converts each <K,V> pair in that map to a list entry as follows:

* if V is a non-empty map, then the corresponding list entry is the map V with `{ .key: K }` added
* otherwise, the corresponding list entry is `{ .key: K, .value: V }`
 
Next, each entry in the list is interpreted as a `serialization` instance, 
and the serializations defined for that type specify:

* If the key `.value` is present and `type` is not defined, that key is renamed to `type` (ignored if `type` is already present)
* If it is a map of size exactly one, it is converted to a map as done with `convert-map-to-list` above
* If the key `.key` is present and `type` is not defined, that key is renamed to `type` (ignored if `type` is already present)
* If the item is a primitive V, it is converted to `{ .value: V }`
* If it is a map with no `type` defined, `type: explicit-field` is added


This allows the serialization rules defined on the specific type to kick in to handle `.key` or `.value` entries
introduced but not removed. In the case of `explicit-field` (the default type, as shown in the rules above), 
this will rename either such key `.value` to `field-name` (and give an error if `field-name` is already present). 

Thus we can write:

    serialization:
      # explicit fields
      color: { alias: colour, description: "The color of the shape", constraint: required } 
      name

Or

    serialization:
    - field-name: color
      alias: colour
    - name

This can have some surprising side-effects in occasional edge cases; consider:

```
  # BAD: this would try to load a type called 'color' 
  serialization:
  - color: {}
  # GOOD options
  serialization:
  - color
  # or
  serialization:
    color: {}
  # or
  serialization:
    color: explicit-field

  # BAD: this would try to load a type called 'field-name' 
  serialization:
  - field-name: color
  # GOOD options are those in the previous block or to add another field
  serialization:
  - field-name: color
    alias: colour  

  # BAD: this ultimately takes "explicit-field" as the "field-name", giving a conflict
  serialization:
    explicit-field: { field-name: color }
  # GOOD options (in addition to those in previous section, but assuming you wanted to say the type explicitly)
  serialization:
  - explicit-field: { field-name: color }
  # or 
  - explicit-field: color
```

It does the right thing in most cases, and it serves to illustrate the flexibility of this approach. 
Of course in most cases it's probably a bad idea to do this much overloading!
However the descriptions here will normally be taken from java annotations and not written by hand,
so emphasis is on making type definitions easy-to-read (which overloading does nicely), and 
instance definitions both easy-to-read and -write, rather than type definitions easy-to-write.

Of course if you have any doubt, simply use the long-winded syntax and avoid any convenience syntax:

```
  serialization:
  - type: explicit-field
    field-name: color
```


## Further Behaviours

### Name Mangling and Aliases

We apply a default conversion for fields: 
wherever pattern is lower-upper-lower (java) <-> lower-dash-lower-lower (yaml).
These are handled as a default set of aliases.

    fields:
      # corresponds to field shapeColor 
      shape-color: red


### Primitive types

All Java primitive types are known, with their boxed and unboxed names,
along with `string`.  The key `value` can be used to set a value for these.
It's not normally necessary to do this because the parser can usually detect
these types and coercion will be applied wherever one is expected;
it's only needed if the value needs coercing and the target type isn't implicit.
For instance a red square with size 8 could be defined as:

```
- type: shape
  color:
    type: string
    value: red
  size:
    type: int
    value: 8
```

Or of course the more concise:

```
- type: shape
  color: red
  size: 8
```


### Config/data keys

Some java types define static ConfigKey fields and a `configure(key, value)` or `configure(ConfigBag)`
method. These are detected and applied as one of the default strategies (below).


### Accepting lists, including generics

Where the java object is a list, this can correspond to YAML in many ways.
New serializations we introduce include `convert-map-to-map-list` (which allows
a map value to be supplied), `apply-defaults-in-list` (which ensures a set of keys
are present in every entry, using default values wherever the key is absent),
`convert-single-key-maps-in-list` (which gives special behaviour if the list consists
entirely of single-key-maps, useful where a map would normally be supplied but there
might be key collisions), `if-string-in-list` (which applies `if-string` to every
element in the list), and `convert-map-to-singleton-list` (which puts a map into
a list).

If no special list serialization is supplied for when expecting a type of `list<x>`,
the YAML must be a list and the serialization rules for `x` are then applied.  If no
generic type is available for a list and no serialization is specified, an explicit
type is required on all entries.

Serializations that apply to lists or map entries are applied to each entry, and if
any apply the serialization is then continued from the beginning.

As a complex example, the `serialization` list we described above has the following formal
schema:

```
- field-name: serialization
  field-type: list<serialization>
  serialization:
  
  # transforms `- color` to `- { explicit-field: color }` which will be interpreted again
  - type: if-string-in-list
    set-key: explicit-field
    
  # alternative implementation of above (more explicit, not relying on `apply-defaults-in-list`)
  # transforms `- color` to `- { type: explicit-field, field-name: color }`
  - type: if-string-in-list
    set-key: field-name
    default:
      type: explicit-field

  # describes how a yaml map can correspond to a list
  # in this example `k: { type: x }` in a map (not a list)
  # becomes an entry `{ field-name: k, type: x}` in a list
  # (and same for shorthand `k: x`; however if just `k` is supplied it
  # takes a default type `explicit-field`)
  - type: convert-map-to-map-list
    key-for-key: field-name
    key-for-string-value: type   # note, only applies if x non-blank
    default:
      type: explicit-field       # note: needed to prevent collision with `convert-single-key-in-list` 

  # if yaml is a list containing all maps swith a single key, treat the key specially
  # transforms `- x: k` or `- x: { field-name: k }` to `- { type: x, field-name: k }`
  # (use this one with care as it can be confusing, but useful where type is the only thing
  # always required! typically only use in conjunction with `if-string-in-list` where `set-key: type`.)
  - type: convert-single-key-maps-in-list
    key-for-key: type              # NB fails if this key is present in the value which is a map
    key-for-string-value: field-name
  
  # applies any listed unset "default keys" to the given default values,
  # for every map entry in a list
  # here this essentially makes `explicit-field` the default type
  - type: apply-defaults-in-list
    default:
      type: explicit-field
```

### Accepting maps, including generics

In some cases the underlying type will be a java Map.  The lowest level way of representing a map is
as a list of maps specifying the key and value of each entry, as follows:

```
- key:
    type: string
    value: key1
  value:
    type: red-square
- key:
    type: string
    value: key2
  value: a string
```

You can also use a more concise map syntax if keys are strings:

    key1: { type: red-square }
    key2: "a string"

If we have information about the generic types -- supplied e.g. with a type of `map<K,V>` --
then coercion will be applied in either of the above syntaxes.


### Where the expected type is unknown

In some instances an expected type may be explicitly `java.lang.Object`, or it may be
unknown (eg due to generics).  In these cases if no serialization rules are specified,
we take lists as lists, we take maps as objects if a `type` is defined, we take
primitives when used as keys in a map as those primitives, and we take other primitives
as *types*.  This last is to prevent errors.  It is usually recommended to ensure that 
either an expected type will be known or serialization rules are supplied (or both).   


### Default serialization

It is possible to set some serializations to be defaults run before or after a supplied list.
This is useful if for instance you want certain different default behaviours across the board.
Note that if interfacing with the existing defaults you wil need to understand that process
in detail; see implementation notes below. 


## Even more further behaviours (not part of MVP)

* preventing fields from being set
* type overloading, if string, if number, if map, if list...  inferring type, or setting diff fields
* super-types and abstract types (underlying java of `supertypes` must be assignable from underying java of `type`)
* merging ... deep? field-based?
* setting in java class with annotation
* if-list, if-map, if-key-present, etc
* fields fetched by getters, written by setters
* include/exclude if null/empty/default


## Implementation Notes

We have a `Converter` which runs through phases, running through all `Serializer` instances on each phase.
Each `Serializer` exposes methods to `read`, `write`, and `document`, and the appropriate method is invoked
depending on what the `Converter` is doing.

A `Serializer` can detect the phase and bail out if it isn't appropriate;
or they can end the current phase, and/or insert one or more phases to follow the current phase.
In addition, they use a shared blackboard to store local information and communicate state.
These are the mechanisms by which serializers do the right things in the right order,
whilst allowing them to be extended.

The general phases are:

* `manipulating` (custom serializers, operating directly on the input YAML map) 
* `handling-type` (default to instantaiate the java type, on read, or set the `type` field, on write),
  on read, sets the Java object and sets YamlKeysOnBlackboard which are subsequently used for manipulation;
  on write, sets the YAML object and sets JavaFieldsOnBlackboard (and sets ReadingTypeOnBlackboard with errors);
  inserting new phases:
  * when reading:
    * `manipulating` (custom serializers again, now with the object created, fields known, and other serializers loaded)
    * `handling-fields` (write the fields to the java object)
  * and when writing: 
    * `handling-fields` (collect the fields to write from the java object)
    * `manipulating` (custom serializers again, now with the type set and other serializers loaded)

Afterwards, a completion check runs across all blackboard items to enable the most appropriate error 
to be shown.


### TODO

* annotations (basic is done, but various "if" situations)
* complex syntax, type as key, etc
* config/data keys

* defining serializers and linking to brooklyn

* infinite loop detection: in serialize loop
* handle references, solve infinite loop detection in self-referential writes, with `.reference: ../../OBJ`
* best-serialization vs first-serialization

* documentation
* yaml segment information and code-point completion



## Real World Use Cases

### An Init.d-style entity/effector language 

```
- id: print-all
  type: initdish-effector
  steps:
    00-provision: provision
    10-install:
      bash: |
        curl blah
        tar blah
    20-run:
      effector: 
        launch:
          parameters...
    21-run-other:
      type; invoke-effector
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
```




First, if the `serialization` field (which expects a list) is given a map, 
the `convert-map-to-list` serializer converts each <K,V> pair in that map to a list entry as follows:

* if V is a non-empty map, then the corresponding list entry is the map V with `{ field-name: K }` added
* otherwise, the corresponding list entry is `{ field-name: K, type: V }`
  convert-map-to-list: { key-for-key: field-name
            key-for-primitive-value: type,  || key-for-any-value: ... || key-for-list-value: || key-for-map-value
              || merge-with-map-value
            apply-to-singleton-maps: true
            defaults: { type: explicit-field }
  # explicit-field sets key-for-list-value as aliases
# on serializer
  convert-singleton-map: { key-for-key: type
            key-for-primitive-value: type,  || key-for-any-value: ... || key-for-list-value: || key-for-map-value
              || merge-with-map-value
            defaults: { type: explicit-field }

Next, each entry in the list is interpreted as a `serialization` instance, 
and the serializations defined for that type specify:

* If the key `.key` is present and `type` is not defined, that key is renamed to `type` (ignored if `type` is already present)
  rename-key: { from: .key, to: type, fail-if-present: true }
  rename-default-key: type  (as above but .value)
  rename-default-value: to
  # above two serializers have special rules to need their own 

* If the item is a primitive V, it is converted to `{ .value: V }`
  primitive-to-kv-pair
  primitive-to-kv-pair: { key: .value || value: foo } 

* If it is a map with no `type` defined, `type: explicit-field` is added
  defaults: { type: explicit-field }
  # serializer declares convert-singleton-map ( merge-with-map-value )  

NOTES

convert-map-to-list (default-key, default-value)

* if V is a non-empty map, then the corresponding list entry is the map V with `{ <default-key>: K }` added
* otherwise, the corresponding list entry is `{ <default-key>: K, <default-value>: V }`
