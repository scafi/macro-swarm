package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D
import it.unibo.scafi.space.pimp.PimpPoint3D

import scala.concurrent.duration.FiniteDuration

trait CoreMovement[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  trait BaseMovementLib {
    self: StandardSensors with AggregateProgram with TimeUtils =>

    def standStill(): Point3D = Point3D.Zero

    def brownian(scale: Double = 1.0): Point3D =
      randomInNegativeUnitSphere(scale)

    def maintainTrajectory(velocityGenerator: => Point3D)(time: FiniteDuration): Point3D =
      rep(velocityGenerator)(previousVelocity => mux(impulsesEvery(time))(velocityGenerator)(previousVelocity))

    def maintainUntil(velocity: Point3D)(condition: Boolean): Point3D =
      mux(condition)(Point3D.Zero)(velocity)

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
    /*-(obstacles
        .minByOption(_.module)
        .map(distance => distance.normalize * (weight / distance.module))
        .getOrElse(Point3D.Zero): Point3D)*/

    private def randomInNegativeUnitSphere(scale: Double): Point3D = {
      val x = randomGenerator().nextDouble() * 2 - 1
      val y = randomGenerator().nextDouble() * 2 - 1
      val z = randomGenerator().nextDouble() * 2 - 1
      Point3D(x * scale, y * scale, z * scale)
    }
  }

  trait GPSMovement {
    self: StandardSensors with AggregateProgram with TimeUtils =>

    def goto(destination: Point3D, maxVelocity: Double = 1): Point3D = {
      val distance = currentPosition().distance(destination)
      val direction = (destination: Point3D) - (currentPosition: Point3D)
      val velocity = direction * (1 / distance)
      velocity * maxVelocity
    }

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

    def isClose(goal: Point3D, distance: Double = 10): Boolean =
      currentPosition().distance(goal) <= distance
  }

}
