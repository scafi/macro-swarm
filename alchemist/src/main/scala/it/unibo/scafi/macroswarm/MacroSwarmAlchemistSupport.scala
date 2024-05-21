package it.unibo.scafi.macroswarm
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.scafi.space.Point3D
object MacroSwarmAlchemistSupport extends MacroSwarmSupport(ScafiIncarnationForAlchemist) {
  implicit override def ordering: Ordering[Int] = Ordering.Int

  trait MacroSwarmProgram extends incarnation.AggregateProgram {
    self: incarnation.StandardSensors with incarnation.ScafiAlchemistSupport =>
    override def main(): Any = actuate(movementLogic())

    protected def movementLogic(): Point3D
    private def actuate(velocity: Point3D): Unit = {
      val previousPosition = alchemistEnvironment.getPosition(alchemistEnvironment.getNodeByID(mid()))
      val target = previousPosition.plus(Array(velocity.x, velocity.y))
      target.asInstanceOf[Position[_]]
      node.put("velocity", Array(velocity.x, velocity.y))
      node.put("destination", target)
    }
  }
}
