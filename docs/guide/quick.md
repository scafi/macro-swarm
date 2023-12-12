---
layout: docs
title:  "Quick start"

---


For more details, please refer to the [API documentation](https://scafi.github.io/macro-swarm/api/it/unibo/scafi/index.html).

MacroSwarm is a field-based libraries for expressing swarm behaviors in a declarative way.
It is based on [ScaFi](), a scala library for programming aggregate computing systems.
It supports a large variety of swarm behaviors, including collective movement, shape formation, team formation, and collective planning.

This library does not provide any simulation environment, but it is possible to use it with [Alchemist](https://alchemistsimulator.github.io/).
In the [alchemist](/guide/alchemist.html) section of the guide, we provide a quick tutorial on how to use MacroSwarm with Alchemist.

## Learning Materials
- [API documentation](https://scafi.github.io/macro-swarm/api/it/unibo/scafi/index.html)
- [Guide](/guide/index.html)
- [COORDINATION 2023 paper](https://www.researchgate.net/publication/371587547_MacroSwarm_A_Field-Based_Compositional_Framework_for_Swarm_Programming)
More details about the library can be found in the [API documentation](https://scafi.github.io/macro-swarm/api/it/unibo/scafi/index.html)
and in the main concepts section of the [guide](/guide/concepts.html).

## Example

### Requirements
- Java 11
- SBT

### Steps
1) Create a new SBT project with the following `build.sbt` file:
```scala
ThisBuild / scalaVersion := "2.13.12"
val alchemistClass = "it.unibo.alchemist.Alchemist"
lazy val root = (project in file("."))
  .settings(
    name := "macro-swarm-demo",
    libraryDependencies += "it.unibo.scafi" %% "macro-swarm-alchemist" % "1.4.0",
    Compile / mainClass := Some(alchemistClass),
    run / mainClass := Some(alchemistClass)
  )

addCommandAlias("runAlchemist", "run src/main/yaml/main.yaml")
```

2) Create a main app like the following:
```scala
package example

import it.unibo.scafi.macroswarm.MacroSwarmAlchemistSupport._ // import all the MacroSwarm API
import it.unibo.scafi.macroswarm.MacroSwarmAlchemistSupport.incarnation._ // import the standard AC API

class SimpleMovement extends MacroSwarmProgram // define a program that supports the movement in alchemist env
  with StandardSensors with TimeUtils // standard AC API (sensing and time)
  // library for basic movement
  with BaseMovementLib {

  override def main(): Any =
    brownian(1) // random movement
}
```

3) Create a new file `src/main/yaml/main.yaml` with the following content:

```yaml
incarnation: scafi

launcher: # launcher configuration, i.e., the gui that will be used
  type: SingleRunSwingUI

seeds: # seeds for the random number generator
  scenario: 0
  simulation: 0

network-model: # example of network connection
  type: ConnectWithinDistance
  parameters: [350]

_reactions:
  - program: &program # define a program that will be executed by the agents, based on macro swarm
      - time-distribution:
          type: DiracComb
          parameters: [ 0, 1 ]
        type: Event
        actions:
          - type: RunScafiProgram
            parameters: [example.SimpleMovement, 1.0]
      - program: send
  - move: &move # define the actuation, that use the data produced by macro swarm
      - time-distribution:
          type: DiracComb
          parameters: [ 0, 1 ]
        type: Event
        actions: { type: MoveToTarget, parameters: [ destination, 10 ] }

deployments:
  type: Grid
  parameters: [0, 0, 1000, 1000, 300, 300, 25, 25]
  programs:
    - *program
    - *move
```


## Related Tools
- [Alchemist](https://alchemistsimulator.github.io/): a simulator for aggregate computing systems
- [ScaFi](https://scafi.github.io/): a scala library for programming aggregate computing systems

## People
- [Gianluca Aguzzi](https://www.unibo.it/sitoweb/gianluca.aguzzi): main developer and maintainer
- [Roberto Casadei](https://www.unibo.it/sitoweb/roby.casadei)
- [Mirko Viroli](https://www.unibo.it/sitoweb/mirko.viroli)
