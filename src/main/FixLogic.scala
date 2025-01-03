import org.geotools.geometry.jts.JTS.makeValid
import org.locationtech.jts.geom._

object FixLogic {
  implicit val factory: GeometryFactory = new GeometryFactory()

  private def getSlope(head: Coordinate, tail: Coordinate): Double = {
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
      .map(lr => fixIntersectionLogic(lr, id))
//      .map(fixGeometry)
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

    val cleanCoordinates = (0 until coordinates.length - 1)
      .foldLeft[(Array[Coordinate], Double)](Array[Coordinate](), Double.NaN) {
        case ((prev, lastSlope), i) =>
          val pureSlope = getSlope(coordinates(i), coordinates(i+1))
          val currSlope = if (pureSlope == Double.PositiveInfinity || pureSlope == Double.NegativeInfinity)
            pureSlope
          else
            BigDecimal(pureSlope).setScale(5, BigDecimal.RoundingMode.HALF_UP).toDouble
          if (i == 0) (prev ++ Array(coordinates(i), coordinates(i+1)), currSlope)
          else {
            val cleanedCoordinates = if (lastSlope == currSlope) prev.slice(0, prev.length - 1)
            else prev
            (cleanedCoordinates :+ coordinates(i+1), currSlope)
          }
      }._1

    val fixedCoordinates = if (cleanCoordinates(cleanCoordinates.length - 2) == cleanCoordinates.last)
      cleanCoordinates.reverse.tail.reverse
    else cleanCoordinates

    try {
      val fixedLinearRing = geometryFactory.createLinearRing(fixedCoordinates)

      fixedLinearRing
    } catch {
      case e: Exception => println(e.getMessage + " line 72")
        println(linearRing)
        println(linearRing.toString)
        println(id)
        linearRing
    }
  }



  private def findFixedCoordinate(c1: Coordinate, c2: Coordinate): Coordinate = {
    val epsilon = 0.001
    val vector = new Coordinate(c1.x - c2.x, c1.y - c2.y)
    val magnitude = Math.sqrt(Math.pow(vector.x, 2) + Math.pow(vector.y, 2))
    val newCoordinate = if (magnitude == 0) c2
    else new Coordinate(c2. x +(epsilon * vector.x / magnitude),
      c2.y + (epsilon * vector.y / magnitude))
    newCoordinate
  }


  private def fixIntersectionLogic(linearRing: LinearRing, id: String): LinearRing = {
    val polygon = factory.createPolygon(linearRing)
    val fixedLinearRing = fixIntersectionOnExistingCoordinate(polygon, id)
      .getBoundary.getGeometryN(0).asInstanceOf[LinearRing]
    fixedLinearRing
  }

  private def fixIntersectionOnExistingCoordinate(polygon: Polygon, id: String): Polygon = {
    try {
      val repaired = makeValid(polygon, false).toArray(Array[Polygon]()).toList
      val coordinatesArray = repaired.map(_.getCoordinates)

      val polygonCoordinates = polygon.getCoordinates
      val innerCoordinates = polygonCoordinates.slice(1, polygonCoordinates.length - 1)
      val problemCoordinates = innerCoordinates
        .groupBy(c => polygonCoordinates.count(c1 => c1 == c))
        .filter(c => c._1 > 1).values
        .reduce((p, c) => p ++ c).distinct

      val (start, end, _) = coordinatesArray.foldLeft[(Array[Coordinate], Array[Coordinate], Seq[Coordinate])]((
        Array[Coordinate](), Array[Coordinate](), Seq[Coordinate]()))({
        case ((q1, q2, usedIntersections), curr) =>
          val currLength = curr.length
          val startPoint = if (q1.isEmpty) curr.head else q1.last
          val startIndex = curr.indexOf(startPoint) + 1
          val intersectionPoint = curr.find(c1 => problemCoordinates.contains(c1) &&
            !usedIntersections.contains(c1))
          val intersectionPointIndex = if (intersectionPoint.isDefined)
            curr.indexOf(intersectionPoint.get) + 1
          else startIndex

          val usedIntersectionPoints = usedIntersections :+ curr(intersectionPointIndex - 1)

          val (cw, ccw) = if (q1.isEmpty) {
            val (ltr, rtl) = if (startIndex == intersectionPointIndex)
              (curr.slice(startIndex - 1, currLength), curr.slice(0, intersectionPointIndex))
            else (curr.slice(startIndex - 1, intersectionPointIndex), curr.slice(intersectionPointIndex, currLength))
            (ltr, rtl.reverse)
          } else {
            val (ltr,rtl) = if (intersectionPointIndex < startIndex)
              (curr.slice(startIndex, currLength) ++ curr.slice(1, intersectionPointIndex),
                curr.slice(intersectionPointIndex, startIndex))
            else if (startIndex == intersectionPointIndex)
              (curr.slice(startIndex, currLength) ++ curr.slice(1, startIndex),
                Array[Coordinate]())
            else (curr.slice(startIndex, intersectionPointIndex),
              curr.slice(intersectionPointIndex, currLength) ++ curr.slice(1, startIndex))
            (ltr, rtl.reverse)
          }

          (q1 ++ cw, q2 ++ ccw, usedIntersectionPoints)
      })

      val reconstructArray = start ++ end.reverse
      val indexPolygonHead = reconstructArray.indexOf(polygonCoordinates.head)
      val reArranged = if (indexPolygonHead == 0) reconstructArray
      else reconstructArray.slice(indexPolygonHead, reconstructArray.length) ++
        reconstructArray.slice(1, indexPolygonHead + 1)

      val (fixUntil, suffix) = if (problemCoordinates.contains(reArranged.last))
        (reArranged.length - 1, Array(reArranged.last))
      else (reArranged.length, Array[Coordinate]())

      val fixedInnerArray = (1 until fixUntil)
        .foldLeft(Array[Coordinate]())({
          case (prev, index) =>
            val curr = reArranged(index)
            prev :+ (if (problemCoordinates.contains(curr))
            findFixedCoordinate(reArranged(index - 1), curr)
          else curr)
        })


      val fixedCoordinates = (reArranged.head +: fixedInnerArray) ++ suffix
      val fixedPolygon = factory.createPolygon(fixedCoordinates)
      fixedPolygon
    } catch {
      case e: Exception => println(e.getMessage)
        println(e.getCause)
        println(id)
        polygon
    }
  }
}
