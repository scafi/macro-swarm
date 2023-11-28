package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D

/** Module that provides a way to plan the movement of a swarm.
  * @tparam E
  *   the incarnation of the aggregate program
  */
trait CollectivePlanner[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  /** The plan library.
    *
    * It is based on the concept of plan, which is a couple of computation and condition. The computation is a function
    * that returns the velocity of the agent.
    *
    * The condition is a function that returns a boolean value that indicates if the plan is still valid. The plan is
    * executed until the condition is false.
    *
    * The plan is selected by the leader of the swarm and broadcasted to the other agents.
    */
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

    /** A plan is a couple of computation and condition.
      * @param computation
      *   the computation that returns the velocity of the agent
      * @param condition
      *   the condition that indicates if the plan is still valid
      */
    case class Plan(computation: () => Point3D, condition: () => Boolean)

    /** The plan executor, i.e., the component that executes the plans.
      * @param plans
      *   the plans to execute, in order
      * @param repeated
      *   indicates if the plans should be repeated
      */
    class PlanExecutor(plans: Seq[Plan], repeated: Boolean = false) {
      def run(lead: Boolean): Point3D = {
        rep((0, Point3D.Zero)) { case (oldPlan, _) =>
          val planId = broadcast(lead, oldPlan)
          branch(plans.length > planId) {
            val conditions = plans.map(_.condition())
            val velocities = plans.map(_.computation())
            val condition = conditions(planId)
            val velocity = velocities(planId)
            (mux(condition && lead)(planId + 1)(planId), velocity)
          }(
            (
              if (repeated) { 0 }
              else { planId },
              Point3D.Zero
            )
          )
        }
      }._2
    }

    /** Builder for plans. It follows the step builder pattern.
      * @param velocity
      *   the velocity of the plan, i.e., the computation that returns the velocity of the agent
      */
    class PlanBuilder(velocity: () => Point3D) {

      /** defines the condition of the plan, i.e., the condition that indicates if the plan is completed
        * @param condition
        *   the condition that indicates if the plan is completed
        * @return
        *   the plan
        */
      def endWhen(condition: => Boolean): Plan = Plan(velocity, () => condition)
    }

    /** Starts the definition of a plan. Example of usage:
      * {{{
      *  planMovementLib.plan(velocity = 1.0) endWhen (currentPosition === Point3D.Zero)
      * }}}
      * @param velocity
      *   the velocity of the plan, i.e., the computation that returns the velocity of the agent
      * @return
      *   the plan builder
      */
    def plan(velocity: => Point3D): PlanBuilder = new PlanBuilder(() => velocity)

    /** Starts the definition of a plan. Example of usage: {{{ execute.once( planMovementLib.plan(velocity = 1.0)
      * endWhen (currentPosition === Point3D.Zero) }
      */
    object execute {

      /** Executes the plans once.
        * @param plans
        *   the plans to execute
        * @return
        *   the plan executor
        */
      def once(plans: Plan*): PlanExecutor = new PlanExecutor(plans)

      /** Repeats the plans forever, starting from the first one and going on. When it reaches the last one, it starts
        * again from the first one.
        * @param plans
        *   the plans to execute
        * @return
        *   the plan executor
        */
      def repeat(plans: Plan*): PlanExecutor = new PlanExecutor(plans, true)
    }
  }

}
