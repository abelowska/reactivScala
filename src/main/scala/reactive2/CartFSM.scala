package reactive2

import akka.actor.{FSM, Props}

import scala.concurrent.duration._


// states
sealed trait CartState
case object Empty extends CartState
case object NonEmpty extends CartState
case object InCheckout extends CartState

sealed trait CartData
case class ItemsList(items: List[String]) extends CartData

class CartFSM extends FSM[CartState, CartData] {

  val initialData = ItemsList(List())
  startWith(Empty, initialData)

  when(Empty) {
    case Event(CartActions.AddItem(id), _) =>
      println("adding item")
      sender ! CartActions.ItemAdded(id)
      goto(NonEmpty) using ItemsList(List(id))
  }

  when(NonEmpty) {
    case Event(CartActions.AddItem(id), ItemsList(receivedData)) =>
      println("adding item")
      sender ! CartActions.ItemAdded(id)
      stay using ItemsList(receivedData ++ List(id))
    case Event(CartActions.RemoveItem(id), ItemsList(receivedData)) =>
      if (!receivedData.contains(id)) {
        sender ! CartActions.Failed
        stay using ItemsList(receivedData)
      }
      else {
        println("removing item")
        sender ! CartActions.ItemRemoved(id)
        receivedData.filter(item => item != id) match {
          case list if list.isEmpty => goto(Empty) using ItemsList(list)
          case list => stay using ItemsList(list)
        }

      }
    case Event(CartActions.TimerExpired, _) =>
      println("chart time expired")
      goto(Empty) using ItemsList(List())
    case Event(CartActions.StartCheckout, ItemsList(items)) =>
      val checkoutActorFSM = context.system.actorOf(Props(new CheckoutFSM(context.self, items)))
      checkoutActorFSM ! CartActions.CheckoutStarted
      goto(InCheckout) using ItemsList(items)
  }

  when(InCheckout) {
    case Event(CartActions.CancelCheckout, cartData) =>
      goto(NonEmpty) using cartData
    case Event(CartActions.CloseCheckout, _) =>
      goto(Empty) using ItemsList(List())
  }

  onTransition {
    case Empty -> NonEmpty =>
      setTimer(CartActions.CartTimer.toString, CartActions.TimerExpired, 15.second)
    case NonEmpty -> _ =>
      cancelTimer(CartActions.CartTimer.toString)
    case InCheckout -> NonEmpty =>
      setTimer(CartActions.CartTimer.toString, CartActions.TimerExpired, 15.second)
  }

  whenUnhandled {
    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      context stop self
      stay
  }
  initialize()
}
