package reactive2

import akka.actor.{ActorSystem, Props}
import reactive2.FSM.ShopActor

object Shop extends App {

  val system = ActorSystem("Siop-Gymraeg")
  val shopActor = system.actorOf(Props[ShopActor])

  shopActor ! ShopActor.Init
  println("Croeso!")
}
