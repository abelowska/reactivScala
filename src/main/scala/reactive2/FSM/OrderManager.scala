package reactive2.FSM

import akka.actor.{ActorRef, FSM, Props}

sealed trait Command
case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String)

sealed trait Ack
case object Done extends Ack //trivial ACK

//states
sealed trait OrderManagerState

object OrderManagerState {
  case object Uninitialized extends OrderManagerState
  case object Open extends OrderManagerState
  case object InCheckout extends OrderManagerState
  case object InPayment extends OrderManagerState
  case object Finished extends OrderManagerState

}
//data
sealed trait OrderManagerData

object OrderManagerData{
  case class Empty()                                                           extends OrderManagerData
  case class CartData(cartRef: ActorRef)                                       extends OrderManagerData
  case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef)           extends OrderManagerData
  case class InCheckoutData(checkoutRef: ActorRef)                             extends OrderManagerData
  case class InCheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends OrderManagerData
  case class InPaymentData(paymentRef: ActorRef)                               extends OrderManagerData
  case class InPaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef)   extends OrderManagerData
}



class OrderManager extends FSM[OrderManagerState, OrderManagerData] {

  var deliveryMethodSelected: Boolean = false
  var paymentMethodSelected: Boolean = false

  startWith(OrderManagerState.Uninitialized, OrderManagerData.Empty())

  when(OrderManagerState.Uninitialized) {
    case Event(Messages.AddItem(itemId, count), OrderManagerData.Empty()) =>
      println("received: add item message")
      val cartActor = context.system.actorOf(Props[CartFSM])
      cartActor ! Messages.AddItem(itemId, count)
      goto(OrderManagerState.Open) using OrderManagerData.CartDataWithSender(cartActor, sender)
  }

  when(OrderManagerState.Open) {
    case Event(Messages.AddItem(itemId, count), OrderManagerData.CartData(cartRef)) =>
      println("receive: add item")
      cartRef ! Messages.AddItem(itemId, count)
      stay using OrderManagerData.CartDataWithSender(cartRef, sender)
    case Event(ItemAdded(_), OrderManagerData.CartDataWithSender(cartRef, sender)) =>
      println("received: item added")
      sender ! Done
      stay using OrderManagerData.CartData(cartRef)
    case Event(Messages.RemoveItem(itemId, count), OrderManagerData.CartData(cartRef)) =>
      println("received: remove item")
      cartRef ! Messages.RemoveItem(itemId, count)
      stay using OrderManagerData.CartDataWithSender(cartRef, sender)
    case Event(ItemRemoved(_), OrderManagerData.CartDataWithSender(cartRef, sender)) =>
      println("received: item removed")
      sender ! Done
      stay using OrderManagerData.CartData(cartRef)
    case Event(Messages.Buy, OrderManagerData.CartData(cartRef)) =>
      println("received: buy massage")
      cartRef ! StartCheckout
      stay using OrderManagerData.CartDataWithSender(cartRef, sender)
    case Event(CheckoutStarted(checkoutRef), OrderManagerData.CartDataWithSender(_, sender)) =>
      println("received: checkout started")
      sender ! Done
      goto(OrderManagerState.InCheckout) using OrderManagerData.InCheckoutData(checkoutRef)
  }

  when(OrderManagerState.InCheckout) {
    case Event(SelectDeliveryAndPaymentMethod(deliveryMethod, paymentMethod), OrderManagerData.InCheckoutData(checkoutRef)) =>
      println("received: select delivery and payment method")
      checkoutRef ! SelectDeliveryMethod(deliveryMethod)
      checkoutRef ! SelectPaymentMethod(paymentMethod)
      stay using OrderManagerData.InCheckoutDataWithSender(checkoutRef, sender)
    case Event(DeliveryMethodSelected(_), OrderManagerData.InCheckoutDataWithSender(_, _)) =>
      println("received: delivery method selected")
      stay
    case Event(PaymentMethodSelected(_), OrderManagerData.InCheckoutDataWithSender(_, _)) =>
      println("received: payment method selected")
      stay
    case Event(PaymentServiceStarted(paymentRef), OrderManagerData.InCheckoutDataWithSender(_, sender)) =>
      println("received: payment service started")
      sender ! Done
      goto(OrderManagerState.InPayment) using OrderManagerData.InPaymentData(paymentRef)
  }

  when(OrderManagerState.InPayment) {
    case Event(Messages.Pay, OrderManagerData.InPaymentData(paymentRef)) =>
      println("received: pay")
      paymentRef ! Messages.Pay
      stay using OrderManagerData.InPaymentDataWithSender(paymentRef, sender)
    case Event(PaymentActions.PaymentConfirmed, OrderManagerData.InPaymentDataWithSender(paymentRef, sender))  =>
      println("received: payment confirmed")
      sender ! Done
      goto(OrderManagerState.Finished) using OrderManagerData.Empty()
  }

  when(OrderManagerState.Finished) {
    case Event(CheckoutClosed, OrderManagerData.Empty()) =>
      println("received: checkout closed")
      stay
    case Event(CartEmpty, OrderManagerData.Empty()) =>
      println("received: cart empty")
      stay
  }

  whenUnhandled {
    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      context stop self
      stay
  }
  initialize()
}
