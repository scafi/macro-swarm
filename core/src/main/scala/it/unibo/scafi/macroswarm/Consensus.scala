package it.unibo.scafi.macroswarm

import it.unibo.scafi.space.Point3D

/** Module that provides a way to agree to a certain value.
  * @tparam E
  *   the incarnation of the aggregate program
  */
trait Consensus[E <: MacroSwarmSupport.Dependency] {
  _: MacroSwarmSupport[E] =>

  import incarnation._

  trait ConsensusLib {
    self: AggregateProgram
      with StandardSensors
      with FieldUtils
      with TimeUtils
      with BaseMovementLib
      with CustomSpawn
      with BlocksWithGC
      with BlocksWithShare
      with FlockLib =>
    def consensusOn(choices: Int, importanceEval: ID => Double): Int =
      consensusWithPreferences(computeInitialVector(choices), importanceEval)

    def consensusWithPreferences(preferences: List[Double], importanceEval: ID => Double): Int = {
      val vector = rep(softmaxNormalization(preferences)) { preferences =>
        val choice = oneHotVector(preferences.size, preferenceChoice(preferences), certaintyLevel(preferences))
        val neighbourhoodPreferences = excludingSelf.reifyField(nbr(choice))

        def sumAllOverIndex(i: Int): Double = neighbourhoodPreferences.map { case (id, vector) =>
          vector(i) * importanceEval(id)
        }.sum

        val normalization =
          preferences.zipWithIndex.map { case (value, i) =>
            value + sumAllOverIndex(i)
          }.sum
        val result = preferences.zipWithIndex.map { case (value, i) =>
          (value + sumAllOverIndex(i)) / normalization
        }
        mux(neighbourhoodPreferences.isEmpty)(preferences)(lowNumberFilter(result))
      }
      preferenceChoice(vector)
    }

    def computeInitialVector(howMany: Int): List[Double] = softmaxNormalization(randomChoices(howMany))

    private def randomChoices(howMany: Int): List[Double] =
      LazyList.fill(howMany)(alchemistRandomGen.nextDouble()).toList

    private def softmaxNormalization(vector: List[Double]): List[Double] = {
      val exps = vector.map(math.exp)
      val sum = exps.sum
      exps.map(_ / sum)
    }

    private def entropyVector(vector: List[Double]): Double = {
      val log2 = (x: Double) => math.log(x) / math.log(2)
      -vector.map(x => x * log2(x)).sum
    }

    private def certaintyLevel(vector: List[Double]): Double = 1 / (entropyVector(vector) + 1)

    def preferenceChoice(vector: List[Double]): Int = vector.zipWithIndex.maxBy(_._1)._2

    def oneHotVector(n: Int, one: Int, value: Double): List[Double] = List.fill(n)(0.0).updated(one, value)

    def lowNumberFilter(vector: List[Double]): List[Double] = vector.map(x => if (x < 0.0000001) 0.0000001 else x)
  }
}
