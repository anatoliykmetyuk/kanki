package krdicscraper

import org.scalatest._

import API._

class APISuite extends FlatSpec with Matchers {
  "Words with full info" should "be parsed" in {
    val res = grab("군함")
    res shouldBe a [Right[_, _]]

    res.right.get.word shouldBe "군함"
    res.right.get.pronunciation.get   should startWith ("http://dic.dn.naver.com/v?_lsu_sa_=")
    res.right.get.actualPronunciation shouldBe Some("군함")
    res.right.get.explanation         shouldBe Some("軍艦")
    res.right.get.partOfSpeech        shouldBe Some("명사")
  }

  "Words without pronunciation" should "represent it as None" in {
    val word = "미사일"
    val res = grab(word)
    res shouldBe a [Right[_, _]]

    res.right.get.word shouldBe word
    res.right.get.pronunciation.get   should startWith ("http://dic.dn.naver.com/v?_lsu_sa_=")
    res.right.get.actualPronunciation shouldBe None
    res.right.get.explanation         shouldBe Some("missile")
    res.right.get.partOfSpeech        shouldBe Some("명사")
  }

  "Words withotu POS" should "represent it as None" in {
    val word = "요원"
    val res = grab(word)
    res shouldBe a [Right[_, _]]

    res.right.get.word shouldBe word
    res.right.get.pronunciation.get   should startWith ("http://dic.dn.naver.com/v?_lsu_sa_=")
    res.right.get.actualPronunciation shouldBe Some("요원")
    res.right.get.explanation         shouldBe Some("遙遠/遼遠")
    res.right.get.partOfSpeech        shouldBe None
  }

  "Words with two or more POS" should "represent it as an informative String" in {
    val word = "제일"
    val res = grab(word)
    res shouldBe a [Right[_, _]]

    res.right.get.word shouldBe word
    res.right.get.pronunciation.get   should startWith ("http://dic.dn.naver.com/v?_lsu_sa_=")
    res.right.get.actualPronunciation shouldBe Some("제ː일")
    res.right.get.explanation         shouldBe Some("第一")
    res.right.get.partOfSpeech        shouldBe Some("More than one entry!")
  }
}
