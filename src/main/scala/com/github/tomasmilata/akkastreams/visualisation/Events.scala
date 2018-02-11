package com.github.tomasmilata.akkastreams.visualisation

object Events {
  case class CharGenerated(c: Char)
  case class WordGenerated(word: String)
}
