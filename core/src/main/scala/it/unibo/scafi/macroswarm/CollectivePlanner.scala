package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D

/** Module that provides a way to plan the movement of a swarm. It is based on the concept of plan, which is a couple of
  * computation and condition. The computation is a function that returns the velocity of the agent. The condition is a
  * function that returns a boolean value that indicates if the plan is still valid. The plan is executed until the
  * condition is false. The plan is selected by the leader of the swarm and broadcasted to the other agents.
  * @tparam E
  *   the incarnation of the aggregate program
  */
trait CollectivePlanner[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  trait PlanMovementLib {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare
      with FlockLib =>

    case class Plan(computation: () => Point3D, condition: () => Boolean)

    class PlanExecutor(plans: Seq[Plan], repeated: Boolean = false) {
      def run(lead: Boolean): Point3D = {
        rep((0, Point3D.Zero)) { case (oldPlan, _) =>
          val planId = broadcast(lead, oldPlan)
          branch(plans.length > planId) {
            val conditions = plans.map(_.condition())
            val velocities = plans.map(_.computation())
            val condition = conditions(planId)
            val velocity = velocities(planId)
            val (_, stable) = rep((-1, false)) { case (oldNeigh, _) =>
              val current = foldhood(0)(_ + _)(1)
              (current, current == oldNeigh)
            }
            (mux(condition && lead)(planId + 1)(planId), velocity)
          }(
            (
              if (repeated) {
                0
              } else {
                planId
              },
              Point3D.Zero
            )
          )
        }
      }._2
    }

    def plan(velocity: => Point3D): PlanBuilder = new PlanBuilder(() => velocity)

    class PlanBuilder(velocity: () => Point3D) {
      def endWhen(condition: => Boolean): Plan = Plan(velocity, () => condition)
    }

    object execute {
      def once(plans: Plan*): PlanExecutor = new PlanExecutor(plans)

      def repeat(plans: Plan*): PlanExecutor = new PlanExecutor(plans, true)
    }
  }

}
