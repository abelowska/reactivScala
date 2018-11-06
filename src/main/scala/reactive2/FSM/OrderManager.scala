package reactive2.FSM

import akka.actor.{ActorRef, FSM, Props}

sealed trait Command
object OrderManagerEvent {
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String)
  case class SelectDeliveryMethod(delivery: String)
  case class SelectPaymentMethod(payment: String)
}

sealed trait OrderEvent
case object Persist

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
      println("OrderManager received: add item message")
      val cartActor = context.system.actorOf(Props(new CartFSM(self)))
      cartActor ! Messages.AddItem(itemId, count)
      goto(OrderManagerState.Open) using OrderManagerData.CartDataWithSender(cartActor, sender)
    case Event(Persist, OrderManagerData.Empty()) =>
      println("rOrderManager received: persist message")
      val cartActor = context.system.actorOf(Props(new CartFSM(self)))
      goto(OrderManagerState.Open) using OrderManagerData.CartData(cartActor)
  }

  when(OrderManagerState.Open) {
    case Event(Messages.AddItem(itemId, count), OrderManagerData.CartData(cartRef)) =>
      println("OrderManager receive: add item")
      cartRef ! Messages.AddItem(itemId, count)
      stay using OrderManagerData.CartDataWithSender(cartRef, sender)
    case Event(ItemAdded(_, _), OrderManagerData.CartDataWithSender(cartRef, sender)) =>
      println("OrderManager received: item added")
      sender ! Done
      stay using OrderManagerData.CartData(cartRef)
    case Event(Messages.RemoveItem(itemId, count), OrderManagerData.CartData(cartRef)) =>
      println("OrderManager received: remove item")
      cartRef ! Messages.RemoveItem(itemId, count)
      stay using OrderManagerData.CartDataWithSender(cartRef, sender)
    case Event(ItemRemoved(_, _), OrderManagerData.CartDataWithSender(cartRef, sender)) =>
      println("OrderManager received: item removed")
      sender ! Done
      stay using OrderManagerData.CartData(cartRef)
    case Event(Messages.Buy, OrderManagerData.CartData(cartRef)) =>
      println("OrderManager received: buy massage")
      cartRef ! StartCheckout
      stay using OrderManagerData.CartDataWithSender(cartRef, sender)
    case Event(CheckoutStarted(checkoutRef), OrderManagerData.CartDataWithSender(_, sender)) =>
      println("OrderManager received: checkout started")
      sender ! Done
      goto(OrderManagerState.InCheckout) using OrderManagerData.InCheckoutData(checkoutRef)
    case Event(CheckoutStarted(checkoutRef), OrderManagerData.CartData(_)) =>
      println("OrderManager received: checkout started persist one")
      sender ! Done
      goto(OrderManagerState.InCheckout) using OrderManagerData.InCheckoutData(checkoutRef)
  }

  when(OrderManagerState.InCheckout) {
    case Event(OrderManagerEvent.SelectDeliveryAndPaymentMethod(deliveryMethod, paymentMethod), OrderManagerData.InCheckoutData(checkoutRef)) =>
      println("OrderManager received: select delivery and payment method")
      checkoutRef ! SelectDeliveryMethod(deliveryMethod)
      checkoutRef ! SelectPaymentMethod(paymentMethod)
      stay using OrderManagerData.InCheckoutDataWithSender(checkoutRef, sender)

    case Event(OrderManagerEvent.SelectDeliveryMethod(delivery), OrderManagerData.InCheckoutData(checkoutRef)) =>
      println("OrderManager received: select delivery method")
      checkoutRef ! SelectDeliveryMethod(delivery)
      stay using OrderManagerData.InCheckoutDataWithSender(checkoutRef, sender)

    case Event(OrderManagerEvent.SelectPaymentMethod(payment), OrderManagerData.InCheckoutData(checkoutRef)) =>
      println("OrderManager received: select payment method")
      checkoutRef ! SelectPaymentMethod(payment)
      stay using OrderManagerData.InCheckoutDataWithSender(checkoutRef, sender)

    case Event(DeliveryMethodSelected(_), OrderManagerData.InCheckoutDataWithSender(checkoutRef, _)) =>
      println("OrderManager received: delivery method selected")
      stay using OrderManagerData.InCheckoutData(checkoutRef)

    case Event(PaymentMethodSelected(_), OrderManagerData.InCheckoutDataWithSender(checkoutRef, sender)) =>
      println("OrderManager received: payment method selected")
      stay using OrderManagerData.InCheckoutDataWithSender(checkoutRef, sender)
    case Event(PaymentServiceStarted(paymentRef), OrderManagerData.InCheckoutDataWithSender(_, sender)) =>
      println("OrderManager received: payment service started")
      sender ! Done
      goto(OrderManagerState.InPayment) using OrderManagerData.InPaymentData(paymentRef)
  }

  when(OrderManagerState.InPayment) {
    case Event(Messages.Pay, OrderManagerData.InPaymentData(paymentRef)) =>
      println("OrderManager received: pay")
      paymentRef ! Messages.Pay
      stay using OrderManagerData.InPaymentDataWithSender(paymentRef, sender)
    case Event(PaymentActions.PaymentConfirmed, OrderManagerData.InPaymentDataWithSender(paymentRef, sender))  =>
      println("OrderManager received: payment confirmed")
      sender ! Done
      goto(OrderManagerState.Finished) using OrderManagerData.Empty()
  }

  when(OrderManagerState.Finished) {
    case Event(CheckoutClosed, OrderManagerData.Empty()) =>
      println("OrderManager received: checkout closed")
      stay
    case Event(CartEmpty, OrderManagerData.Empty()) =>
      println("OrderManager received: cart empty")
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
