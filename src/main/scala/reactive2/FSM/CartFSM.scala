package reactive2.FSM

import java.net.URI

import akka.persistence.fsm.PersistentFSM.FSMState
import akka.actor.{ActorRef, FSM, Props}
import akka.persistence.fsm.PersistentFSM

import scala.concurrent.duration._
import scala.reflect.ClassTag

//sealed trait Command
case object StartCheckout
case object CancelCheckout
case object CloseCheckout

//sealed trait Event
sealed trait CartEvent
case class ItemAdded(id: String, count: Int) extends CartEvent
case class ItemRemoved() extends CartEvent
case class EmptinessChecked(id: String, count: Int) extends CartEvent
case class CheckoutStarted(checkoutRef: ActorRef) extends CartEvent
case object CheckoutCancelled extends CartEvent
case object CheckoutClosed extends CartEvent
case object CartEmpty extends CartEvent
case object TimerExpired extends CartEvent

case object CartTimer

// states
sealed trait CartState extends FSMState
case object Empty extends CartState {
  override def identifier: String = "Empty"
}
case object NonEmpty extends CartState {
  override def identifier: String = "NonEmpty"
}
case object CheckEmptiness extends CartState {
  override def identifier: String = "CheckEmptiness"
}
case object InCheckout extends CartState {
  override def identifier: String = "InCheckout"
}

//data
sealed trait CartData {
  val items: Map[URI, Item]
  def addItem(item: Item): CartData
  def removeItem(uri: URI, count: Int): CartData
}
case class Item(uri: URI, id: String, price: BigDecimal, count: Int)

case class Cart(items: Map[URI, Item]) extends CartData {
  def addItem(item: Item): Cart = {
    val currentCount = if (items contains item.uri) items(item.uri).count else 0
    copy(items = items.updated(item.uri, item.copy(count = currentCount + item.count)))
  }

  def removeItem(uri: URI, count: Int): Cart = {
    //TODO add validating count
    val item = items(uri)
    if (item.count - count > 0 ) {
      copy(items = items.updated(item.uri, item.copy(count = items(item.uri).count - item.count)))
    } else {
      copy(items = items - uri)
    }
  }
}

class CartFSM(_persistenceId: String = "persistent-toggle-fsm-id-1")(implicit val domainEventClassTag: ClassTag[CartEvent]) extends PersistentFSM[CartState, CartData, CartEvent] {

  override def persistenceId = _persistenceId

  startWith(Empty, Cart(Map.empty))

  when(Empty) {
    case Event(Messages.AddItem(id, count), _) =>
      println("adding item")
      sender ! ItemAdded(id, count)
      goto(NonEmpty) applying ItemAdded(id, count)
  }

  when(NonEmpty) {
    case Event(Messages.AddItem(id, count), _) =>
      println("adding item")
      sender ! ItemAdded(id, count)
      stay applying ItemAdded(id, count)
    case Event(Messages.RemoveItem(id, count), _) =>
      println("removing item")
      sender ! ItemRemoved()
      goto(CheckEmptiness) applying EmptinessChecked(id, count) andThen {
        case newCart if newCart.items.isEmpty =>
          saveStateSnapshot()
          goto(Empty) applying ItemRemoved()
        case newCart =>
          saveStateSnapshot()
          stay applying ItemRemoved()
      }
    case Event(TimerExpired, _) =>
      println("chart time expired")
      goto(Empty) applying CartEmpty
    case Event(StartCheckout, cart) =>
      val checkoutActorFSM = context.system.actorOf(Props(new CheckoutFSM(context.parent, context.self, cart)))
      checkoutActorFSM ! CheckoutStarted(checkoutActorFSM)
      //replying to Order Manager startedCheckout and actorRef
      goto(InCheckout) applying CheckoutStarted(null) replying CheckoutStarted(checkoutActorFSM)
  }

  when(InCheckout) {
    case Event(CancelCheckout, _) =>
      goto(NonEmpty) applying CheckoutCancelled
    case Event(CheckoutClosed, _) =>
      context.parent ! CartEmpty
      goto(Empty) applying CheckoutClosed
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

  override def applyEvent(event: CartEvent, cart: CartData): CartData = {
    event match {
      case ItemAdded(id, count) ⇒ cart.addItem(Item(new URI(id), id, 1000, count))
      case ItemRemoved() => cart
      case EmptinessChecked(id, count) => cart.removeItem(new URI(id), count)
      case CartEmpty => Cart(Map.empty)
      case CheckoutStarted(_) => cart
      case CheckoutCancelled => cart
      case CheckoutClosed => Cart(Map.empty)
    }
  }
}
