package reactive2

import akka.actor.{Actor, ActorRef, Timers}
import akka.event.LoggingReceive

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._


object CheckoutActions {

  sealed trait Command
  case class SelectDeliveryMethod(deliveryMethod: String)
  case class SelectPaymentMethod(paymentMethod: String)

  sealed trait Event
  case class DeliveryMethodSelected(deliveryMethod: String)
  case class PaymentMethodSelected(paymentMethod: String)

  case object CheckoutTimerExpired

  case object PaymentTimerExpired

  case object Cancel

  case object PaymentReceive

  case object CheckoutTimer

  case object PaymentTimer

  case object Cancelled

}

class Checkout(cartActor: ActorRef, items: ListBuffer[String]) extends Actor with Timers {

  override def receive: Receive = LoggingReceive {
    case CartActions.CheckoutStarted =>
      timers.startSingleTimer(CheckoutActions.CheckoutTimer, CheckoutActions.CheckoutTimerExpired, 1.minute)
      context become selectingDelivery
    case _ =>
  }

  def selectingDelivery: Receive = LoggingReceive {
    case CheckoutActions.CheckoutTimerExpired =>
      cancel
    case CheckoutActions.Cancel =>
      sender ! CheckoutActions.Cancelled
      cancel
    case CheckoutActions.SelectDeliveryMethod(deliveryMethod) =>
      sender ! CheckoutActions.DeliveryMethodSelected(deliveryMethod)
      context become selectingPayment(deliveryMethod)
    case _ =>
  }

  def selectingPayment(deliveryMethod: String): Receive = LoggingReceive {
    case CheckoutActions.CheckoutTimerExpired =>
      cancel
    case CheckoutActions.Cancel =>
      sender ! CheckoutActions.Cancelled
      cancel
    case CheckoutActions.SelectPaymentMethod(paymentMethod) =>
      sender ! CheckoutActions.PaymentMethodSelected(paymentMethod)
      timers.cancel(CheckoutActions.CheckoutTimer)
      timers.startSingleTimer(CheckoutActions.PaymentTimer, CheckoutActions.PaymentTimerExpired, 1.minute)
      context become processingPayment(deliveryMethod, paymentMethod)
    case _ =>
  }

  def processingPayment(deliveryMethod: String, paymentMethod: String): Receive = LoggingReceive {
    case CheckoutActions.PaymentTimerExpired =>
      cancel
    case CheckoutActions.Cancel =>
      sender ! CheckoutActions.Cancelled
      cancel
    case CheckoutActions.PaymentReceive =>
      close
    case _ =>
  }

  def cancel {
    cartActor ! CartActions.CancelCheckout
    context stop self
  }

  def close {
    cartActor ! CartActions.CloseCheckout
    context stop self
  }


}
