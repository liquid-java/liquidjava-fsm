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
```

A class with several `@StateSet` annotations has one state machine per state set, since each changes independently.
`stateSet` is the index of the state set, the same order the verifier uses in `StateErrorContext`.
A state machine only has the transitions that mention its states. States of other state sets in a `from` are kept as
conditions, and in a `to` they are left out, since they change at the same time.
`parse` returns the state machine of the first state set.

The verifier uses the first `@StateRefinement` of a method whose `from` matches the current state. A `@StateRefinement`
without `from` therefore only goes from the states that the earlier ones do not cover. When it sits beside
`@StateRefinement`s with `from`, it is also an entry transition in `entryTransitions`: how an object whose state is
unknown (a field, a parameter or the result of a getter) enters the protocol, drawn as an arrow from the initial pseudo
state. For example, `canUndo` below moves an object in an unknown state to `endDone` when it returns `true`, but keeps
the state it was in otherwise:

```java
@StateRefinement(from="start(this)", to="!return && start(this)")
@StateRefinement(from="canEdit(this)")
@StateRefinement(from="endDone(this)")
@StateRefinement(from="endUndone(this)", to="!return && endUndone(this)")
@StateRefinement(to="return ? endDone(this) : true")
boolean canUndo();
```

```json
"entryTransitions": [
  {
    "to": "endDone",
    "label": "canUndo",
    "toCondition": "return"
  }
]
```

### JSON Output

```json
{
  "className": "example.File",
  "stateSet": 0,
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
