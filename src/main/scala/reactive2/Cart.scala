package reactive2

import akka.actor.{Actor, ActorSystem, Props, Timers}
import akka.event.LoggingReceive

import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer


object CartActions {

  sealed trait Command
  case class AddItem(id: String) extends Command
  case class RemoveItem(id: String) extends Command
  case object StartCheckout
  case object CancelCheckout
  case object CloseCheckout

  sealed trait Event
  case class ItemAdded(id: String) extends Event
  case class ItemRemoved(id: String) extends Event
  case object CheckoutStarted
  case object CheckoutCancelled
  case object CheckoutClosed

  case object Init
  case object Failed
  case object TimerExpired

  case object CartTimer

}

class Cart(system: ActorSystem) extends Actor with Timers {
  private val items: ListBuffer[String] = ListBuffer()

  override def receive: Receive = LoggingReceive {
    case CartActions.Init =>
      context become empty
    case _ =>
  }

  def empty: Receive = LoggingReceive{
    case CartActions.AddItem(id) =>
      items += id
      sender ! CartActions.ItemAdded(id)
      timers.startSingleTimer(CartActions.CartTimer, CartActions.TimerExpired , 15.second)
      context become nonEmpty
    case _ => sender ! CartActions.Failed
  }

  def nonEmpty: Receive = LoggingReceive {
    case CartActions.AddItem(id) =>
      items += id
      sender ! CartActions.ItemAdded(id)
    case CartActions.RemoveItem(id) =>
      if(!items.contains(id)) sender ! CartActions.Failed
      else {
        items -= id
        sender ! CartActions.ItemRemoved(id)
        if (items.isEmpty)  {
          timers.cancel(CartActions.CartTimer)
          context become empty
        }
      }
    case CartActions.TimerExpired =>
      items.clear()
      context become empty
    case CartActions.StartCheckout =>
      val checkoutActor = system.actorOf(Props(new Checkout(context.self, items)))
      checkoutActor ! CartActions.CheckoutStarted

      //wyslanie aktora do siop aby robic operacje chekoutowe potem
      sender ! checkoutActor

      timers.cancel(CartActions.CartTimer)
      context become inCheckout
    case _ => CartActions.Failed
  }

  def inCheckout: Receive = LoggingReceive {
    case CartActions.CancelCheckout =>
//      sender ! CartActions.CheckoutCancelled
      timers.startSingleTimer(CartActions.CartTimer, CartActions.TimerExpired , 1.minute)
      context become nonEmpty
    case CartActions.CloseCheckout =>
      items.clear()
//      sender ! CartActions.CheckoutClosed
      context become empty
    case _ =>
  }
}
