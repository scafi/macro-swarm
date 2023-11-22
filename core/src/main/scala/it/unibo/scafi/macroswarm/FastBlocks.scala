package it.unibo.scafi.macroswarm
trait FastBlocks[E <: MacroSwarmSupport.Dependency] {
  outer: MacroSwarmSupport[E] =>

  import incarnation._
  trait ProcessFix extends CustomSpawn {
    self: AggregateProgram =>
    override def runOnSharedKeysWithShare[K, A, R](process: K => (R, Boolean), params: Set[K]): Map[K, R] = {
      share(Map[K, R]())((_, nbr) => {
        includingSelf
          .unionHoodSet(nbr().keySet ++ params)
          .mapToValues(k => exportConditionally(process.apply(k)))
          .collectValues[R] { case (r, true) => r }
      })
    }

    override def sspawn2[K, A, R](process: K => A => POut[R], params: Set[K], args: A): Map[K, R] =
      spawn2[K, A, Option[R]](process.map(handleTermination).map(handleOutput), params, args)
        .collectValues { case Some(p) => p }
  }

  /** This trait provides a set of blocks that can be used to implement the fast gradient algorithm and share operator.
    * These are twice as fast as the standard blocks.
    */
  trait BlocksWithShare {
    self: AggregateProgram with StandardSensors with BlocksWithGC with ProcessFix =>

    /** Tha gradient algorithm used to compute the distance from the source. Internally it uses the share operator.
      * @param source
      *   the source of the gradient (i.e., the device that has distance 0 from itself)
      * @param metric
      *   the metric used to compute the distance between two devices
      * @return
      *   the distance from the source
      */
    def fastGradient(source: Boolean, metric: Metric = nbrRange): Double = {
      share(Double.PositiveInfinity) { case (_, nbrg) =>
        mux(source)(0.0)(minHoodPlus(nbrg() + metric()))
      }
    }

    /** A generalize gradient operator to accumulate values along a potential field.
      * @param gradient
      *   the gradient value (i.e., the potential field)
      * @param field
      *   the field to be accumulated
      * @param accumulator
      *   the accumulator function
      * @param metric
      *   the metric used to compute the distance between two devices
      * @tparam V
      *   the type of the field
      * @return
      *   the accumulated field along the gradient
      */
    def GAlongWithShare[V](gradient: Double, field: V, accumulator: V => V, metric: Metric = nbrRange): V = {
      share(field) { case (_, nbrField) =>
        mux(gradient == 0.0)(field) {
          excludingSelf.minHoodSelector[Double, V](nbr(gradient) + metric())(accumulator(nbrField())).getOrElse(field)
        }
      }
    }

    /** The C operator (collect-cast) used to collect values along a potential field. This means that can be used to
      * collect values into leader nodes.
      *
      * @param potential
      *   the potential field (i.e., the result of a fast gradient)
      * @param accumulator
      *   the accumulator function
      * @param local
      *   the local value
      * @param Null
      *   the null value (i.e. acc(Null, v) = v)
      * @tparam P
      *   the type of the potential field
      * @tparam V
      *   the type of the value
      * @return
      *   the collected values along the potential field
      */
    def CWithShare[P: Builtins.Bounded, V](potential: P, accumulator: (V, V) => V, local: V, Null: V): V =
      share(local) { (_, nbrv) =>
        accumulator(
          local,
          foldhood(Null)(accumulator) {
            mux(nbr(findParent(potential)) == mid())(nbrv())(nbr(Null))
          }
        )
      }

    /** A generalized version of the gradient operator that can be used to accumulate values along the gradient
      * @param source
      *   the source of the gradient (i.e., the device that has distance 0 from itself)
      * @param field
      *   the field to be accumulated
      * @param acc
      *   the accumulator function
      * @param metric
      *   the metric used to compute the distance between two devices
      * @tparam V
      *   the type of the field
      * @return
      *   the accumulated field along the gradient
      */
    def GWithShare[V](source: Boolean, field: V, acc: V => V, metric: Metric = nbrRange): V =
      share((Double.MaxValue, field)) { case (_, nbrvalues) =>
        mux(source) {
          (0.0, field)
        } {
          excludingSelf
            .minHoodSelector(nbrvalues()._1 + metric())((nbrvalues()._1 + metric() + metric(), acc(nbrvalues()._2)))
            .getOrElse((Double.PositiveInfinity, field))
        }
      }._2

    /** A generalized version of the broadcast operator that can be used to broadcast values along the gradient
      * @param g
      *   the gradient value (i.e., the potential field)
      * @param value
      *   the value to be broadcasted
      * @param metric
      *   the metric used to compute the distance between two devices
      * @tparam V
      *   the type of the value
      * @return
      *   the broadcasted value along the gradient
      */
    def broadcastAlongWithShare[V](g: Double, value: V, metric: Metric = nbrRange) =
      GAlongWithShare(g, value, identity[V], metric)

    /** Sparse choice algorithm used to compute sparse leader in a network.
      * @param grain
      *   the grain used to define the distance threshold
      * @param metric
      *   the metric used to compute the distance between two devices
      * @return
      *   true if the device is a leader, false otherwise
      */
    def SWithShare(grain: Double, metric: Metric): Boolean =
      breakUsingUidsWithShare(randomUid, grain, metric)

    /** Generates a field of random unique identifiers.
      *
      * @return
      *   a tuple where the first element is a random number, end the second element is the device identifier to ensure
      *   uniqueness of the field elements.
      */
    private def randomUid: (Double, ID) = rep((nextRandom(), mid())) { v =>
      (v._1, mid())
    }

    /** This function is used to break the symmetry between devices with the same distance from the source.
      * @param uid
      *   the unique identifier of the device composed of the distance and the device identifier
      * @param grain
      *   the grain used to define the distance threshold
      * @param metric
      *   the metric used to compute the distance between two devices
      * @return
      *   true if the device wins the competition, false otherwise
      */
    def breakUsingUidsWithShare(uid: (Double, ID), grain: Double, metric: Metric): Boolean =
      uid == (share((uid, 0.0)) { (lead, nbrInfo) =>
        val dist = GWithShare[Double](uid == lead._1, 0, (_: Double) + metric(), metric)
        (distanceCompetitionWithShare(nbrInfo, lead, grain, metric), dist)
      }._1)

    /** This function is used to compute the distance competition between two devices. The competition is based on the
      * distance between the two devices and the grain. The grain is a parameter that is used to define the distance
      * threshold. If the distance between the two devices is less than the grain, the competition is based on the
      * distance. Otherwise, the competition is based on the distance between the device and the grain.
      * @param nbrInfo
      *   the information of the neighbor computed through the share operator
      * @param localInfo
      *   the information of the local device
      * @param grain
      *   the grain used to define the distance threshold
      * @param metric
      *   the metric used to compute the distance between two devices
      * @return
      *   the information of the device that wins the competition (i.e., the leader)
      */
    def distanceCompetitionWithShare(
        nbrInfo: () => ((Double, ID), Double),
        localInfo: ((Double, ID), Double),
        grain: Double,
        metric: Metric
    ): (Double, ID) = {
      val inf: (Double, ID) = (Double.PositiveInfinity, localInfo._1._2)
      mux(localInfo._2 > grain) {
        localInfo._1
      } {
        mux(localInfo._2 >= (0.5 * grain)) {
          inf
        } {
          minHood {
            mux(nbrInfo()._2 + metric() >= 0.5 * grain) {
              nbr(inf)
            } {
              nbrInfo()._1
            }
          }
        }
      }
    }
  }
}
