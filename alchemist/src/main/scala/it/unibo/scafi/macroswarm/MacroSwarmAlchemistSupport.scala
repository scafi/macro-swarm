package it.unibo.scafi.macroswarm
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
object MacroSwarmAlchemistSupport extends MacroSwarmSupport(ScafiIncarnationForAlchemist) {
  implicit override def ordering: Ordering[Int] = Ordering.Int
}
