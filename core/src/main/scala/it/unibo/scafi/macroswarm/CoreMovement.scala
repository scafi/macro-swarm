package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D
import it.unibo.scafi.space.pimp.PimpPoint3D

import scala.concurrent.duration.FiniteDuration

/** This trait contains the basic movement libraries for the MacroSwarm. It includes GPS based movement and movement
  * based on the standard sensors.
  * @tparam E
  *   the incarnation of the aggregate system
  */
trait CoreMovement[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  /** This trait contains the basic movement libraries for the MacroSwarm. It includes movements like standStill,
    * brownian, maintainTrajectory, maintainUntil, obstacleAvoidance.
    */
  trait BaseMovementLib {
    self: StandardSensors with AggregateProgram with TimeUtils =>

    /** No movement, i.e., stand still.
      * @return
      *   the zero vector
      */
    def standStill(): Point3D = Point3D.Zero

    /** Brownian movement, i.e., random movement.
      * @param scale
      *   the scale of the movement, i.e., the multiplier of the random vector
      * @return
      *   the random vector
      */
    def brownian(scale: Double = 1.0): Point3D =
      randomInNegativeUnitSphere(scale)

    /** Maintains a trajectory for a given time, then changes it.
      * @param velocityGenerator
      *   the generator of the velocity
      * @param time
      *   the time after which the trajectory is changed
      * @return
      */
    def maintainTrajectory(velocityGenerator: => Point3D)(time: FiniteDuration): Point3D =
      rep(velocityGenerator)(previousVelocity => mux(impulsesEvery(time))(velocityGenerator)(previousVelocity))

    /** Maintains a trajectory until a condition is met, then it became zero.
      * @param velocity
      *   the velocity to maintain
      * @param condition
      *   the condition to be met
      * @return
      */
    def maintainUntil(velocity: Point3D)(condition: Boolean): Point3D =
      mux(condition)(Point3D.Zero)(velocity)

    /** Avoids obstacles creating a repulsive force from them.
      * @param obstacles
      *   the obstacles to avoid expressed in term of the direction from the device
      * @param minDistance
      *   the minimum distance from the obstacles
      * @param distanceWeightNormalization
      * @return
      */
    def obstacleAvoidance(
        obstacles: Seq[Point3D],
        minDistance: Double,
        distanceWeightNormalization: Double = 100
    ): Point3D = {
      -obstacles
        .map(obstacleDirection => (minDistance - obstacleDirection.module, obstacleDirection.normalize))
        .filter(_._1 > 0)
        .map { case (magnitude, direction) =>
          direction * (magnitude / distanceWeightNormalization)
        }
        .foldLeft(Point3D.Zero)(_ + _)
    }

    private def randomInNegativeUnitSphere(scale: Double): Point3D = {
      val x = randomGenerator().nextDouble() * 2 - 1
      val y = randomGenerator().nextDouble() * 2 - 1
      val z = randomGenerator().nextDouble() * 2 - 1
      Point3D(x * scale, y * scale, z * scale)
    }
  }

  /** This trait contains the basic movement based of GPS. The movements includes goto, explore, isClose. */
  trait GPSMovement {
    self: StandardSensors with AggregateProgram with TimeUtils =>

    /** this function is used to go to a destination.
      * @param destination
      *   the position to reach
      * @param maxVelocity
      *   the maximum velocity
      * @return
      *   the velocity to reach the destination
      */
    def goto(destination: Point3D, maxVelocity: Double = 1): Point3D = {
      val distance = currentPosition().distance(destination)
      val direction = (destination: Point3D) - (currentPosition: Point3D)
      val velocity = direction * (1 / distance)
      velocity * maxVelocity
    }

    /** this function is used to explore the space, i.e., to move randomly in the space.
      * @param minBound
      *   the minimum bound of the space, i.e., the minimum point of the space
      * @param maxBound
      *   the maximum bound of the space, i.e., the maximum point of the space
      * @param maxVelocity
      *   the maximum velocity
      * @return
      *   the velocity to reach the destination
      */
    def explore(minBound: Point3D, maxBound: Point3D, maxVelocity: Double): Point3D = {
      def randomInBound(): Point3D = {
        val x = randomGenerator().nextDouble() * (maxBound.x - minBound.x) + minBound.x
        val y = randomGenerator().nextDouble() * (maxBound.y - minBound.y) + minBound.y
        val z = randomGenerator().nextDouble() * (maxBound.z - minBound.z) + minBound.z
        Point3D(x, y, z)
      }

      val goal = rep(randomInBound())(goal => mux(isClose(goal))(randomInBound())(goal))
      goto(goal, maxVelocity)
    }

    /** This function is used to check if the device is close to a goal.
      * @param goal
      *   the goal to reach
      * @param distance
      *   the distance from the goal
      * @return
      *   true if the device is close to the goal, false otherwise
      */
    def isClose(goal: Point3D, distance: Double = 10): Boolean =
      currentPosition().distance(goal) <= distance
  }

}
