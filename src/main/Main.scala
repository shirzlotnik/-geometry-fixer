import FixLogic.fixCoordinatesDuplicates
import org.apache.sedona.spark.SedonaContext
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.sedona_sql.expressions.st_constructors.ST_GeomFromGeoJSON
import org.apache.spark.sql.sedona_sql.expressions.st_functions.ST_AsGeoJSON
import org.locationtech.jts.geom._


object Main {
//  def fixDoubleCoords(geometry: Geometry): Geometry = {
//    val coordinates = geometry.getCoordinates
//    val geometryFactory = new GeometryFactory()
//
//    val newCoords = (0 until coordinates.length - 1)
//      .foldLeft[(Array[Coordinate], Double)](Array[Coordinate](), Double.NaN) {
//        case ((accCoords, lastM), i) => {
//          val currM = (coordinates(i+1).y - coordinates(i).y)/(coordinates(i+1).x-coordinates(i).x)
//          if(i == 0) (accCoords ++ Array(coordinates(i), coordinates(i+1)), currM)
//          else {
//
//            if(lastM == currM) {
//              val newFixCoords = (accCoords.slice(0, accCoords.length - 1) :+ coordinates(i+1))
//              (newFixCoords, currM)
//            } else (accCoords :+ coordinates(i+1), currM)
//          }
//        }
//      }._1
//
//    val newGeom = geometryFactory.createPolygon(newCoords)
//    newGeom
//  }


  def fixGeometry(geometry: Geometry): Option[Geometry] = {
    try {
      geometry match {
        case multiPolygon: MultiPolygon => Some(fixCoordinatesDuplicates(multiPolygon))
        case polygon: Polygon => Some(fixCoordinatesDuplicates(polygon))
        case _ => None
      }
    } catch {
      case e: Exception => println(e.getMessage)
        None
    }
  }

  case class GeometryDF(geom: Geometry)

  def main(args: Array[String]): Unit = {
    val sedonaContex = SedonaContext.builder().master("local[*]").getOrCreate()
    val spark = SedonaContext.create(sedonaContex)

    import spark.implicits._


    val getFixGeometry = udf(fixGeometry _)

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
      ),
      Array(
        new Coordinate(0,-1),
        new Coordinate(-1,-1),
        new Coordinate(-1,-0),
        new Coordinate(0,0),
        new Coordinate(1,0),
        new Coordinate(1,1),
        new Coordinate(0,1),
        new Coordinate(0,0),
        new Coordinate(0,-1)
      )
    )

    val geometryFactory = new GeometryFactory()

    val polygons = polyCoords.map(geometryFactory.createPolygon)
    val multiPolygons = polygons.map(p => geometryFactory.createMultiPolygon(Array[Polygon](p))) :+
      geometryFactory.createMultiPolygon(polygons.toArray)
    val geoms = polygons ++ multiPolygons
    val geomsDf = geoms.map(GeometryDF).toDF()
      .withColumn("geoJson", ST_AsGeoJSON(col("geom")))
      .withColumn("real_geom", ST_GeomFromGeoJSON(col("geoJson")))

    val fixedPolygonsDF = geomsDf.withColumn("fixed", getFixGeometry(col("real_geom")))

    fixedPolygonsDF.show(false)

    println("FINISH")

  }
}