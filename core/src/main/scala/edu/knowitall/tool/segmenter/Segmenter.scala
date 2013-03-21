package edu.knowitall
package tool
package segment

import _root_.edu.knowitall.collection.immutable.Interval

/** A sentencer breaks text into sentences.
  */
abstract class Segmenter {
  def apply(document: String) = segment(document)

  def segmentTexts(document: String) = {
    this.segment(document).map(_.text)
  }

  def segment(document: String): Iterable[Segment]
}

case class Segment(text: String, offset: Int) {
  override def toString = serialize

  def interval = Interval.open(offset, offset + text.length)
  def length = text.length

  def serialize = text + "@" + offset
}

object Segment {
  implicit def asString(segment: Segment): String = segment.text

  private[this] val segmentRegex = """(.+)@(\d+)""".r
  def deserialize(pickled: String): Segment = {
    pickled match {
      case segmentRegex(string, offset) => new Segment(string, offset.toInt)
      case s => throw new MatchError("Could not deserialize: " + s)
    }
  }
}

abstract class SegmenterMain
extends LineProcessor("segmenter") {
  def sentencer: Segmenter
  override def process(line: String) =
    sentencer(line).mkString("\n")
}

class RemoteSegmenter(urlString: String) extends Segmenter {
  import dispatch._
  val svc = url(urlString)

  def segment(sentence: String) = {
    val response = Http(svc << sentence OK as.String).apply()
    response.split("\\n").map(Segment.deserialize)(scala.collection.breakOut)
  }
}
