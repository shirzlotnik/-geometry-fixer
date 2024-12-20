import shirzlotnik.GeoJson.GeoJsonCodec.GeoJsonCodec
import shirzlotnik.GeoJson.GeoJson
import org.apache.sedona.spark.SedonaContext
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.sedona_sql.expressions.st_constructors.ST_GeomFromGeoJSON
import org.apache.spark.sql.sedona_sql.expressions.st_functions.{ST_Area, ST_Centroid, ST_DumpPoints}
import org.locationtech.jts.geom._
import org.wololo.jts2geojson.GeoJSONReader
import io.circe.parser.decode
import io.circe.syntax._
import FixLogic._
import org.geotools.geometry.jts.JTS._
import org.locationtech.jts.algorithm.Angle.{angleBetween, angleBetweenOriented, toDegrees}
import shirzlotnik.GeoJson.GeoJson.{LineStringCoordinates, PolygonCoordinates}


object Main {

  def fixLinearRingNotClosing(geoJsonStr: String): String = {
    try {
      val geoJson = decode[GeoJson](geoJsonStr)
      val newGeom: GeoJson = geoJson match {
        case Right(multiPolygon: GeoJson.MultiPolygon) => GeoJson.MultiPolygon(multiPolygon.coordinates.map((pc: PolygonCoordinates) => pc.map(fixLinearRingNotClosing)))
        case Right(polygon: GeoJson.Polygon) => GeoJson.Polygon(polygon.coordinates.map(fixLinearRingNotClosing))

        case Left(error) =>
          println(error.getMessage)
          throw error
      }

      newGeom.asJson.noSpaces
    } catch {
      case e: Exception =>
        println(e.getMessage)
        geoJsonStr
    }
  }

  def fixLinearRingNotClosing(coordinates: LineStringCoordinates): LineStringCoordinates = {
    if (coordinates.head != coordinates.last)
      coordinates :+ coordinates.head
    else coordinates
  }

  def parseGeoJsonToGeometry(geoJsonStr: String): Option[Geometry] = {
    try {
      val reader = new GeoJSONReader()
      val geom = reader.read(geoJsonStr)

      Some(geom)
    } catch {
      case e: IllegalArgumentException if e.getMessage == "Points of LinearRing do not form a closed linestring" =>
        val pip = parseGeoJsonToGeometry(fixLinearRingNotClosing(geoJsonStr))
        pip
      case e: Exception =>
        println(e.getMessage)
        throw e
    }
  }

  private def fixGeometry(geometry: Geometry): Option[Geometry] = {
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


  def GeometryCoordinatesLength(geometry: Geometry): Int = {
    geometry.getCoordinates.length
  }




  case class GeometryDF(geom: Geometry)
  case class GeoJsonDf(geoJson: String, id: String)

  def main(args: Array[String]): Unit = {
    val sedonaContex = SedonaContext.builder().master("local[*]").getOrCreate()
    val spark = SedonaContext.create(sedonaContex)

    import spark.implicits._

    val getFixGeometry = udf(fixGeometry _)
    val getParsedGeometry = udf(parseGeoJsonToGeometry _)
    val getGeometryCoordinatesLength = udf(GeometryCoordinatesLength _)
    val getFixSelfIntersectOnExistCoordinate = udf(fixSelfIntersectOnExistCoordinate _)

    val geoJSons = Seq(
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]]}", "polygon1"),
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]}", "polygon2"),
      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]]]}", "multipolygon1"),
      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]]}", "multipolygon2"),
      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]],[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]]}", "multipolygon_1_2"),

      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0]]]}", "polygon1_missing_closing_ring"), // work
      GeoJsonDf("{\"type\":\"Polygon\",\"coordinates\":[[[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]}", "polygon2_missing_closing_ring"), // no work
      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0]]]]}", "multipolygon1_missing_closing_ring"), // work
      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0],[0.0,-1.0]]]]}", "multipolygon2_missing_closing_ring"), // no work
      GeoJsonDf("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[1.0,-1.0],[0.0,-2.0],[-1.0,-1.0],[0.0,0.0],[1.0,0.0],[0.0,2.0],[-1.0,1.0],[0.0,0.0],[1.0,-1.0]]],[[[0.0,-1.0],[-1.0,-1.0],[-1.0,0.0],[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0]]]]}", "multipolygon_1_2_missing_closing_ring"), // no work
    ).toDF()

    val fixedPolygonsDF = geoJSons.withColumn("geometry", getParsedGeometry(col("geoJson")))
      .withColumn("fixSelfIntersects", getFixSelfIntersectOnExistCoordinate(col("geometry")))
      .withColumn("fixed", getFixGeometry(col("fixSelfIntersects")))
      .withColumn("area", ST_Area(col("fixed")))
      .withColumn("centroid", ST_Centroid(col("fixed")))
      .withColumn("original_coordinates", getGeometryCoordinatesLength(col("geometry")))
      .withColumn("fixed_coordinates", getGeometryCoordinatesLength(col("fixed")))


    fixedPolygonsDF.show(false)

    println("FINISH")

  }
}