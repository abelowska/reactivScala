package reactive2

import akka.actor.{ActorRef, FSM}

import scala.concurrent.duration._


sealed trait CheckoutState
case object Init extends CheckoutState
case object SelectingDelivery extends CheckoutState
case object SelectingPaymentMethod extends CheckoutState
case object ProcessingPayment extends CheckoutState
case object Cancelled extends CheckoutState
case object Closed extends CheckoutState

case class CheckoutData(items: List[String], deliveryMethod: Option[String] = None, paymentMethod: Option[String] = None)


class CheckoutFSM(cartActor: ActorRef, items: List[String]) extends FSM[CheckoutState, CheckoutData] {

  startWith(Init, CheckoutData(items))

  when(Init) {
    case Event(CartActions.CheckoutStarted, checkoutData) =>
      goto(SelectingDelivery) using checkoutData
  }

  when(SelectingDelivery) {
    case Event(CheckoutActions.SelectDeliveryMethod(deliveryMethod), CheckoutData(itemsList, _, payment)) =>
      println("selecting delivery ")
      sender ! CheckoutActions.DeliveryMethodSelected(deliveryMethod)
      goto(SelectingPaymentMethod) using CheckoutData(itemsList, Some(deliveryMethod), payment)
  }

  when(SelectingPaymentMethod) {
    case Event(CheckoutActions.SelectPaymentMethod(paymentMethod), CheckoutData(itemsList, delivery, _)) =>
      println("selecting payment ")
      sender ! CheckoutActions.PaymentMethodSelected(paymentMethod)
      goto(ProcessingPayment) using CheckoutData(itemsList, delivery, Some(paymentMethod))
  }

  when(ProcessingPayment) {
    case Event(CheckoutActions.PaymentReceive, _) =>
      println("finishing... ")
      close
      stay
  }

  onTransition {
    case Init -> SelectingDelivery =>
      setTimer(CheckoutActions.CheckoutTimer.toString, CheckoutActions.CheckoutTimerExpired, 1.minute)
    case SelectingPaymentMethod -> ProcessingPayment =>
      setTimer(CheckoutActions.PaymentTimer.toString, CheckoutActions.PaymentTimerExpired, 1.minute)
      cancelTimer(CheckoutActions.CheckoutTimer.toString)
  }

  whenUnhandled {
    case Event(CheckoutActions.Cancel, _) =>
      sender ! CheckoutActions.Cancelled
      cancel
      stay
    case Event(CheckoutActions.CheckoutTimerExpired, _) =>
      cancel
      stay
    case Event(CheckoutActions.CheckoutTimerExpired, _) =>
      cancel
      stay
    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      context stop self
      stay
  }

  def cancel {
    cartActor ! CartActions.CancelCheckout
    context stop self

  }

  def close {
    cartActor ! CartActions.CloseCheckout
    context stop self
  }
  initialize()
}
