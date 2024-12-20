import org.geotools.geometry.jts.JTS.makeValid
import org.locationtech.jts.algorithm.Angle.{angleBetweenOriented, toDegrees}
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

  def fixCoordinatesDuplicates(multiPolygon: MultiPolygon): MultiPolygon = {
    val fixedPolygons = (0 until multiPolygon.getNumGeometries)
      .map(multiPolygon.getGeometryN(_).asInstanceOf[Polygon])
      .map(fixCoordinatesDuplicates)
      .toArray

    factory.createMultiPolygon(fixedPolygons)
  }

  def fixCoordinatesDuplicates(polygon: Polygon): Polygon = {
    val boundary = polygon.getBoundary
    val fixedLinearRings = (0 until boundary.getNumGeometries)
      .map(boundary.getGeometryN(_).asInstanceOf[LinearRing])
      .map(fixCoordinatesDuplicates)
      .toArray

    if (fixedLinearRings.length == 1) factory.createPolygon(fixedLinearRings.head)
    else if (fixedLinearRings.length > 1) factory.createPolygon(fixedLinearRings.head, fixedLinearRings.tail)
    else polygon
  }


  def fixCoordinatesDuplicates(linearRing: LinearRing): LinearRing = {
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

    val newGeom = geometryFactory.createLinearRing(fixedCoords)
    newGeom
  }

  def fixSelfIntersectOnExistCoordinate(geometry: Geometry): Geometry = {
    geometry match {
      case multiPolygon: MultiPolygon =>
        factory.createMultiPolygon((0 until multiPolygon.getNumGeometries)
          .map(p =>
            fixSelfIntersectOnExistCoordinateLogic(multiPolygon.getGeometryN(p)
              .asInstanceOf[Polygon])).toArray)
      case polygon: Polygon => fixSelfIntersectOnExistCoordinateLogic(polygon)
      case _ => geometry
    }
  }

  def fixSelfIntersectOnExistCoordinateLogic(polygon: Polygon): Polygon = {
    val repaired = makeValid(polygon, false).toArray(Array[Polygon]()).toList

    repaired.tail.foldLeft[Polygon](repaired.head)(
      (acc, curr) => {
        val accCoords = acc.getCoordinates
        val currCoords = curr.getCoordinates
        val currIndex = repaired.indexOf(curr)

        val problemCoord = accCoords.find(currCoords.contains)
        val indexProblem = accCoords.indexOf(problemCoord.get)


        val coordinatesToAddPolygon = (if (currIndex % 2 != 0) {
          currCoords.slice(indexProblem, currCoords.length) ++
            currCoords.slice(0, indexProblem)

        } else {
          (currCoords.slice(0, indexProblem) ++
            currCoords.slice(indexProblem, currCoords.length))
            .filter(c => !(c.x == problemCoord.get.x && c.y == problemCoord.get.y))
        }).filter(c => !(c.x == problemCoord.get.x && c.y == problemCoord.get.y))


        val kakiAngle = try {
          val angelRadi = angleBetweenOriented(accCoords(indexProblem - 1), problemCoord.get, currCoords.head)
          angelRadi
        } catch {
          case e: Exception =>
            println(e.getMessage)
            val kaka = angleBetweenOriented(accCoords(accCoords.length - 2), problemCoord.get, currCoords.head)

            kaka
        }
        val angle = toDegrees(kakiAngle)

        val newCoordsMaybe = createNewCoords(accCoords, coordinatesToAddPolygon, problemCoord.get, indexProblem, angle)

        try {
          val maybePolyFix = factory.createPolygon(newCoordsMaybe)
          maybePolyFix
        } catch {
          case e: Exception =>
            println(e.getMessage)
            polygon
        }
      }
    )
  }


  def createNewCoords(prevCoords: Array[Coordinate], currCoordsToAdd: Array[Coordinate],
                      problemCoord2: Coordinate, indexOfProblem: Int, angleOf: Double): Array[Coordinate] = {

    if (angleOf < 180) {
      val newCoords2 = if (indexOfProblem != 0) {
        (prevCoords.slice(0, indexOfProblem) :+
          new Coordinate(problemCoord2.x - 0.001, problemCoord2.y - 0.001)) ++
          (currCoordsToAdd :+ new Coordinate(problemCoord2.x + 0.001, problemCoord2.y + 0.001)) ++
          prevCoords.slice(indexOfProblem + 1, prevCoords.length)
      } else {
        (currCoordsToAdd :+  new Coordinate(problemCoord2.x + 0.001, problemCoord2.y + 0.001)) ++
          prevCoords.slice(1, prevCoords.length - 1) ++
          Array[Coordinate]( new Coordinate(problemCoord2.x - 0.001, problemCoord2.y - 0.001),
            currCoordsToAdd.head)
      }

      newCoords2
    } else {
      val newCoords2 = (prevCoords.slice(0, indexOfProblem) :+
        new Coordinate(problemCoord2.x + 0.001, problemCoord2.y + 0.001)) ++
        (currCoordsToAdd :+ new Coordinate(problemCoord2.x - 0.001, problemCoord2.y - 0.001)) ++
        prevCoords.slice(indexOfProblem + 1, prevCoords.length)

      newCoords2
    }
  }
}
