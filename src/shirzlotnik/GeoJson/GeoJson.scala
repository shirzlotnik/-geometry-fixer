package shirzlotnik.GeoJson

sealed trait GeoJson {
  def `type`: String
  def coordinates: Any
}

object GeoJson {
  object Types {
    val Point = "Point"
    val LineString = "LineString"
    val Polygon = "Polygon"
    val MultiPoint = "MultiPoint"
    val MultiLineString = "MultiLineString"
    val MultiPolygon = "MultiPolygon"
  }

  type PointCoordinates = Seq[Double]
  type LineStringCoordinates = Seq[PointCoordinates]
  type PolygonCoordinates = Seq[LineStringCoordinates]

  case class Point(coordinates: PointCoordinates) extends GeoJson {
    override val `type`: String = Types.Point
  }

  case class LineString(coordinates: LineStringCoordinates) extends GeoJson {
    override val `type`: String = Types.LineString
  }

  case class Polygon(coordinates: PolygonCoordinates) extends GeoJson {
    override val `type`: String = Types.Polygon
  }

  case class MultiPoint(coordinates: Seq[PointCoordinates]) extends GeoJson {
    override val `type`: String = Types.MultiPoint
  }

  case class MultiLineString(coordinates: Seq[LineStringCoordinates]) extends GeoJson {
    override val `type`: String = Types.MultiLineString
  }

  case class MultiPolygon(coordinates: Seq[PolygonCoordinates]) extends GeoJson {
    override val `type`: String = Types.MultiPolygon
  }
}