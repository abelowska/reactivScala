package reactive2.FSM

import java.net.URI

import scala.util.Random

object Utils {
  def generateURI: URI = {
    val x = Random.alphanumeric
    val uriString = "grabania" + x take 10 mkString

    new URI(uriString)
  }
}