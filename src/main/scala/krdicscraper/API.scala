package krdicscraper

import java.io.File
import java.net.URL

import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._

import org.apache.commons.codec.net.URLCodec
import org.apache.commons.io.FileUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}


object API {
  type Error[A] = Either[String, A]

  implicit def tryToError[A](t: Try[A]): Error[A] = t match {
    case Success(res) => Right(res)
    case Failure(t  ) => Left(s"Exception happened: ${t.getMessage}. Stack trace: ${t.getStackTrace.mkString("\r\n")}")
  }

  def encode(str: String): Error[String] =
    Try { new URLCodec("utf8").encode(str) }

  def dicUrl(word: String): Error[String] =
    encode(word).map { w => s"http://m.krdic.naver.com/search/entry/1/$w/" }

  def dicUrlRaw(word: String): String =
    s"http://m.krdic.naver.com/search/entry/1/$word/"

  def connect(url: String, attempts: Int = 10): Error[Document] =
    try {
      Right(Jsoup.connect(url).get())
    } catch {
      case t: Throwable if attempts > 0 => connect(url, attempts - 1)
      case t: Throwable => Failure(t)
    }

  def maybeNull[T <: AnyRef](t: T): Option[T] =
    if (t eq null) None else Some(t)

  def wordElt(e: Element): Error[Element] =
    (for {
      lst <- maybeNull(e.getElementById("viewMoreList"))
      wds <- lst.getElementsByClass("lte").asScala.toList.headOption
    } yield wds) match {
      case None    => Left(s"Unable to process ${e}: most probably it has a non-standard structure.")
      case Some(x) => Right(x)
    }

  def grabWord(word: String): Error[Element] = for {
    doc  <- connect(dicUrlRaw(word))
    word <- wordElt(doc)
  } yield word

  def elementToModel(e: Element): Error[Entry] = {
    val posPat = """\[(.+)\]""".r

    val word = e.getElementsByClass("str2").asScala.toList.headOption.map(_.text)
    val mp3  = e.getElementsByAttributeValue("onclick", "nclk(this,'wrd.listen','','')").asScala.toList.headOption.map(_.attr("href"))
    val pn   = e.getElementsByClass("pn").asScala.toList.headOption.map(_.text.drop(1).dropRight(1))
    val exp  = e.getElementsByClass("ft").asScala.toList.headOption.map(_.ownText.drop(1).dropRight(1))
    val pos  = {
      val list = e.getElementsByClass("sy").asScala.toList
      if (list.size > 1) Some("More than one entry!")
      else list.headOption.map(_.ownText).flatMap { txt => posPat.findFirstMatchIn(txt) }.map { _.group(1) }
    }

    word match {
      case Some(w) => Right(Entry(w, mp3, pn, exp, pos))
      case None    => Left(s"Error while parsing element, probably it has a non-standard structure: $e")
    }
  }

  def grab(word: String): Error[Entry] = grabWord(word).flatMap(elementToModel)

  implicit class RangeInt(x: Int) { def rng(other: Int): Int => Boolean = y => y >= x && x <= other }
  implicit class UnionBoolFun[A](x: A => Boolean) { def or(other: A => Boolean): A => Boolean = a => x(a) || other(a) }

  def isKorean(c: Char): Boolean =
    (
      (0xAC00 rng 0xD7A3) or
      (0x1100 rng 0x11FF) or
      (0x3130 rng 0x318F) or
      (0xA960 rng 0xA97F) or
      (0xD7B0 rng 0xD7FF)
    )(c)


  def parseWordList(location: File): Error[List[String]] = Try {
    FileUtils.readLines(location, "utf8").asScala.toList
      .map { _.takeWhile(isKorean) }.filter(_.nonEmpty)
  }

  def downloadMp3(link: String, location: File): Unit = {
    FileUtils.copyURLToFile(new URL(link), location)
  }

  def downloadPronunciation(e: Entry, dir: File): Error[File] = {
    val lnk: Error[String] = e.pronunciation match {
      case Some(x) => Right(x)
      case None    => Left (s"Pronunciation is not available for ${e.word}")
    }

    for {
      l <- lnk
      f  = new File(dir, s"${e.word}.mp3")
      _ <- Try { downloadMp3(l, f) }
    } yield f
  }

  def appendToFile(f: File, str: String): Error[Unit] =
    Try { FileUtils.write(f, str + "\r\n", "utf8", true) }
}

case class Entry(
  word               : String
, pronunciation      : Option[String]
, actualPronunciation: Option[String]
, explanation        : Option[String]
, partOfSpeech       : Option[String]
) {
  def toCSV(d: String = ","): String =
    s"${word}${d}${d}${pronunciation.map(_ => s"[sound:${word}.mp3]").getOrElse("")}${d}${actualPronunciation.getOrElse("")}${d}${explanation.getOrElse("")}${d}${d}${partOfSpeech.getOrElse("")}${d}${d}"
}
