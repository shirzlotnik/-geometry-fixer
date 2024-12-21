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
      .map(p1 => fixSelfIntersectWithCoordinates(p1, id))
      .map(p2 => fixCoordinatesDuplicates(p2, id))
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

  def fixSelfIntersectOnExistCoordinate(linearRing: LinearRing, id: String): LinearRing = {
    val polygon = factory.createPolygon(linearRing)
    val fixedLinearRing = fixSelfIntersectOnExistCoordinate(polygon, id)
      .getBoundary.getGeometryN(0).asInstanceOf[LinearRing]
    fixedLinearRing
  }


  // better option
  def fixSelfIntersectWithCoordinates(linearRing: LinearRing, id: String): LinearRing = {
    val polygon = factory.createPolygon(linearRing)
    val fixedLinearRing = fixSelfIntersectWithCoordinates(polygon, id)
      .getBoundary.getGeometryN(0).asInstanceOf[LinearRing]
    fixedLinearRing
  }

  def fixSelfIntersectWithCoordinates(polygon: Polygon, id: String): Geometry = {
    val repaired = makeValid(polygon, false).toArray(Array[Polygon]()).toList

    val coordinatesArray = repaired.map(_.getCoordinates)
    val problemCoords = coordinatesArray.tail.foldLeft[Array[Coordinate]](coordinatesArray.head)(
      (accCoordsArray, currArray) => {
      accCoordsArray.intersect(currArray)
    }).head

    val polygonsSplitCoords = repaired.map(_.getCoordinates).map(cs1 => {
      val indexOfProblem = cs1.indexOf(problemCoords)
      (cs1.slice(indexOfProblem, cs1.length), cs1.slice(0, indexOfProblem))
    })

    val newCoordinates = polygonsSplitCoords.zipWithIndex.foldLeft[Array[Coordinate]](Array[Coordinate]())(
      (prevCoords, currCoordsTupleWithIndex) => {
        val index = currCoordsTupleWithIndex._2
        val (firstCoords, secondCoords) = currCoordsTupleWithIndex._1
        val problemCoords = firstCoords.head

        val angleFromX = if (prevCoords.isEmpty) {
          val (firstPoint, secondPoint) = getFirstAndSecondPoints(firstCoords, secondCoords, prevCoords, problemCoords)
          val p1 = angle(firstPoint, problemCoords)
          val p2 = angle(problemCoords, secondPoint)
          val p3 = p1 + p2
          p3
        } else {
          val (firstPoint, secondPoint) = getFirstAndSecondPoints(firstCoords, secondCoords, prevCoords, problemCoords)
          val p1 = angle(firstPoint, prevCoords.head)
          val p2 = angle(prevCoords.head, secondPoint)
          val p3 = p1 + p2
          p3
        }
        val (changedCoords, changedCoordsOps) = buildNewCoordinates(angleFromX,
          if (prevCoords.isEmpty) problemCoords else prevCoords(prevCoords.length - (index)))
        val firstArray = if (prevCoords.isEmpty) Array[Coordinate](changedCoords) else prevCoords.slice(0, prevCoords.length - 1) :+ changedCoords
        val lastArray = if (prevCoords.isEmpty) Array(changedCoords) else Array(changedCoordsOps, prevCoords.head)
        val secondArray =  if (secondCoords.isEmpty) firstCoords.tail else firstCoords.slice(1, firstCoords.length - 1) ++ secondCoords

         val pppp = firstArray ++ secondArray ++ lastArray

        pppp
      }
    )

    try {
      val poly2 = factory.createPolygon(newCoordinates)
      poly2
    } catch {
      case e: Exception => println(e.getMessage)
        println(polygon)
        println(id)
        polygon
    }
  }

  def getFirstAndSecondPoints(firstCoords: Array[Coordinate], secondCoords: Array[Coordinate],
               prevCoords: Array[Coordinate], problemCoords: Coordinate): (Coordinate, Coordinate) = {
    if (prevCoords.isEmpty) {
      val arraysContainsProblem = if (firstCoords.contains(problemCoords)) (firstCoords, secondCoords) else (secondCoords, firstCoords)
      val firstPoint = arraysContainsProblem._1(arraysContainsProblem._1.indexOf(problemCoords) + 1)
      val secondPoint = if (arraysContainsProblem._2.isEmpty) {
        arraysContainsProblem._1(arraysContainsProblem._1.length - 2)
      } else {
        arraysContainsProblem._2.last
      }
      (firstPoint, secondPoint)
    } else {
      val arraysContainsProblem = if (firstCoords.isEmpty) (prevCoords, secondCoords) else (prevCoords, firstCoords)
      val firstPoint = arraysContainsProblem._1.head
      val secondPoint = if (arraysContainsProblem._2.isEmpty) {
        arraysContainsProblem._1(arraysContainsProblem._1.indexOf(problemCoords) + 1)
      } else {
        arraysContainsProblem._2(arraysContainsProblem._2.indexOf(problemCoords) + 1)
      }
      (firstPoint, secondPoint)
    }
  }

  def buildNewCoordinates(angleFromX: Double, coordinate: Coordinate, opposite: Int = 1):
  (Coordinate, Coordinate) = {
    val sinAngle = Math.sin(angleFromX)
    val cosAngle = Math.cos(angleFromX)
    val epsilon = 0.001 * opposite


    try {
      if (sinAngle >= 0) { // I or II
        if (cosAngle >= 0) { // I
          (new Coordinate(coordinate.x + epsilon, coordinate.y + epsilon),
            new Coordinate(coordinate.x + epsilon, coordinate.y - epsilon))
        } else { // II
          (new Coordinate(coordinate.x - epsilon, coordinate.y - epsilon),
            new Coordinate(coordinate.x - epsilon, coordinate.y + epsilon))
        }
      } else { // III or IV
        if (cosAngle >= 0) { // IV
          (new Coordinate(coordinate.x + epsilon, coordinate.y - epsilon),
            new Coordinate(coordinate.x - epsilon, coordinate.y - epsilon))
        } else { // III
          (new Coordinate(coordinate.x + epsilon, coordinate.y - epsilon),
            new Coordinate(coordinate.x - epsilon, coordinate.y - epsilon))
        }
      }
    } catch {
      case e: Exception => println(e.getMessage)
        println(e.getClass)
        (coordinate, coordinate)
    }
  }


   // maybe better?
  def buildNewCoordinates2(angleFromX: Double, coordinate: Coordinate, opposite: Int = 1):
  (Coordinate, Coordinate) = {
    val sinAngle = Math.sin(angleFromX)
    val cosAngle = Math.cos(angleFromX)
    val epsilon = 0.001


    try {
      if (sinAngle >= 0) { // I or II
        if (cosAngle >= 0) { // I
          (new Coordinate(coordinate.x + epsilon, coordinate.y + epsilon),
            new Coordinate(coordinate.x - epsilon, coordinate.y - epsilon))
        } else { // II
          (new Coordinate(coordinate.x - epsilon, coordinate.y + epsilon),
            new Coordinate(coordinate.x + epsilon, coordinate.y - epsilon))
        }
      } else { // III or IV
        if (cosAngle >= 0) { // IV
//          (new Coordinate(coordinate.x + epsilon, coordinate.y - epsilon),
//            new Coordinate(coordinate.x - epsilon, coordinate.y + epsilon))
          (new Coordinate(coordinate.x + epsilon, coordinate.y + epsilon),
            new Coordinate(coordinate.x - epsilon, coordinate.y - epsilon))
        } else { // III
          (new Coordinate(coordinate.x - epsilon, coordinate.y - epsilon),
            new Coordinate(coordinate.x + epsilon, coordinate.y + epsilon))
        }
      }
    } catch {
      case e: Exception => println(e.getMessage)
        println(e.getClass)
        (coordinate, coordinate)
    }
  }


  def fixSelfIntersectOnExistCoordinate(polygon: Polygon, id: String): Polygon = {
    val repaired = makeValid(polygon, false).toArray(Array[Polygon]()).toList

    repaired match {
      case polygons: List[Polygon] if polygons.nonEmpty =>
        polygons.tail.foldLeft[Polygon](polygons.head)(
          (acc, curr) => {
            val accCoords = acc.getCoordinates
            val currCoords = curr.getCoordinates
            val currIndex = polygons.indexOf(curr)

            val problemCoord = accCoords.find(currCoords.contains)
            val indexProblem = accCoords.indexOf(problemCoord.get)
            val indexForCurrPolygonProblem = currCoords.indexOf(problemCoord.get)

            val currPolygonCoordinates = if (indexProblem == 0) {
              accCoords.slice(1, accCoords.length - 1)
            } else accCoords

            val coordinatesToAddPolygon = (if (currIndex % 2 != 0) {
              currCoords.slice(indexForCurrPolygonProblem, currCoords.length) ++
                currCoords.slice(0, indexForCurrPolygonProblem)
            } else {
              (currCoords.slice(0, indexForCurrPolygonProblem) ++
                currCoords.slice(indexForCurrPolygonProblem, currCoords.length))
            })
            val indexProblemCurr = coordinatesToAddPolygon.indexOf(problemCoord.get)

            val firstPoint = if (indexProblem != accCoords.length - 1) {
              accCoords(indexProblem + 1)
            } else {
              accCoords(accCoords.length - 2)
            }

            val secondPoint = if (indexProblemCurr == 0) {
              coordinatesToAddPolygon(coordinatesToAddPolygon.length - 1)
            } else {
              coordinatesToAddPolygon(indexProblemCurr - 1)
            }


            val kakiAngle = try {
              val angelRadi = angleBetween(firstPoint, problemCoord.get, secondPoint)
              angelRadi
            } catch {
              case e: ArrayIndexOutOfBoundsException =>
                println(e.getMessage + " line 116")
                println(id)
                val kaka = angleBetween(accCoords(accCoords.length - 2), problemCoord.get, coordinatesToAddPolygon(coordinatesToAddPolygon.length - 1))

                kaka
              case e: Exception =>
                println(e.getMessage + " line 116")
                println(id)
                val kaka = angleBetween(accCoords(accCoords.length - 2), problemCoord.get, coordinatesToAddPolygon(indexProblemCurr + 1))

                kaka
            }
            val angle = toDegrees(kakiAngle)

            println(s"angle: ${angle} angle_radian ${kakiAngle}")
            val newCoordsMaybe = createNewCoords(currPolygonCoordinates, coordinatesToAddPolygon,
              problemCoord.get, indexProblem, indexProblemCurr, angle)

            try {
              val maybePolyFix = factory.createPolygon(newCoordsMaybe)
              maybePolyFix
            } catch {
              case e: Exception =>
                println(e.getMessage+ " line 131")
                println(polygon)
                println(id)
                polygon
            }
          }
        )
      case _ => polygon
    }
  }


  def createNewCoords(prevCoords: Array[Coordinate], currCoordsToAdd: Array[Coordinate],
                      problemCoord2: Coordinate, indexOfProblem: Int, indexProblemCurr: Int, angleOf: Double): Array[Coordinate] = {

    val leftOrRight = if ((angleOf < 90 || angleOf == 180) && angleOf > 45) (1, -1) else (-1, 1)
    val coordsToFix: (Coordinate, Coordinate) =
      (new Coordinate(problemCoord2.x + 0.001 * leftOrRight._1, problemCoord2.y + 0.001 * leftOrRight._1),
      new Coordinate(problemCoord2.x + 0.001 * leftOrRight._2, problemCoord2.y + 0.001 * leftOrRight._2))

    val newCoords3 = if (indexOfProblem == 0) {
      (prevCoords :+ coordsToFix._1) ++
        currCoordsToAdd.slice(indexProblemCurr + 1,
          if (indexProblemCurr == 0) currCoordsToAdd.length -1 else currCoordsToAdd.length) ++
        currCoordsToAdd.slice(if (indexProblemCurr == 0) 0 else 1, indexProblemCurr) :+
        coordsToFix._2 :+ prevCoords.head
    } else {
      ((prevCoords.slice(0, indexOfProblem) :+ coordsToFix._1) ++
        currCoordsToAdd.slice(indexProblemCurr + 1, currCoordsToAdd.length).distinct ++
        currCoordsToAdd.slice(0, indexProblemCurr).distinct :+ coordsToFix._2) ++
        prevCoords.slice(indexOfProblem + 1, prevCoords.length)
    }

    newCoords3
  }
}
