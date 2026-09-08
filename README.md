# LiquidJava FSM

LiquidJava state machine parser used by the VS Code language server and MCP server.
Depends on the verifier and Spoon.

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
```

### JSON Output

```json
{
  "className": "example.File",
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
  ]
}
```

## Maven dependency

```xml
<dependency>
  <groupId>io.github.liquid-java</groupId>
  <artifactId>liquidjava-fsm</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Release

Releases are published to Maven Central by GitHub Actions when a `v*`
tag is pushed to `main` that is created using the `release.sh` script:

```bash
./release.sh
```
