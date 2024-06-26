---
layout: docs
title:  "Quick start"

---


MacroSwarm is a field-based libraries for expressing swarm behaviors in a declarative way.
It is based on [ScaFi](https://github.com/scafi/scafi), a scala library for programming aggregate computing systems.
It supports a large variety of swarm behaviors, including collective movement, shape formation, team formation, and collective planning.

This library does not provide any simulation environment, but it is possible to use it with [Alchemist](https://alchemistsimulator.github.io/).
In the [alchemist](/macro-swarm/guide/alchemist.html) section of the guide, we provide a quick tutorial on how to use MacroSwarm with Alchemist.

## Learning Materials
- [COORDINATION 2023 paper](https://www.researchgate.net/publication/371587547_MacroSwarm_A_Field-Based_Compositional_Framework_for_Swarm_Programming)
More details about the library can be found in the [API documentation](https://scafi.github.io/macro-swarm/api/it/unibo/scafi/index.html).

## Example

### Requirements
- Java 11: [installation guide](https://openjdk.org/install/)
- SBT: [installation guide](https://www.scala-sbt.org/1.x/docs/Setup.html)

### Steps
1) Create a new SBT project with the following `build.sbt` file:
```scala
ThisBuild / scalaVersion := "2.13.12"
val alchemistClass = "it.unibo.alchemist.Alchemist"
lazy val root = (project in file("."))
  .settings(
    name := "macro-swarm-demo",
    libraryDependencies += "it.unibo.scafi" %% "macro-swarm-alchemist" % "1.5.3",
    Compile / mainClass := Some(alchemistClass),
    run / mainClass := Some(alchemistClass)
  )

addCommandAlias("runAlchemist", "run run src/main/yaml/main.yaml")
```

2) Create a main app like the following:
```scala
package example

import it.unibo.scafi.macroswarm.MacroSwarmAlchemistSupport._
import it.unibo.scafi.macroswarm.MacroSwarmAlchemistSupport.incarnation._
import it.unibo.scafi.space.Point3D

class SimpleMovement extends MacroSwarmProgram // define a program that supports the movement in alchemist env
  with StandardSensors with TimeUtils // standard AC API (sensing and time)
  with ScafiAlchemistSupport // helper for alchemist
  // library for basic movement
  with BaseMovementLib {

  override protected def movementLogic(): Point3D = brownian()
}
```

3) Create a new file `src/main/yaml/main.yaml` with the following content:

```yaml
incarnation: scafi
# Define the launcher, standard one
launcher: { parameters: { batch: [], autoStart: false } }
# Attach the GUI to the simulator
monitors: { type: SwingGUI, parameters: { graphics: src/main/resources/effect.json } }

# Define how the nodes are connected, in this case within a distance of 350 units
network-model: { type: ConnectWithinDistance, parameters: [350] }
# Define the behaviour of the nodes
_reactions:
  - program: &program # MacroSwarm program, executed every second
      - time-distribution: 1
        type: Event
        actions:
          - type: RunScafiProgram
            parameters: [example.SimpleMovement, 1.0]
      - program: send
  - move: &move # Related actuation, executed every second
      - time-distribution: 1
        type: Event
        actions: { type: MoveToTarget, parameters: [ destination, 10 ] }
# Define how to deploy the program, in this case in a grid
# which starts from (-500, -500) and ends in (500, 500) with a step of (300, 300).
# 25 is the variance of the position of the nodes in the grid (randomness).
# The program is deployed in a 4x4 grid.
deployments:
  type: Grid
  parameters: [-500, -500, 500, 500, 300, 300, 25, 25]
  programs: [ *program, *move ] # Add the behaviour
```
After that, typing `sbt runAlchemist` in the terminal, you should see a window with a simulation started.
To effectively start the simulation, please press the key <kbd>p</kbd> on the keyboard.

![](https://user-images.githubusercontent.com/23448811/224010877-6f5c9d36-d348-4343-8b66-19f78778297e.gif)


## Related Tools
- [Alchemist](https://alchemistsimulator.github.io/): a simulator for aggregate computing systems
- [ScaFi](https://scafi.github.io/): a scala library for programming aggregate computing systems

## People
- [Gianluca Aguzzi](https://www.unibo.it/sitoweb/gianluca.aguzzi): main developer and maintainer
- [Roberto Casadei](https://www.unibo.it/sitoweb/roby.casadei)
- [Mirko Viroli](https://www.unibo.it/sitoweb/mirko.viroli)
