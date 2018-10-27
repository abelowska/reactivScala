package reactive2.FSM

import akka.actor.{ActorRef, FSM, Props}
import reactive2._

import scala.concurrent.duration._

sealed trait Command
case object StartCheckout
case object CancelCheckout
case object CloseCheckout

sealed trait Event
case class ItemAdded(id: String)
case class ItemRemoved(id: String)
case class CheckoutStarted (checkoutRef: ActorRef)
case object CheckoutCancelled
case object CheckoutClosed

case object TimerExpired
case object CartTimer

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
    case Event(Messages.AddItem(id), _) =>
      println("adding item")
      sender ! ItemAdded(id)
      goto(NonEmpty) using ItemsList(List(id))
  }

  when(NonEmpty) {
    case Event(Messages.AddItem(id), ItemsList(receivedData)) =>
      println("adding item")
      sender ! ItemAdded(id)
      stay using ItemsList(receivedData ++ List(id))
    case Event(Messages.RemoveItem(id), ItemsList(receivedData)) =>
      if (!receivedData.contains(id)) {
        sender ! Messages.Failed
        stay using ItemsList(receivedData)
      }
      else {
        println("removing item")
        sender ! ItemRemoved(id)
        receivedData.filter(item => item != id) match {
          case list if list.isEmpty => goto(Empty) using ItemsList(list)
          case list => stay using ItemsList(list)
        }

      }
    case Event(CartActions.TimerExpired, _) =>
      println("chart time expired")
      goto(Empty) using ItemsList(List())
    case Event(StartCheckout, ItemsList(items)) =>
      val checkoutActorFSM = context.system.actorOf(Props(new CheckoutFSM(context.self, items)))
      checkoutActorFSM ! CheckoutStarted(checkoutActorFSM)
      //replying to Order Manager startedCheckout and actorRef
      goto(InCheckout) using ItemsList(items) replying CheckoutStarted(checkoutActorFSM)
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
