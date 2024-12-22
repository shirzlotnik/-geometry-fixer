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
      .map(p1 => fixSelfIntersectOnExistCoordinate(p1, id))
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
  def fixSelfIntersectWithCoordinatesNo(linearRing: LinearRing, id: String): LinearRing = {
    val polygon = factory.createPolygon(linearRing)
    val fixedLinearRing = fixSelfIntersectWithCoordinates(polygon, id)
      .getBoundary.getGeometryN(0).asInstanceOf[LinearRing]
    fixedLinearRing
  }

  def fixSuperIntersect(polygon: Polygon, id: String): Geometry = {
    val repaired = makeValid(polygon, false).toArray(Array[Polygon]()).toList
    val coordinatesArray = repaired.map(_.getCoordinates)

    val fixedPolygon = (1 until repaired.length)
      .foldLeft[(Polygon, Option[Coordinate], Option[Coordinate])](repaired.head, None, None)({
      case ((prevPolygon, refPoint, lastIntersect), i) => {
        val currPolygon = repaired(i)
        val (coordinates1, coordinates2) = (prevPolygon.getCoordinates, currPolygon.getCoordinates)
        val intersectionPoint = lastIntersect.getOrElse(coordinates1.find(coordinates2.contains).get)
        val (newPolygon, newRef) = merge2Polygons(coordinates1, coordinates2, refPoint, intersectionPoint)
        (newPolygon, newRef, Some(intersectionPoint))
      }
      case ((a, b, c), _) => (a, b, c)
    })._1


    fixedPolygon
  }

  def merge2Polygons(coordinates1: Array[Coordinate], coordinates2: Array[Coordinate],
                     refPoint: Option[Coordinate], intersectionPoint: Coordinate):
  (Polygon, Option[Coordinate]) = {

    val currRefPoint = refPoint.getOrElse(intersectionPoint)
    val index1 = coordinates1.indexOf(coordinates1.find(c => c == intersectionPoint).get)
    val index2 = coordinates2.indexOf(coordinates2.find(c => c == intersectionPoint).get)

    val firstIndex = if (index1 > -1) index1
    else coordinates1.indexOf(coordinates1.find(c => c == refPoint.get).get)

    val (coordinates1P1, coordinates2P1, firstCheckP1, secondCheckP1) =
      getSplitCoordinatesAndPointToCheckIntersection(coordinates1, firstIndex, true)
    val (coordinates1P2, coordinates2P2, firstCheckP2, secondCheckP2) =
      getSplitCoordinatesAndPointToCheckIntersection(coordinates2, index2, false)

    val addedCoordinate1 = findNewIntersection(firstCheckP1, firstCheckP2, currRefPoint)
    val addedCoordinate2 = findNewIntersection(secondCheckP2, secondCheckP1, currRefPoint)

    val mergedCoordinates = (coordinates1P1 :+ addedCoordinate1) ++ coordinates2P2 ++
      (coordinates1P2 :+ new Coordinate(addedCoordinate1.x * -1, addedCoordinate1.y * -1)) ++
      coordinates2P1

//    val mergedCoordinates = (pl1TillIntersect :+ ap1) ++ pl2FromIntersect ++
//      (pl2TillIntersect :+ ap2) ++ pl1FromIntersect

    try {
      val newPoly = factory.createPolygon(mergedCoordinates)
      (newPoly, Some(addedCoordinate2))
    } catch {
      case e: Exception => println(e.getMessage)
        (factory.createPolygon(coordinates1), refPoint)
    }
  }

  def getSplitCoordinatesAndPointToCheckIntersection(coordinates: Array[Coordinate],
                                                     index: Int, first: Boolean):
  (Array[Coordinate], Array[Coordinate], Coordinate, Coordinate) = {
    if (first) {
      if (index == 0) {
        val coordinates1 = coordinates.slice(0, coordinates.length - 1)
        val coordinates2 = Array(coordinates.head)
        val firstCheck = coordinates1.last
        val secondCheck = coordinates1(1)
        (coordinates1, coordinates2, firstCheck, secondCheck)
      } else {
        val coordinates1 = coordinates.slice(0, index)
        val coordinates2 = coordinates.slice(index + 1, coordinates.length)
        val firstCheck = coordinates1.last
        val secondCheck = coordinates2.head
        (coordinates1, coordinates2, firstCheck, secondCheck)
      }
    } else {
      if (index == 0) {
        val coordinates1 = coordinates.slice(1, coordinates.length - 1)
        val coordinates2 = Array[Coordinate]()
        val firstCheck = coordinates1.head
        val secondCheck = coordinates1.last
        (coordinates1, coordinates2, firstCheck, secondCheck)
      } else {
        val coordinates1 = coordinates.slice(0, index)
        val coordinates2 = coordinates.slice(index + 1, coordinates.length - 1)
        val firstCheck = (if (coordinates2.isEmpty) coordinates1 else coordinates2).head
        val secondCheck = coordinates1.last
        (coordinates1, coordinates2, firstCheck, secondCheck)
      }
    }
  }

  def findNewIntersection(p1: Coordinate, p2: Coordinate, refCoords: Coordinate): Coordinate = {
    val epsilon = 0.001

    val addedX = if (p2.x > p1.x) (if (p1.x > 0 && p2.x > 0) epsilon
    else if (p1.x < 0 && p2.x < 0) -epsilon
    else -epsilon)
    else if (p2.x < p1.x) (if (p1.x > 0 && p2.x > 0) epsilon
    else if (p1.x < 0 && p2.x < 0) -epsilon
    else if(p1.x > 0 || p2.x > 0) epsilon else -epsilon)
    else (if (p2.x > 0 || p1.x > 0) epsilon else -epsilon)
//    else epsilon


    val addedY = if (p2.y > p1.y) (if (p1.y > 0 && p2.y > 0) epsilon
    else if (p1.y < 0 && p2.y < 0) -epsilon
    else epsilon)
    else if (p2.y < p1.y) (if (p1.y > 0 && p2.y > 0) epsilon
    else if (p1.y < 0 && p2.y < 0) -epsilon
    else (if (p2.y > 0 || p1.y > 0) epsilon else -epsilon))
    else (if (p2.y > 0) epsilon else  -epsilon)
//    else -epsilon

    val x1 = refCoords.x + addedX
    val y1 = refCoords.y + addedY

    val ap1 = new Coordinate(x1, y1)

    ap1
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

    repaired.foldLeft[(Polygon, Coordinate)](repaired.head, repaired.head.getCoordinates.head)({
      case ((p, c), c2) => (p,c)
    })

    polygonsSplitCoords.foldLeft[(Array[Coordinate], Option[Coordinate])]((Array[Coordinate](), None))({
      case ((prev, last), (first, second)) => (prev, last)

    })

    val newCoordinates = polygonsSplitCoords.zipWithIndex.foldLeft[Array[Coordinate]](Array[Coordinate]())(
      (prevCoords, currCoordsTupleWithIndex) => {
        val index = currCoordsTupleWithIndex._2
        val (firstCoords, secondCoords) = currCoordsTupleWithIndex._1
        val problemCoords = firstCoords.head

//        val angleFromX = if (prevCoords.isEmpty) {
//          val (firstPoint, secondPoint) = getFirstAndSecondPoints(firstCoords, secondCoords, prevCoords, problemCoords)
//          val p1 = angle(firstPoint, problemCoords)
//          val p2 = angle(problemCoords, secondPoint)
//          val p3 = p1 + p2
//          p3
//        } else {
//          val (firstPoint, secondPoint) = getFirstAndSecondPoints(firstCoords, secondCoords, prevCoords, problemCoords)
//          val p1 = angle(firstPoint, prevCoords.head)
//          val p2 = angle(prevCoords.head, secondPoint)
//          val p3 = p1 + p2
//          p3
//        }

        val (firstPoint, secondPoint) = if (prevCoords.isEmpty) {
          getFirstAndSecondPoints(firstCoords, secondCoords, prevCoords, problemCoords)
        } else {
          getFirstAndSecondPoints(firstCoords, secondCoords, prevCoords, problemCoords)
        }

        val refCoords = if (prevCoords.isEmpty) problemCoords else prevCoords.last
        val (delta1, delta2) = buildNewCoordinates3(firstPoint, secondPoint, refCoords)
//        val coordChangeSecond = secondCoordsChanges(coordChangeFirst, secondPoint)

//        val (changedCoords, changedCoordsOps) = buildNewCoordinates(angleFromX,
//          if (prevCoords.isEmpty) problemCoords else prevCoords(prevCoords.length - (index)))
        val firstArray = if (prevCoords.isEmpty) Array[Coordinate](delta1) else prevCoords.slice(0, prevCoords.length - 1) :+ delta1
        val lastCoordinates = if (prevCoords.isEmpty) delta1 else prevCoords.head
        val part1Array =  if (secondCoords.isEmpty &&
          firstCoords.last != problemCoords) firstCoords.tail
        else firstCoords.slice(1, firstCoords.length - 1)
        val part2Array = (if (index > 1) Array(delta2, lastCoordinates) else Array(lastCoordinates))

         val pppp = firstArray ++
//           (if (index > 1) secondArray :+ coordChangeSecond else secondArray) ++
           (part1Array ++ secondCoords) ++
           part2Array

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


  def secondCoordsChanges(point1: Coordinate, point2: Coordinate): Coordinate = {
    val epsilon = 0.001

    val newX = point1.x + (if (point2.x > point1.x) epsilon else -epsilon)
    val newY = point1.y + (if (point2.y > point1.y) epsilon else -epsilon)

    new Coordinate(newX, newY)
  }

  def buildNewCoordinates3(point1: Coordinate, point2: Coordinate,
                           coordinate: Coordinate): (Coordinate, Coordinate) = {
    val epsilon = 0.001

    val x1 = coordinate.x + (if (point2.x > point1.x) epsilon else -epsilon)
    val y1 = coordinate.y + (if (point2.y > point1.y) epsilon else -epsilon)
    val delta1 = new Coordinate(x1, y1)

    val x2 = delta1.x + (if (point2.x > delta1.x) epsilon else -epsilon)
    val y2 = delta1.y + (if (point2.y > delta1.y) epsilon else -epsilon)
    val delta2 = new Coordinate(x2, y2)

    (delta1, delta2)
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
