package it.unibo.scafi.macroswarm

import it.unibo.scafi.core.RichLanguage
import it.unibo.scafi.incarnations.Incarnation
import it.unibo.scafi.lib.StandardLibrary

abstract class MacroSwarmSupport[E <: MacroSwarmSupport.Dependency](val incarnation: E)
    extends FastBlocks[E]
    with CoreMovement[E]
    with CoordinatedMovement[E]
    with LeaderBasedMovement[E] {}

object MacroSwarmSupport {
  type Dependency = Incarnation with StandardLibrary with RichLanguage
}
