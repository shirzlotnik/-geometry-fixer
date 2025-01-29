package shir

import org.locationtech.jts.geom.Coordinate

case class Line(id: String, head: Coordinate, tail: Coordinate, slope: Double)



object FixByLines {
  private def getSlope(head: Coordinate, tail: Coordinate): Double = {
    if (head.x == tail.x) {
      (head.y - tail.y)/(-1 * Double.MinPositiveValue)
    } else {
      (head.y - tail.y)/(head.x - tail.x)
    }
  }

  var cordinatesMap = Map[String, Coordinate]()

  def coordinatesToMap(coordinates: Array[Coordinate]): Map[String, Coordinate] = {
    val kaka: Map[String, Coordinate] = coordinates.indices.collect{
      case i => (s"x${i}", coordinates(i))
    }.toMap
    kaka
  }
  def calculateLines(coordinates: Array[Coordinate]): Array[Line] = {
    val lines = (0 until coordinates.length - 1).collect{
      case i =>
        val head = coordinates(i)
        val tail = coordinates(i + 1)
        val headId = cordinatesMap.find(cm => cm._2 == head).get._1
        val tailId = cordinatesMap.find(cm => cm._2 == tail).get._1
        Line(s"${headId}_${tailId}", head, tail, getSlope(head, tail))
    }.toArray

    lines
  }

  def findIntersection(line1: Line, line2: Line): Option[Coordinate] = None

  private def findFixedCoordinate(c1: Coordinate, c2: Coordinate): Coordinate = {
    val epsilon = 0.001
    val vector = new Coordinate(c1.x - c2.x, c1.y - c2.y)
    val magnitude = Math.sqrt(Math.pow(vector.x, 2) + Math.pow(vector.y, 2))
    val newCoordinate = if (magnitude == 0) c2
    else new Coordinate(c2. x +(epsilon * vector.x / magnitude),
      c2.y + (epsilon * vector.y / magnitude))
    newCoordinate
  }

  def fixIntersectionOnExistingVertex(lines: Array[Line], currentLine: Line): Array[Line] = {
    val otherLines = lines.filter(l => l.id != currentLine.id && l.id != currentLine.id.reverse)
    var okLines = Array[Line]()
    otherLines.foreach{
      case l =>
        val intersectionPoint = findIntersection(currentLine, l)
        if (intersectionPoint.isDefined) {
          val linesWithIntersection = otherLines.filter(l1 => l.tail == intersectionPoint.get ||
            l1.head == intersectionPoint.get)
          if (linesWithIntersection.length > 1) {
            linesWithIntersection.foreach{
              case l1 =>
                okLines = lines.filter(l2 => l2.id != l1.id && l2.id != l1.id.reverse)
                okLines = okLines :+ if (l1.head == intersectionPoint.get)
                   Line(s"${currentLine.id.split('_').head}_${l1.id.split('_').tail}",
                    currentLine.head, l1.tail, getSlope(currentLine.head, l1.tail))
                    else
                      Line(s"${currentLine.id.split('_').head}_${l1.id.split('_').head}",
                        currentLine.head, l1.head, getSlope(currentLine.head, l1.head))
               return fixIntersectionOnExistingVertex(okLines, currentLine)
            }
          } else {

          }
        }
    }
  }
}
