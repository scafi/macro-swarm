package it.unibo.scafi.macroswarm

import it.unibo.scafi.core.RichLanguage
import it.unibo.scafi.incarnations.Incarnation
import it.unibo.scafi.lib.StandardLibrary

/** This trait provides the support for the MacroSwarm library, i.e., it provides the implementation of the swarm
  * behaviours expressed in CoreMovement, CoordinatedMovement, LeaderBasedMovement, and CollectivePlanner. It enriches
  * an incarnation without extending it, so that it can be mixed in with other traits. To use it, you need to extend it
  * and provide the incarnation you want to enrich. e.g., `object MyMacroSwarmSupport(ScafiIncarnationForAlchemist)
  * extends MacroSwarmSupport[MyIncarnation]` After that, you have to import the incarnation accordingly to the one you
  * provided, e.g., `import MyMacroSwarmSupport.incarnation._`
  * @param incarnation
  *   the incarnation to enrich
  * @tparam E
  *   the incarnation of the aggregate system
  */
abstract class MacroSwarmSupport[E <: MacroSwarmSupport.Dependency](val incarnation: E)
    extends FastBlocks[E]
    with CoreMovement[E]
    with CoordinatedMovement[E]
    with LeaderBasedMovement[E]
    with CollectivePlanner[E]
    with Consensus[E] {}

object MacroSwarmSupport {

  /** This type represents the dependencies of the MacroSwarmSupport trait. It is basically an Incarnation with the
    * StandardLibrary (G, S, C, etc.) and the RichLanguage (hood operator and so on).
    */
  type Dependency = Incarnation with StandardLibrary with RichLanguage
}
