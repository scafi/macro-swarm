package it.unibo.scafi.space

/** This package contains the pimping of the Point3D class. It helps in using Point3D in the movement library. */
package object pimp {
  implicit class PimpPoint3D(p: Point3D) {

    /** Multiplies the point by a scalar.
      * @param alpha
      *   the scalar to multiply the point by.
      * @return
      *   the point multiplied by the scalar.
      */
    def *(alpha: Double): Point3D = Point3D(p.x * alpha, p.y * alpha, p.z * alpha)

    /** Divides the point by a scalar.
      * @param alpha
      *   the scalar to divide the point by.
      * @return
      *   the point divided by the scalar.
      */
    def /(alpha: Double): Point3D = Point3D(p.x / alpha, p.y / alpha, p.z / alpha)

    /** Computes the difference between two points.
      * @param p2
      *   the point to subtract.
      * @return
      *   the difference between the two points.
      */
    def -(p2: Point3D): Point3D = Point3D(p.x - p2.x, p.y - p2.y, p.z - p2.z)

    /** Computes the module of the point.
      * @return
      *   the module of the point.
      */
    def module: Double = Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)

    /** Normalizes the point, that is, it divides the point by its module.
      * @return
      *   the normalized point.
      */
    def normalize: Point3D = if (p == Point3D.Zero) { p }
    else { p / p.module }

    /** Negates the point, that is, it returns the point with the opposite direction.
      * @return
      *   the negated point.
      */
    def unary_- : Point3D = Point3D(-p.x, -p.y, -p.z)

    /** Computes the cross product between two points, that is the vector orthogonal to the plane defined by the two
      * @param other
      *   the other point.
      * @return
      *   the cross product between the two points.
      */
    def crossProduct(other: Point3D): Point3D = Point3D(
      p.y * other.z - p.z * other.y,
      p.z * other.x - p.x * other.z,
      p.x * other.y - p.y * other.x
    )

    /** Computes the perpendicular vector to the point.
      * @return
      *   the perpendicular vector to the point.
      */
    def perpendicular: Point3D = p.crossProduct(Point3D(0, 0, 1))

    /** Computes the angle of the point.
      * @return
      */
    def angle: Double = Math.atan2(p.y, p.x)

    /** Rotates the point by a given radiant.
      * @param radiant
      *   the radiant to rotate the point by.
      * @return
      *   the rotated point.
      */
    def rotate(radiant: Double): Point3D = {
      Point3D(
        p.x * Math.cos(radiant) - p.y * Math.sin(radiant),
        p.x * Math.sin(radiant) + p.y * Math.cos(radiant),
        p.z
      )
    }
  }

  implicit class RichDoublePointContext(p: Double) {

    /** Divides a double by a point.
      * @param p2
      *   the point to divide the double by.
      * @return
      *   the double divided by the point.
      */
    def /(p2: Point3D): Point3D = Point3D(p / p2.x, p / p2.y, p / p2.z)
  }
}
