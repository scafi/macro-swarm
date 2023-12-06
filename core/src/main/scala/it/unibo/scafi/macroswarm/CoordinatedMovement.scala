package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D
import it.unibo.scafi.space.pimp._

/** This trait provides a set of methods to implement coordinated movement algorithms.
  *
  * @tparam E
  */
trait CoordinatedMovement[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  /** This trait provides a set of methods to implement flocking algorithms. */
  trait FlockLib {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare =>

    /** Flocking model based on vicsek algorithm: https://it.wikipedia.org/wiki/Modello_di_Vicsek
      * @param velocity
      *   the current velocity since it is based on a temporal model
      * @param neighbouringQuery
      *   the query to get the neighbours
      * @param alpha
      *   the alpha parameter of the model
      * @param epsilon
      *   the epsilon parameter of the model
      * @return
      *   the new velocity
      */
    def vicsek(
        velocity: Point3D,
        neighbouringQuery: NeighbouringQuery,
        alpha: Double = 0.1,
        epsilon: Double = 0.1
    ): Point3D = {
      val neighbours = neighbouringQuery.queryLocal(velocity)
      val averageVelocity = neighbours.values.reduce(_ + _) / neighbours.size.toDouble
      val randomPerturbation = brownian(epsilon)
      velocity * (1 - alpha) + averageVelocity * alpha + randomPerturbation
    }

    /** The flocking algorithm based on the Cucker-Smale model: https://arxiv.org/abs/2010.10693
      * @param velocity
      *   the current velocity since it is based on a temporal model
      * @param neighbouringQuery
      *   the query to get the neighbours
      * @param strength
      *   the strength of the interaction
      * @param epsilon
      *   the epsilon parameter of the model
      * @return
      *   the new velocity
      */
    def cuckerSmale(
        velocity: Point3D,
        neighbouringQuery: NeighbouringQuery,
        strength: Double,
        epsilon: Double = 0.1
    ): Point3D = {
      val neighbours = neighbouringQuery.query(velocity, 0.0, (d: Double) => nbrRange() + d).filter(_._1 != mid())
      val perturbation = brownian(epsilon)
      if (neighbours.isEmpty) {
        velocity + perturbation
      } else {
        neighbours
          .map { case (_, (velocityNeighbour, distance)) => (velocityNeighbour - velocity) * (strength / distance) }
          .foldLeft(velocity + perturbation)(_ + _)
      }
    }

    /** The flocking algorithm based on the Reynolds model: https://www.red3d.com/cwr/boids/
      * @param velocity
      *   the current velocity since it is based on a temporal model
      * @param visionRange
      *   the vision range of the agent
      * @param separationRange
      *   the separation range of the agent
      * @param separationFactor
      *   the separation factor of the agent
      * @param alignFactor
      *   the align factor of the agent
      * @param cohesionFactor
      *   the cohesion factor of the agent
      * @return
      */
    def reynold(
        velocity: Point3D,
        visionRange: NeighbouringQuery,
        separationRange: NeighbouringQuery,
        separationFactor: Double,
        alignFactor: Double,
        cohesionFactor: Double
    ): Point3D = {
      val separationForce = separation(velocity, separationRange)
      val alignForce = align(velocity, visionRange)
      val cohesionForce = cohesion(velocity, visionRange)
      (separationForce.normalize * separationFactor + alignForce.normalize * alignFactor + cohesionForce.normalize * cohesionFactor) + velocity
    }

    /** The seperation logic based on the Reynolds model: https://www.red3d.com/cwr/boids/
      * @param velocity
      *   the current velocity since it is based on a temporal model
      * @param neighborhood
      *   query to get the neighbours
      * @return
      *   the new velocity
      */
    def separation(velocity: Point3D, neighborhood: NeighbouringQuery): Point3D = {
      val distances = neighborhood.queryNeighborhood(Point3D.Zero, distance => nbrVector() + distance).withoutMe()
      separationFromDistancesVector(velocity, distances.values.map(Point3D.Zero - _))
    }

    /** The align logic based on the Reynolds model: https://www.red3d.com/cwr/boids/
      * @param velocity
      *   the current velocity since it is based on a temporal model
      * @param neighborhood
      *   the query to get the neighbours
      * @return
      *   the new velocity
      */
    def align(velocity: Point3D, neighborhood: NeighbouringQuery): Point3D = {
      val neighbourhood = neighborhood.queryLocal(velocity).withoutMe()
      if (neighbourhood.isEmpty) {
        Point3D.Zero
      } else {
        (neighbourhood.values.reduce(_ + _) / neighbourhood.size.toDouble).normalize - velocity
      }
    }

    /** The cohesion logic based on the Reynolds model: https://www.red3d.com/cwr/boids/
      * @param velocity
      *   the current velocity since it is based on a temporal model
      * @param neighborhood
      *   the query to get the neighbours
      * @return
      *   the new velocity
      */
    def cohesion(velocity: Point3D, neighborhood: NeighbouringQuery): Point3D = {
      val distances: Map[ID, Point3D] =
        neighborhood.queryNeighborhood(Point3D.Zero, distance => nbrVector() + distance).withoutMe()
      if (distances.isEmpty) {
        Point3D.Zero
      } else {
        (distances.values.reduce(_ + _) / distances.size.toDouble).normalize - velocity
      }
    }

    private def separationFromDistancesVector(velocity: Point3D, distances: Iterable[Point3D]): Point3D = {
      if (distances.isEmpty) {
        Point3D.Zero
      } else {
        val separationForce = distances.map(point => point).reduce(_ + _) / distances.size
        separationForce.normalize - velocity
      }
    }

    /** This trait define a query to get information about the neighbours. */
    trait NeighbouringQuery {

      /** Query the neighbours about a local value (e.g., the velocity).
        * @param local
        *   the local value to gather
        * @tparam L
        *   the type of the local value
        * @return
        *   a map where the key is the id of the neighbour and the value is the local value
        */
      def queryLocal[L](local: L): Map[ID, L] = query(local, (), (_: Unit) => ()).view.mapValues(_._1).toMap

      /** Query the neighbours about a neighborhood value (e.g., the distance) and an accumulation function (e.g., the
        * sum).
        * @param center
        * @param accumulation
        * @tparam A
        * @return
        */
      def queryNeighborhood[A](center: A, accumulation: A => A): Map[ID, A] =
        query((), center, accumulation).view.mapValues(_._2).toMap

      /** Query the neighbours about a local value (e.g., the velocity) and a neighborhood value (e.g., the distance)
        * and an accumulation function (e.g., the
        * @param local
        *   the local value to gather
        * @param valueAtCenter
        *   the value at the center of the neighborhood
        * @param accumulation
        *   the accumulation function
        * @tparam L
        *   the type of the local value
        * @tparam A
        *   the type of the neighborhood value
        * @return
        *   a map where the key is the id of the neighbour and the value is a tuple with the local value and the
        */
      def query[L, A](local: L, valueAtCenter: A, accumulation: A => A): Map[ID, (L, A)]
    }

    /** One hop strategy, i.e., the standard model */
    case object OneHopNeighbourhood extends NeighbouringQuery {
      override def query[L, A](local: L, valueAtCenter: A, accumulation: A => A): Map[ID, (L, A)] =
        includingSelf.reifyField((nbr(local), accumulation(valueAtCenter)))
    }

    /** One hop strategy constrained by the distance */
    case class OneHopNeighbourhoodWithinRange(range: Double) extends NeighbouringQuery {
      override def query[L, A](local: L, valueAtCenter: A, accumulation: A => A): Map[ID, (L, A)] =
        includingSelf
          .reifyField((nbr(local), accumulation(valueAtCenter), nbrRange()))
          .filter(_._2._3 < range)
          .view
          .mapValues { case (local, acc, _) => (local, acc) }
          .toMap
    }

    case class OneHopNeighbourhoodNearestN(k: Int) extends NeighbouringQuery {
      override def query[L, A](local: L, valueAtCenter: A, accumulation: A => A): Map[ID, (L, A)] =
        includingSelf
          .reifyField((nbr(local), accumulation(valueAtCenter), nbrRange()))
          .toList
          .sortBy(_._2._3)
          .map { case (id, (local, acc, _)) => id -> (local, acc) }
          .take(k)
          .toMap
    }

    /** Radius based strategy based on processes */
    case class RadiusBasedQuery(radius: Double) extends NeighbouringQuery {
      override def query[L, A](local: L, valueAtCenter: A, accumulation: A => A): Map[ID, (L, A)] =
        sspawn2[ID, Unit, Map[ID, (L, A)]](
          id =>
            (_: Unit) => {
              val source = mid() == id
              val potential = fastGradient(source, nbrRange)
              val accumValue: A = GAlongWithShare[A](potential, valueAtCenter, accumulation, nbrRange)
              mux(potential < radius) {
                val result = CWithShare[Double, Map[ID, (L, A)]](
                  potential,
                  (acc, local) => acc ++ local,
                  Map((mid(), (local, accumValue))),
                  Map.empty
                )
                POut(result, SpawnInterface.OutputStatus)
              } {
                POut(Map.empty, SpawnInterface.ExternalStatus)
              }
            },
          Set(mid),
          ()
        ).getOrElse(mid(), Map.empty)
    }

    /** A type classes for enrich the map */
    implicit class MapExcludingMe[I <: ID, V](map: Map[I, V]) {

      /** This function returns the map without the current device.
        * @return
        *   the map without the current device
        */
      def withoutMe(): Map[I, V] = map.filter(_._1 != mid())
    }

  }
}
