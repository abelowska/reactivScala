package reactive2.FSM

import akka.actor.{ActorRef, FSM}
import reactive2._

import scala.concurrent.duration._

sealed trait Command
case class SelectDeliveryMethod(deliveryMethod: String)
case class SelectPaymentMethod(paymentMethod: String)


sealed trait Event
case class DeliveryMethodSelected(deliveryMethod: String)
case class PaymentMethodSelected(paymentMethod: String)
case class PaymentServiceStarted(paymentRef: ActorRef)

case object CheckoutTimerExpired
case object PaymentTimerExpired
case object PaymentReceive
case object CheckoutTimer
case object PaymentTimer
case object Cancel


sealed trait CheckoutState
case object Init extends CheckoutState
case object SelectingDelivery extends CheckoutState
case object SelectingPaymentMethod extends CheckoutState
case object ProcessingPayment extends CheckoutState
case object Cancelled extends CheckoutState
case object Closed extends CheckoutState

case class CheckoutData(items: List[String], deliveryMethod: Option[String] = None, paymentMethod: Option[String] = None)


class CheckoutFSM(cartActor: ActorRef, items: List[String]) extends FSM[CheckoutState, CheckoutData] {

  var deliveryMethodSelected: Boolean = false
  var paymentMethodSelected: Boolean = false

  startWith(Init, CheckoutData(items))

  when(Init) {
    case Event(CheckoutStarted(_), checkoutData) =>
      goto(SelectingDelivery) using checkoutData
  }

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(deliveryMethod), CheckoutData(itemsList, _, payment)) =>
      println("selecting delivery ")
      deliveryMethodSelected = true
      sender ! DeliveryMethodSelected(deliveryMethod)
      goto(SelectingPaymentMethod) using CheckoutData(itemsList, Some(deliveryMethod), payment)
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPaymentMethod(paymentMethod), CheckoutData(itemsList, delivery, _)) =>
      println("selecting payment ")
      sender ! PaymentMethodSelected(paymentMethod)
      paymentMethodSelected = true
      //w razie gdyby nie przyszla wiadomosc - flagi na potwierdzenie przyjscia wiadomosci
      if (deliveryMethodSelected && paymentMethodSelected) {
        //implementacja paymenta val paymentActor = context.system.actorOf()
        sender ! PaymentServiceStarted(paymentActor)
        goto(ProcessingPayment) using CheckoutData(itemsList, delivery, Some(paymentMethod))
      }
      else {
        stay
      }
  }

  when(ProcessingPayment) {
    case Event(PaymentReceive, _) =>
      println("finishing... ")
      //      close
      stay
  }

  onTransition {
    case Init -> SelectingDelivery =>
      setTimer(CheckoutTimer.toString, CheckoutTimerExpired, 1.minute)
    case SelectingPaymentMethod -> ProcessingPayment =>
      setTimer(PaymentTimer.toString, PaymentTimerExpired, 1.minute)
      cancelTimer(CheckoutTimer.toString)
  }

  whenUnhandled {
    case Event(Cancel, _) =>
      cancel
      stay
    case Event(CheckoutTimerExpired, _) =>
      cancel
      stay
    case Event(CheckoutTimerExpired, _) =>
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
