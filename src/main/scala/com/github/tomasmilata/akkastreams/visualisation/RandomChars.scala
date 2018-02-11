package com.github.tomasmilata.akkastreams.visualisation

import scala.util.Random

trait RandomChars {

  def randomChar: Char = {
    val chars = "abcdefghijklmnopqrstuvwxyz"
    chars.charAt(Random.nextInt(chars.length))
  }
}