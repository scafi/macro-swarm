package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D
import it.unibo.scafi.space.pimp.PimpPoint3D

/** This trait provides the libraries for LeaderBasedMovement.
  * @tparam E
  *   the incarnation of the aggregate system
  */
trait LeaderBasedMovement[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  /** It is needed for ordering the IDs of the nodes. Indeed, the ID might be of any type, so we need to provide an
    * ordering for it.
    * @return
    *   the Ordering type class for the IDs
    */
  implicit def ordering: Ordering[ID]

  /** This library provides the basic movement primitives for the leader-based movement. It mainly consist in function
    * for aligning, sinking, and spinning around a leader, i.e., a node that is responsible for the movement of a subset
    * of the nodes. Indeed, the leader-based movements are based of G, therefore they create areas of influence around
    * the leader.
    */
  trait LeaderBasedLib {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare =>

    /** Aligns the node with the leader, i.e., it returns the vector from the node to the leader.
      * @param source
      *   whether the node is the leader or not
      * @param point
      *   the velocity to align with the leader
      * @return
      *   the velocity aligned with the leader
      */
    def alignWithLeader(source: Boolean, velocity: Point3D): Point3D =
      GWithShare(source, velocity, identity[Point3D], nbrRange)

    /** Sink the node towards the leader, i.e., it returns the vector from the node to the leader.
      * @param source
      *   whether the node is the leader or not
      * @return
      *   the vector from the node to the leader
      */
    def sinkAt(source: Boolean): Point3D =
      GWithShare[Point3D](source, Point3D.Zero, vector => vector + nbrVector(), nbrRange).normalize

    /** Spins around the leader, i.e., it returns the vector orthogonal to the vector from the node to the leader.
      *
      * @param center
      *   whether the node is the leader or not
      * @return
      *   the vector orthogonal to the vector from the node to the leader
      */
    def spinAround(center: Boolean): Point3D =
      sinkAt(center).crossProduct(Point3D(0, 0, 1))
  }

  /** This library provides the basic blocks to create logical teams, i.e., a subset of node that have a persistent
    * common goal. One a team is formed, the nodes cannot leave it. Inside a team, there will be a logic influenced by
    * the leader-based movement, i.e., the leader will be responsible for the movement of the team. This library
    * provides a way to create this teams based on intra-distance and extra-distance. The foster is the distance between
    * nodes that are in the same team. The latter is influence of the leader during the formation of the team.
    */
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

    /** A team is a set of nodes that have a common goal. The team is formed when the leader is able to influence the
      * nodes to move towards the goal. The team is persistent, i.e., the nodes cannot leave the team.
      * @param leader
      *   the leader of the team
      * @param isFormed
      *   whether the team is formed or not
      * @param velocity
      *   the velocity of local movement of the team
      */
    case class Team(leader: ID, isFormed: Boolean, velocity: Point3D) {

      /** Returns the velocity of the node inside the team.
        * @param velocityGenerator
        *   the logic applied inside the team, it is a function from the leader ID of the node to the velocity of the
        *   node
        * @return
        *   the velocity of the node inside the team
        */
      def insideTeam(velocityGenerator: ID => Point3D): Point3D = rep((isFormed, velocity)) { case (formed, _) =>
        branch(!formed)((isFormed, velocity))(align(leader)(k => (true, velocityGenerator(k))))
      }._2
    }

    /** The logic of creating a team based on the distance between the nodes. A team is consider formed when all the
      * nodes have the same average distances from their neighbours.
      * @param source
      *   whether the node is the leader or not
      * @param targetDistance
      *   the distance between the nodes in the team
      * @param necessary
      *   the number of nodes to be considered when computing the average distance
      * @return
      *   whether the team is formed or not
      */
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

    /** The logic of creating a team based on the distance between the nodes.
      *
      * In this case, it use internally S to compute leaders based on the extra distance.
      *
      * The condition is a function that takes as input the leader and returns whether the team is formed or not.
      *
      * An example of usage is the following:
      * ```scala
      * teamFormation(80, 300, 0.1, leader => isTeamFormed(leader, 100, 2))
      * ```
      *
      * @param targetIntraDistance
      *   the distance between the nodes in the team
      * @param targetExtraDistance
      *   the area of influence of the leader
      * @param separationWeight
      *   the weight of the separation force
      * @param condition
      *   the condition to be satisfied to consider the team formed
      * @return
      *   whether the team is formed or not
      */
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
          val updateVelocity =
            (sinkAt(leader)
              + separation(velocity, OneHopNeighbourhoodWithinRange(targetIntraDistance))
              * separationWeight).normalize
          (localLeader, isFormed, updateVelocity)
        } {
          (leaderId, true, Point3D.Zero)
        }
      }
      Team(leaderId.asInstanceOf[ID], formed, velocity)
    }

    /** The actual team formation logic. In this case, a leader already exists and it is provided as input. This block
      * is responsible of maintaining the right intra distance between the nodes. The condition is a function that takes
      * as input the leader and returns whether the team is formed or not.
      *
      * An example of usage is the following:
      * ```scala
      * teamFormation(leader, 80, 0.1, leader => isTeamFormed(leader, 100, 2))
      * ```
      * @param center
      *   the leader of the team
      * @param targetIntraDistance
      *   the distance between the nodes in the team
      * @param separationWeight
      *   the weight of the separation force
      * @param condition
      *   the condition to be satisfied to consider the team formed
      * @return
      */
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
