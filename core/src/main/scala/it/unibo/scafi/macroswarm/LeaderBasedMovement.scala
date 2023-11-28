package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D
import it.unibo.scafi.space.pimp.PimpPoint3D

trait LeaderBasedMovement[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  implicit def ordering: Ordering[ID]
  trait LeaderBasedLib {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare =>

    def alignWithLeader(source: Boolean, point: Point3D): Point3D =
      GWithShare(source, point, identity[Point3D], nbrRange)

    def sinkAt(source: Boolean): Point3D =
      GWithShare[Point3D](source, Point3D.Zero, vector => vector + nbrVector(), nbrRange).normalize

    def spinAround(center: Boolean): Point3D = {
      val toCenter = sinkAt(center)
      toCenter.crossProduct(Point3D(0, 0, 1))
    }
  }

  trait TeamFormationLib {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare
      with FlockLib
      with LeaderBasedLib =>

    def isTeamFormed(source: Boolean, targetDistance: Double, necessary: Int = 1): Boolean = {
      val potential = fastGradient(source, nbrRange)
      val totalDistance = excludingSelf.reifyField(nbrRange())
      val averageDistance = totalDistance.values.toList.sorted
        .take(necessary)
        .reduceOption(_ + _)
        .map(_ / necessary)
        .getOrElse(Double.PositiveInfinity)
      val isFormed = CWithShare[Double, Boolean](potential, _ && _, averageDistance <= targetDistance, true)
      broadcastAlongWithShare(potential, isFormed, nbrRange)
    }

    def countIn(source: Boolean): Int = {
      val potential = fastGradient(source, nbrRange)
      val count = CWithShare[Double, Int](potential, _ + _, 1, 0)
      broadcastAlongWithShare(potential, count, nbrRange)
    }

    case class Team(leader: ID, isFormed: Boolean, velocity: Point3D) {
      def insideTeam(velocityGenerator: ID => Point3D): Point3D = rep((isFormed, velocity)) { case (formed, _) =>
        branch(!formed)((isFormed, velocity))(align(leader)(k => (true, velocityGenerator(k))))
      }._2
    }
    /*
    def teamFormation(
        targetIntraDistance: Double,
        targetExtraDistance: Double,
        confidence: Double,
        separationWeight: Double,
        necessary: Int = 1
    ): Team = {
      val leader = SWithShare(targetExtraDistance, nbrRange)
      val localLeader = broadcastAlongWithShare(fastGradient(leader, nbrRange), mid(), nbrRange)
      val isFormed = teamFormed(leader, targetIntraDistance + confidence, necessary)
      val velocity = rep(Point3D.Zero)(velocity =>
        branch(!isFormed) {
          (sinkAt(leader) + separation(
            velocity,
            OneHopNeighbourhoodWithinRange(targetIntraDistance)
          ) * separationWeight).normalize
        }(Point3D.Zero)
      )
      Team(localLeader, isFormed, velocity)
    }*/

    def teamFormation(
        targetIntraDistance: Double,
        targetExtraDistance: Double,
        separationWeight: Double,
        condition: Boolean => Boolean
    ): Team = {

      val (leaderId, formed, velocity) = rep((mid(), false, Point3D.Zero)) { case (leaderId, formed, velocity) =>
        mux(!formed) {
          val leader = SWithShare(targetExtraDistance, nbrRange)
          val localLeader = broadcastAlongWithShare(fastGradient(leader, nbrRange), mid(), nbrRange)
          val isFormed = condition(leader)
          val updateVelocity = (sinkAt(leader) + separation(
            velocity,
            OneHopNeighbourhoodWithinRange(targetIntraDistance)
          ) * separationWeight).normalize
          (localLeader, isFormed, updateVelocity)
        } {
          (leaderId, true, Point3D.Zero)
        }
      }
      Team(leaderId.asInstanceOf[ID], formed, velocity)
    }

    def teamFormation(
        center: Boolean,
        targetIntraDistance: Double,
        separationWeight: Double,
        condition: Boolean => Boolean
    ): Team = {
      val (leaderId, formed, velocity) = rep((mid(), false, Point3D.Zero)) { case (leaderId, formed, velocity) =>
        mux(!formed) {
          val leader = center
          val localLeader = broadcastAlongWithShare(fastGradient(leader, nbrRange), mid(), nbrRange)
          val isFormed = condition(leader)
          val updateVelocity = (sinkAt(leader) + separation(
            velocity,
            OneHopNeighbourhoodWithinRange(targetIntraDistance)
          ) * separationWeight).normalize
          (localLeader, isFormed, updateVelocity)
        } {
          (leaderId, true, Point3D.Zero)
        }
      }
      Team(leaderId.asInstanceOf[ID], formed, velocity)
    }
  }

  trait PatternFormationLib extends {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare =>

    def line(
        leader: Boolean,
        distance: Double,
        confidence: Double,
        leaderVelocity: => Point3D = Point3D.Zero
    ): Point3D = {
      val potential = fastGradient(leader)
      val nodes = getNodeInfo(potential)
      val (left, right) = orderedNodes(nodes).splitAt(nodes.size / 2)
      val leftSuggestion = left.zipWithIndex.map { case ((id, velocity), i) =>
        id -> (Point3D(-(i + 1) * distance, 0, 0) + velocity)
      }.toMap
      val rightSuggestions = right.zipWithIndex.map { case ((id, velocity), i) =>
        id -> (Point3D((i + 1) * distance, 0, 0) + velocity)
      }.toMap
      mux(leader)(leaderVelocity) {
        val direction =
          broadcastAlongWithShare(potential, leftSuggestion ++ rightSuggestions, nbrRange)
            .getOrElse(mid().asInstanceOf[ID], Point3D.Zero)
        mux(direction.module < confidence)(Point3D.Zero)(direction.normalize)
      }
    }

    def centeredCircle(
        leader: Boolean,
        radius: Double,
        confidence: Double,
        leaderVelocity: => Point3D = Point3D.Zero
    ): Point3D = {
      val potential = fastGradient(leader)
      val nodes = getNodeInfo(potential)
      val division = (math.Pi * 2) / nodes.size
      val suggestion = orderedNodes(nodes).zipWithIndex.map { case ((id, v), i) =>
        val angle = division * (i + 1)
        id -> (Point3D(math.sin(angle) * radius, math.cos(angle) * radius, 0) + v)
      }.toMap
      mux(leader)(leaderVelocity) {
        val direction =
          broadcastAlongWithShare(potential, suggestion, nbrRange).getOrElse(mid().asInstanceOf[ID], Point3D.Zero)
        mux(direction.module < confidence)(Point3D.Zero)(direction.normalize)
      }
    }

    def vShape(
        leader: Boolean,
        oldVelocity: Point3D,
        distance: Double,
        radius: Double,
        confidence: Double,
        leaderVelocity: Point3D = Point3D.Zero
    ): Point3D = {
      val potential = fastGradient(leader)
      val nodes = getNodeInfo(potential)
      val amount = ((Math.PI * 2) - radius) / 2 // - (Math.PI / 2)
      val leftVersor = oldVelocity.normalize.rotate(amount).rotate(-Math.PI / 2)
      val rightVersor = oldVelocity.normalize.rotate(-amount).rotate(-Math.PI / 2)
      val (left, right) = orderedNodes(nodes).splitAt(nodes.size / 2)
      val leftSuggestion = left.zipWithIndex.map { case ((id, velocity), i) =>
        id -> (leftVersor * distance * -(i + 1) + velocity)
      }.toMap
      val rightSuggestions = right.zipWithIndex.map { case ((id, velocity), i) =>
        id -> (rightVersor * distance * (i + 1) + velocity)
      }.toMap
      mux(leader)(leaderVelocity) {
        val direction =
          broadcastAlongWithShare(potential, leftSuggestion ++ rightSuggestions, nbrRange)
            .getOrElse(mid().asInstanceOf[ID], Point3D.Zero)
        mux(direction.module < confidence)(Point3D.Zero)(direction.normalize)
      }

    }

    def isCircleFormed(source: Boolean, targetDistance: Double, confidence: Double): Boolean = {
      val potential = fastGradient(source, nbrRange)
      val distances = CWithShare[Double, List[Double]](potential, _ ::: _, List(potential), List.empty).filter(_ != 0.0)
      // for all distances, the distance from the leader is between the target distance and the confidence
      val isFormed = distances.forall(d => d > targetDistance - confidence && d < targetDistance + confidence)
      broadcastAlongWithShare(potential, isFormed & distances.nonEmpty, nbrRange)
    }

    private def getNodeInfo(potential: Double): Set[(ID, Point3D)] = {
      val distanceFromLeader = GAlongWithShare[Point3D](potential, Point3D.Zero, v => v + nbrVector(), nbrRange)
      C[Double, Set[(ID, Point3D)]](
        potential,
        (a, b) => a ++ b,
        Set((mid(), distanceFromLeader)),
        Set.empty[(ID, Point3D)]
      )
    }

    private def orderedNodes(nodes: Set[(ID, Point3D)]): List[(ID, Point3D)] = nodes
      .filter(_._1 != mid())
      .toList
      .sortBy(_._1)(ordering)

  }

}
