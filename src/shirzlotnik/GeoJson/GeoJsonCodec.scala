package shirzlotnik.GeoJson

import GeoJson._
import io.circe.Decoder.Result
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Codec, HCursor, Json}

object GeoJsonCodec {
  final implicit object GeoJsonCodec extends Codec[GeoJson] {
    override def apply(c: HCursor): Result[GeoJson] = {
      c.downField("type").as[String].flatMap({
        case Types.Point => c.as[Point]
        case Types.LineString => c.as[LineString]
        case Types.Polygon => c.as[Polygon]
        case Types.MultiPoint => c.as[MultiPoint]
        case Types.MultiLineString => c.as[MultiLineString]
        case Types.MultiPolygon => c.as[MultiPolygon]
      })
    }

    override def apply(a: GeoJson): Json = {
      (("type", Json.fromString(a.`type`)) +: (a match {
        case b: Point => b.asJsonObject
        case b: LineString => b.asJsonObject
        case b: Polygon => b.asJsonObject
        case b: MultiPoint => b.asJsonObject
        case b: MultiLineString => b.asJsonObject
        case b: MultiPolygon => b.asJsonObject
      })).asJson
    }
  }
}