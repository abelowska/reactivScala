package reactive2.FSM

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import reactive2.Shop.system
import reactive2.{Cart, CartActions, CheckoutActions}


object ShopActor {

  case object Init

}

class ShopActor extends Actor {
  override def receive: Receive = LoggingReceive {
    case ShopActor.Init =>

      val cartActor = system.actorOf(Props(new Cart(system)))
//      val cartActor = system.actorOf(Props(new CartFSM))
      cartActor ! CartActions.Init


      cartActor ! CartActions.AddItem("oak-derw")
      cartActor ! CartActions.RemoveItem("oak-derw")

      cartActor ! CartActions.AddItem("oak-derw")
      cartActor ! CartActions.AddItem("birch-bedw")

      Thread.sleep(30000)

      cartActor ! CartActions.AddItem("oak-derw")
      cartActor ! CartActions.AddItem("birch-bedw")

      cartActor ! CartActions.StartCheckout

    case checkoutActor:ActorRef =>
      checkoutActor ! CheckoutActions.SelectDeliveryMethod("letter-llythyr")
      checkoutActor ! CheckoutActions.SelectPaymentMethod("gold-aur")
      checkoutActor ! CheckoutActions.PaymentReceive
  }

}
