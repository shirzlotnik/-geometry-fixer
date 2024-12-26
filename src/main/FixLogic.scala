import org.geotools.geometry.jts.JTS.makeValid
import org.locationtech.jts.algorithm.Angle.{angle, angleBetween, angleBetweenOriented, bisector, interiorAngle, toDegrees}
import org.locationtech.jts.geom._

object FixLogic {
  implicit val factory: GeometryFactory = new GeometryFactory()

  def getSlope(head: Coordinate, tail: Coordinate): Double = {
    if (head.x == tail.x) {
      (head.y - tail.y)/(-1 * Double.MinPositiveValue)
    } else {
      (head.y - tail.y)/(head.x - tail.x)
    }
  }

  def fixCoordinatesDuplicates(multiPolygon: MultiPolygon, id: String): MultiPolygon = {
    val fixedPolygons = (0 until multiPolygon.getNumGeometries)
      .map(multiPolygon.getGeometryN(_).asInstanceOf[Polygon])
      .map(p => fixCoordinatesDuplicates(p, id))
      .toArray

    factory.createMultiPolygon(fixedPolygons)
  }

  def fixCoordinatesDuplicates(polygon: Polygon, id: String): Polygon = {
    val boundary = polygon.getBoundary
    val fixedLinearRings = (0 until boundary.getNumGeometries)
      .map(boundary.getGeometryN(_).asInstanceOf[LinearRing])
      .map(lr => finalFix(lr, id))
      .map(lr => fixCoordinatesDuplicates(lr, id))
      //      .map(fixRegularIntersection)
      .toArray

    try {
      if (fixedLinearRings.length == 1) factory.createPolygon(fixedLinearRings.head)
      else if (fixedLinearRings.length > 1) factory.createPolygon(fixedLinearRings.head, fixedLinearRings.tail)
      else polygon
    } catch {
      case e: Exception => println(e.getMessage + " line 39")
        println(polygon)
        println(polygon.toString)
        println(id)
        polygon
    }
  }


  def fixCoordinatesDuplicates(linearRing: LinearRing, id: String): LinearRing = {
    val coordinates = linearRing.getCoordinates
    val geometryFactory = new GeometryFactory()

    val fixedCoords = (0 until coordinates.length - 1)
      .foldLeft[(Array[Coordinate], Double)](Array[Coordinate](), Double.NaN) {
        case ((accCoords, lastSlope), i) =>
          val pureSlope = getSlope(coordinates(i), coordinates(i+1))
          val currSlope = if (pureSlope == Double.PositiveInfinity || pureSlope == Double.NegativeInfinity)
            pureSlope
          else
            BigDecimal(pureSlope).setScale(5, BigDecimal.RoundingMode.HALF_UP).toDouble
          if (i == 0) (accCoords ++ Array(coordinates(i), coordinates(i+1)), currSlope)
          else {
            val currFixedCoords = if (lastSlope == currSlope) accCoords.slice(0, accCoords.length - 1) else accCoords
            (currFixedCoords :+ coordinates(i+1), currSlope)
          }
      }._1

    try {
      val fixedLinearRing = geometryFactory.createLinearRing(fixedCoords)

      fixedLinearRing
    } catch {
      case e: Exception => println(e.getMessage + " line 72")
        println(linearRing)
        println(linearRing.toString)
        println(id)
        linearRing
    }
  }


  def finalFix(linearRing: LinearRing, id: String): LinearRing = {
    val polygon = factory.createPolygon(linearRing)
    val fixedLinearRing = finalFix(polygon, id)
      .getBoundary.getGeometryN(0).asInstanceOf[LinearRing]
    fixedLinearRing
  }


  def finalFix(polygon: Polygon, id: String): Geometry = {
    val repaired = makeValid(polygon, false).toArray(Array[Polygon]()).toList
    val coordinatesArray = repaired.map(_.getCoordinates)

    val polygonCoordinates = polygon.getCoordinates
    val innerCoordinates = polygonCoordinates.slice(1, polygonCoordinates.length - 1)
    val problemCoordinates = innerCoordinates
      .groupBy(c => polygonCoordinates.count(c1 => c1 == c))
      .filter(c => c._1 > 1)
      .map(_._2.head)
      .toArray

    val innerCW = coordinatesArray.foldLeft(Array[Coordinate]())({
      case (prev, curr) =>
        problemCoordinates.foldLeft(prev)({
          case (prev1, curr1) =>
            val indexCurrProblem = curr.indexOf(curr1)
            prev1 ++ (if (indexCurrProblem != -1)
              curr.slice(indexCurrProblem, curr.length - 1) ++ curr.slice(0, indexCurrProblem)
            else Array[Coordinate]())
        })
    })

    val fixedInnerCoordinates = (1 until innerCW.length)
      .foldLeft(Array[Coordinate]())({
      case (prev, index) =>
        val currCoords = innerCW(index)
        prev :+ (if (problemCoordinates.contains(currCoords)) {
          findFixedCoordinate(innerCW(index - 1), innerCW(index))
        } else innerCW(index))
    })

    val fixedPolygonCoordinates = innerCW.head +: fixedInnerCoordinates :+ innerCW.head
    val fixedPolygon = factory.createPolygon(fixedPolygonCoordinates)
    fixedPolygon
  }

  def findFixedCoordinate(c1: Coordinate, c2: Coordinate): Coordinate = {
    val epsilon = 0.001
    val vector = new Coordinate(c1.x - c2.x, c1.y - c2.y)
    val magnitude = Math.sqrt(Math.pow(vector.x, 2) + Math.pow(vector.y, 2))
    val newCoordinate = new Coordinate(c2. x +(epsilon * vector.x / magnitude),
      c2.y + (epsilon * vector.y / magnitude))
    newCoordinate
  }
}
