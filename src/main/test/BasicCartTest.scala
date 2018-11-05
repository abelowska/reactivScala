import akka.actor.{ActorRef, ActorSystem, Props}
import reactive2.FSM._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import reactive2.FSM.Messages.Buy

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object BasicCartTest extends TestKit(ActorSystem("BasicCartTest")) with ScalaFutures {
  implicit val timeout: Timeout = 1.second
  implicit val ec = ExecutionContext.global

  //pokazuje timery


//  def main(args: Array[String]): Unit = {
//
//    val system = ActorSystem("Siop-Gymraeg")
//    val cartActor = system.actorOf(Props(new CartFSM))
//
////    cartActor ! Messages.AddItem("ania", 4)
//    //    cartActor ! Messages.RemoveItem("ania1", 8)
////    cartActor ! Messages.RemoveItem("ania", 2)
//    //    cartActor ! Messages.RemoveItem("ania", 2)
//
//
//    (cartActor ? StartCheckout).futureValue match {
//      case CheckoutStarted(checkoutRef: ActorRef) =>
//        val checkoutActor = checkoutRef
//        checkoutActor ! SelectDeliveryMethod("letter")
//        Thread.sleep(30000)
//        checkoutActor ! SelectPaymentMethod("cart")
//    }
//  }



  def main(args: Array[String]): Unit = {

    val system = ActorSystem("Siop-Gymraeg")
//    val cartActor = system.actorOf(Props(new CartFSM))

//        cartActor ! Messages.AddItem("ania", 4)
    //    cartActor ! Messages.RemoveItem("ania1", 8)
    //    cartActor ! Messages.RemoveItem("ania", 2)
    //    cartActor ! Messages.RemoveItem("ania", 2)

//    (cartActor ? StartCheckout).onSuccess {
//      case CheckoutStarted(checkoutRef: ActorRef) =>
//        val checkoutActor = checkoutRef
//        println(checkoutActor)
//        checkoutActor ! SelectDeliveryMethod("letter")
////        checkoutActor ! SelectPaymentMethod("cart")
//    }

    val orderManager = system.actorOf(Props[OrderManager])
    orderManager ! Persist

//    orderManager ! Messages.AddItem("ania", 4)
//    Thread.sleep(2000)
//    orderManager ! Buy
//    Thread.sleep(2000)
//    orderManager ! OrderManagerEvent.SelectDeliveryMethod("lalla")

    Thread.sleep(4000)
    orderManager ! OrderManagerEvent.SelectPaymentMethod("bubuub")
    Thread.sleep(4000)
    orderManager ! Messages.Pay





  }
}
