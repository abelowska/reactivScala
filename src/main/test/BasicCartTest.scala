import akka.actor.{ActorSystem, Props}
import reactive2.FSM.{CartFSM, Messages}

object BasicCartTest {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("Siop-Gymraeg")
    val cartActor = system.actorOf(Props(new CartFSM))

    cartActor ! Messages.AddItem("ania", 4)
//    cartActor ! Messages.RemoveItem("ania1", 8)
    cartActor ! Messages.RemoveItem("ania", 2)
//    cartActor ! Messages.RemoveItem("ania", 2)
  }

}
