package reactive2.FSM

import java.net.URI
import akka.actor.{ActorRef, FSM, Props}

import scala.concurrent.duration._

//sealed trait Command
case object StartCheckout
case object CancelCheckout
case object CloseCheckout

//sealed trait Event
case class ItemAdded(id: String)
case class ItemRemoved(id: String)
case class CheckoutStarted(checkoutRef: ActorRef)
case object CheckoutCancelled
case object CheckoutClosed
case object CartEmpty

case object TimerExpired
case object CartTimer

// states
sealed trait CartState
case object Empty extends CartState
case object NonEmpty extends CartState
case object InCheckout extends CartState

//data
sealed trait CartData {
  val items: Map[URI, Item]

  def addItem(item: Item): CartData

  def removeItem(uri: URI, count: Int): CartData
}
//case class CartContent(cart: Cart) extends CartData
case class Item(uri: URI, id: String, price: BigDecimal, count: Int)

case class Cart(items: Map[URI, Item]) extends CartData {
  def addItem(item: Item): Cart = {
    val currentCount = if (items contains item.uri) items(item.uri).count else 0
    copy(items = items.updated(item.uri, item.copy(count = currentCount + item.count)))
  }

  def removeItem(uri: URI, count: Int): Cart = {
    //TODO add validating count
    val item = items(uri)
    println(item)
    println(count)
    println(item.count)
    if (item.count - count > 0 ) {
      copy(items = items.updated(item.uri, item.copy(count = items(item.uri).count - item.count)))
    } else {
      println("dupa")
      copy(items = items - uri)
    }
  }
}

class CartFSM extends FSM[CartState, CartData] {

  startWith(Empty, Cart(Map.empty))

  when(Empty) {
    case Event(Messages.AddItem(id, count), cart) =>
      println("adding item")
      sender ! ItemAdded(id)
      goto(NonEmpty) using cart.addItem(Item(new URI(id), id, 1000, count))
  }

  when(NonEmpty) {
    case Event(Messages.AddItem(id, count), cart) =>
      println("adding item")
      sender ! ItemAdded(id)
      stay using cart.addItem(Item(new URI(id), id, 1000, count))
    case Event(Messages.RemoveItem(id, count), cart) =>
      println("removing item")
      sender ! ItemRemoved(id)

      println(cart.items)
      val newCart = cart.removeItem(new URI(id), count)
      println(newCart.items)
      newCart.items match {
        case newCart.items if newCart.items.isEmpty => goto(Empty) using newCart
        case newCart.items => stay using newCart
      }

    case Event(TimerExpired, _) =>
      println("chart time expired")
      goto(Empty) using Cart(Map.empty)
    case Event(StartCheckout, cart) =>
      val checkoutActorFSM = context.system.actorOf(Props(new CheckoutFSM(context.parent, context.self, cart)))
      checkoutActorFSM ! CheckoutStarted(checkoutActorFSM)
      //replying to Order Manager startedCheckout and actorRef
      goto(InCheckout) using cart replying CheckoutStarted(checkoutActorFSM)
  }

  when(InCheckout) {
    case Event(CancelCheckout, cartData) =>
      goto(NonEmpty) using cartData
    case Event(CheckoutClosed, _) =>
      context.parent ! CartEmpty
      goto(Empty) using Cart(Map.empty)
  }

  onTransition {
    case Empty -> NonEmpty =>
      setTimer(CartTimer.toString, TimerExpired, 15.second)
    case NonEmpty -> _ =>
      cancelTimer(CartTimer.toString)
    case InCheckout -> NonEmpty =>
      setTimer(CartTimer.toString, TimerExpired, 15.second)
  }

  whenUnhandled {
    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      context stop self
      stay
  }
  initialize()
}
