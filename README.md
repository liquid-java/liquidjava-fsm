# LiquidJava FSM

LiquidJava state machine parser used by the LiquidJava VS Code language server and MCP server.
Depends on the LiquidJava verifier.

## Example

### Source File

```java
package example;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"open", "closed"})
public class File {

    @StateRefinement(to="open(this)")
    public File() {}

    @StateRefinement(from="open(this)")
    public void read() {}

    @StateRefinement(from="open(this)", to="closed(this)")
    public void close() {}
}
```

### Source Code

```java
import liquidjava.fsm.StateMachine;
import liquidjava.fsm.StateMachineParser;

StateMachine stateMachine = StateMachineParser.parse(path.toUri().toString());
// in a file with several classes, or for a verifier error's StateErrorContext#receiverClass
StateMachine named = StateMachineParser.parse(path.toUri().toString(), "example.File");
// one state machine per @StateSet, in declaration order
List<StateMachine> stateMachines = StateMachineParser.parseAll(path.toUri().toString());
// with the state sets and methods java.awt.Frame inherits from the specs of its supertypes in these files
List<StateMachine> inherited = StateMachineParser.parseAll(List.of(frameSpec, windowSpec), "java.awt.Frame");
```

A class inherits the state sets and methods of the specs of its supertypes in the parsed files, as in the verifier.
Its own state sets come first, then those of its supertypes, nearest first. A call uses the class's own refinement of a
method, otherwise that of the nearest type that declares the method, so an inherited refinement is left out when a
nearer spec refines the method, when a nearer type redeclares it, or when not every caller can call it. Constructors are
the class's own.

A class with several `@StateSet` annotations has one state machine per state set, since each changes independently.
`stateSet` is the index of the state set, the same order the verifier uses in `StateErrorContext`.
A state machine only has the transitions that mention its states. States of other state sets in a `from` are kept as
conditions, and in a `to` they are left out, since they change at the same time.
`parse` returns the state machine of the first state set.
`stateSetName` is the name given to the state set, `@StateSet(name = "phase", value = {...})`, or null if it has none.
As in the verifier, a state can also be written as a value of its state set, `phase(this) == open` or
`phase(this) != open`, in either order and mixed with the shorthand `open(this)`.

The verifier uses the first `@StateRefinement` of a method whose `from` matches the current state, and reads a
`@StateRefinement` without `from` as `from = true`: the method can be called in any state and moves to `to`. It therefore
goes from the states that the earlier ones do not cover, and never from the initial pseudo state, which only
constructors and factory methods leave. For example, `canRedo` below goes from `notRedoable` to `redoable` when it
returns `true` and stays in `notRedoable` otherwise, while in `redoable` the first alternative decides:

```java
@StateRefinement(from = "redo(this) == redoable", to = "redo(this) == redoable && return")
@StateRefinement(to = "return ? redo(this) == redoable : redo(this) == notRedoable")
boolean canRedo();
```

`entryTransitions` is always empty and only kept so that clients still compile.

### JSON Output

```json
{
  "className": "example.File",
  "stateSet": 0,
  "stateSetName": null,
  "states": [
    "open",
    "closed"
  ],
  "transitions": [
    {
      "from": "open",
      "to": "closed",
      "label": "close",
      "fromCondition": null,
      "toCondition": null
    },
    {
      "from": "open",
      "to": "open",
      "label": "read",
      "fromCondition": null,
      "toCondition": null
    }
  ],
  "initialTransitions": [
    {
      "to": "open",
      "toCondition": null
    }
  ],
  "entryTransitions": []
}
```

## Maven dependency

```xml
<dependency>
  <groupId>io.github.liquid-java</groupId>
  <artifactId>liquidjava-fsm</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Release

Releases are published to Maven Central by GitHub Actions when a `v*`
tag is pushed to `main` that is created using the `release.sh` script:

```bash
./release.sh
```
