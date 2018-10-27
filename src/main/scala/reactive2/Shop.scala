package reactive2

import akka.actor.{ActorSystem, Props}

object Shop extends App {

  val system = ActorSystem("Siop-Gymraeg")
  val shopActor = system.actorOf(Props[ShopActor])

  shopActor ! ShopActor.Init
  println("Croeso!")
}
