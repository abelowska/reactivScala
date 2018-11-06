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
case class ItemRemoved(id: String, count: Int) extends CartEvent
case class CheckoutStarted(checkoutRef: ActorRef) extends CartEvent
case object CheckoutCancelled extends CartEvent
case object CheckoutClosed extends CartEvent
case object CheckoutClosedDebug extends CartEvent
case object CartEmpty extends CartEvent
case object TimerExpired extends CartEvent
case object TimerStarted extends CartEvent
case object TimerStopped extends CartEvent

case object CartTimer

// states
sealed trait CartState extends FSMState
case object Empty extends CartState {
  override def identifier: String = "Empty"
}
case object NonEmpty extends CartState {
  override def identifier: String = "NonEmpty"
}
case object InCheckout extends CartState {
  override def identifier: String = "InCheckout"
}

//data
//sealed trait CartData {
//  val items: Map[URI, Item]
//
//  def addItem(item: Item): CartData
//
//  def removeItem(uri: URI, count: Int): CartData
//}
case class Item(uri: URI, id: String, price: BigDecimal, count: Int)

case class Cart(items: Map[URI, Item]) {
  def addItem(item: Item): Cart = {
    val currentCount = if (items contains item.uri) items(item.uri).count else 0
    copy(items = items.updated(item.uri, item.copy(count = currentCount + item.count)))
  }

  def removeItem(uri: URI, count: Int): Cart = {
    //TODO add validating count
    val item = items(uri)
    if (item.count - count > 0) {
      val newCount = items(item.uri).count - count
      copy(items = items.updated(item.uri, item.copy(count = newCount)))
    } else {
      copy(items = items - uri)
    }
  }
}

class CartFSM(orderManager: ActorRef, chartId: String = "cart_id")(implicit val domainEventClassTag: ClassTag[CartEvent]) extends PersistentFSM[CartState, Cart, CartEvent] {

  override def persistenceId: String = chartId

  startWith(Empty, Cart(Map.empty))

  when(Empty) {
    case Event(Messages.AddItem(id, count), _) =>
      println("   CartFSM adding item")
      orderManager ! ItemAdded(id, count)
      goto(NonEmpty) applying(ItemAdded(id, count), TimerStarted)
  }

  when(NonEmpty) {
    case Event(Messages.AddItem(id, count), _) =>
      println("   CartFSM adding item")
      orderManager ! ItemAdded(id, count)
      //      saveStateSnapshot()
      stay applying ItemAdded(id, count)
    case Event(Messages.RemoveItem(id, count), cart) =>
      println("   CartFSM removing item")
      orderManager ! ItemRemoved(id, count)

      if (cart.removeItem(new URI(id), count).items.isEmpty) {
        //        saveStateSnapshot()
        goto(Empty) applying(ItemRemoved(id, count), TimerStopped)
      } else {
        //        saveStateSnapshot()
        stay applying ItemRemoved(id, count)
      }
    case Event(TimerExpired, _) =>
      println("   CartFSM chart time expired")
      goto(Empty) applying TimerExpired
    case Event(StartCheckout, _) =>
      println("   CartFSM start checkout")
      //replying to Order Manager startedCheckout and actorRef
      goto(InCheckout) applying(CheckoutStarted(null), TimerStopped)
  }

  when(InCheckout) {
    case Event(CancelCheckout, _) =>
      goto(NonEmpty) applying(CheckoutCancelled, TimerStarted)
    case Event(CheckoutClosed, _) =>
      println("   CartFSM received: checkout closed")
      orderManager ! CartEmpty
      goto(Empty) applying CheckoutClosedDebug
    case a =>
      println(a)
      stay
  }

  //  onTransition {
  //    case Empty -> NonEmpty =>
  //      setTimer(CartTimer.toString, TimerExpired, 15.second)
  //    case NonEmpty -> _ =>
  ////      cancelTimer(CartTimer.toString)
  //    case InCheckout -> NonEmpty =>
  //      setTimer(CartTimer.toString, TimerExpired, 15.second)
  //  }

  whenUnhandled {
    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      context stop self
      stay
  }

  override def applyEvent(event: CartEvent, cart: Cart): Cart = {
    println("   CartFSM " + event + " " + cart)
    event match {
      case ItemAdded(id, count) =>
        println("   CartFSM " + event + "added")
        println(cart)
        cart.addItem(Item(new URI(id), id, 1000, count))
      case ItemRemoved(id, count) =>
        println("   CartFSM " + event + " removed")
        cart.removeItem(new URI(id), count)
      case CartEmpty =>
        println("   CartFSM " + event + "empty")
        Cart(Map.empty)
      case CheckoutStarted(_) =>
        val checkoutActorFSM = context.system.actorOf(Props(new CheckoutFSM(orderManager, self, cart)))
//        checkoutActorFSM ! CheckoutStarted(checkoutActorFSM)
        orderManager ! CheckoutStarted(checkoutActorFSM)
        println("    CartFSM checkoutStarted " + cart)
        cart
      case CheckoutCancelled => cart
      case CheckoutClosedDebug => Cart(Map.empty)
      case TimerStarted =>
        setTimer(CartTimer.toString, TimerExpired, 15.second)
        cart
      case TimerExpired => Cart(Map.empty)
      case TimerStopped =>
        cancelTimer(CartTimer.toString)
        cart
    }
  }
}
