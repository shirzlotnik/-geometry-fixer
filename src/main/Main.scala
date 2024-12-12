import org.apache.sedona.spark.SedonaContext
import org.apache.spark.sql.functions.udf
import org.locationtech.jts.geom._


object Main {
  def fixDoubleCoords(geometry: Geometry): Geometry = {
    val coordinates = geometry.getCoordinates
    val geometryFactory = new GeometryFactory()

    val newCoords = (0 until coordinates.length - 1)
      .foldLeft[(Array[Coordinate], Double)](Array[Coordinate](), Double.NaN) {
        case ((accCoords, lastM), i) => {
          val currM = (coordinates(i+1).y - coordinates(i).y)/(coordinates(i+1).x-coordinates(i).x)
          if(i == 0) (accCoords ++ Array(coordinates(i), coordinates(i+1)), currM)
          else {

            if(lastM == currM) {
              val newFixCoords = (accCoords.slice(0, accCoords.length - 1) :+ coordinates(i+1))
              (newFixCoords, currM)
            } else (accCoords :+ coordinates(i+1), currM)
          }
        }
      }._1

    val newGeom = geometryFactory.createPolygon(newCoords)
    newGeom
  }

  def main(args: Array[String]) = {
    val sedonaContex = SedonaContext.builder().master("local[*]").getOrCreate()
    val spark = SedonaContext.create(sedonaContex)

    import spark.implicits._


//    val fixiGeom = udf(fixDoubleCoords _)

    val polyCoords = Seq(
      Array(
        new Coordinate(1,-1),
        new Coordinate(0,-2),
        new Coordinate(-1,-1),
        new Coordinate(0,0),
        new Coordinate(1,0),
        new Coordinate(0,2),
        new Coordinate(-1,1),
        new Coordinate(0,0),
        new Coordinate(1,-1)
      )
    )
    val geometryFactory = new GeometryFactory()

    val polygons = polyCoords.map(geometryFactory.createPolygon)

    val fixedPolygons = polygons.map(fixDoubleCoords)

    println("FINISH")

  }
}