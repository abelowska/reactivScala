package reactive2.FSM

import akka.actor.{ActorRef, FSM, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import reactive2._

import scala.concurrent.duration._
import scala.reflect.ClassTag

//sealed trait Command
case class SelectDeliveryMethod(deliveryMethod: String)
case class SelectPaymentMethod(paymentMethod: String)


sealed trait CheckoutEvent
case class DeliveryMethodSelected(deliveryMethod: String) extends CheckoutEvent
case class PaymentMethodSelected(paymentMethod: String) extends CheckoutEvent
case class PaymentServiceStarted(paymentRef: ActorRef) extends CheckoutEvent
case object PaymentReceived extends CheckoutEvent

case object CheckoutTimerExpired extends CheckoutEvent
case object CheckoutTimerStarted extends CheckoutEvent
case object CheckoutTimerStopped extends CheckoutEvent
case object PaymentTimerExpired extends CheckoutEvent
case object PaymentTimerStarted extends CheckoutEvent
case object PaymentTimerStopped extends CheckoutEvent
case object Cancel extends CheckoutEvent

case object CheckoutTimer
case object PaymentTimer

sealed trait CheckoutState extends FSMState
case object SelectingDelivery extends CheckoutState {
  override def identifier: String = "SelectingDelivery"
}
case object SelectingPaymentMethod extends CheckoutState {
  override def identifier: String = "SelectingPaymentMethod"
}
case object ProcessingPayment extends CheckoutState {
  override def identifier: String = "ProcessingPayment"
}
case object Cancelled extends CheckoutState {
  override def identifier: String = "Cancelled"
}
case object Closed extends CheckoutState {
  override def identifier: String = "Closed"
}

case class CheckoutData(cart: Cart, deliveryMethod: Option[String] = None, paymentMethod: Option[String] = None)


class CheckoutFSM(orderManager: ActorRef, cartActor: ActorRef, cartData: Cart, checkoutId: String = "checkout_id")(implicit val domainEventClassTag: ClassTag[CheckoutEvent]) extends PersistentFSM[CheckoutState, CheckoutData, CheckoutEvent] {

  var deliveryMethodSelected: Boolean = false
  var paymentMethodSelected: Boolean = false

  override def persistenceId: String = checkoutId

  //starting checkout timer
  setTimer(CheckoutTimer.toString, CheckoutTimerExpired, 30.second)

  //  startWith(Init, CheckoutData(cart))
  //
//  when(Init) {
//    case Event(CheckoutStarted(_), checkoutData) =>
//      goto(SelectingDelivery) using checkoutData
//  }

  startWith(SelectingDelivery, CheckoutData(cartData))

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(deliveryMethod), _) =>
      println("     CheckoutFSM receive: selecting delivery ")
      deliveryMethodSelected = true
      sender ! DeliveryMethodSelected(deliveryMethod)
      goto(SelectingPaymentMethod) applying DeliveryMethodSelected(deliveryMethod)
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPaymentMethod(paymentMethod), _) =>
      println("     CheckoutFSM receive: selecting payment ")
      sender ! PaymentMethodSelected(paymentMethod)
      paymentMethodSelected = true
      //w razie gdyby nie przyszla wiadomosc - flagi na potwierdzenie przyjscia wiadomosci
      goto(ProcessingPayment) applying (PaymentMethodSelected(paymentMethod), CheckoutTimerStopped, PaymentTimerStarted)
  }

  when(ProcessingPayment) {
    case Event(PaymentActions.PaymentReceived, _) =>
      println("     CheckoutFSM received: payment receive")
      stay applying (PaymentReceived, PaymentTimerStopped)
  }

//  onTransition {
//    case Init -> SelectingDelivery =>
//      setTimer(CheckoutTimer.toString, CheckoutTimerExpired, 1.minute)
//    case SelectingPaymentMethod -> ProcessingPayment =>
//      setTimer(PaymentTimer.toString, PaymentTimerExpired, 1.minute)
//      cancelTimer(CheckoutTimer.toString)
//  }

  whenUnhandled {
    case Event(Cancel, _) =>
      cancel
      stay
    case Event(CheckoutTimerExpired, _) =>
      stay applying CheckoutTimerExpired
    case Event(PaymentTimerExpired, _) =>
      stay applying PaymentTimerExpired
    case Event(e, s) ⇒
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      context stop self
      stay
  }

  def cancel {
    cartActor ! CancelCheckout
    context stop self

  }

  def close {
    cancelTimer(PaymentTimer.toString)
    cartActor ! CheckoutClosed
    orderManager ! CheckoutClosed
    context stop self
  }

  override def applyEvent(event: CheckoutEvent, currentData: CheckoutData): CheckoutData = {
    println("     CheckoutFSM: applyEvent " + event)
    event match {
      case DeliveryMethodSelected(deliveryMethod) =>
        currentData.copy(deliveryMethod = Some(deliveryMethod))

      case PaymentMethodSelected(paymentMethod) =>
        println("     CheckoutFSM: applyEvent  payment service started")
        val paymentActor = context.system.actorOf(Props(new Payment(self)))
        sender ! PaymentServiceStarted(paymentActor)
        currentData.copy(paymentMethod = Some(paymentMethod))
      case PaymentReceived =>
        println("     CheckoutFSM: applyEvent  payment receive")
        close
        currentData
//      case CheckoutTimerStarted =>
//        setTimer(CheckoutTimer.toString, TimerExpired, 15.second)
//        currentData
      case CheckoutTimerExpired =>
        cancel
        currentData
      case CheckoutTimerStopped =>
        cancelTimer(CheckoutTimer.toString)
        currentData
      case PaymentTimerStarted =>
        setTimer(PaymentTimer.toString, TimerExpired, 30.second)
        currentData
      case PaymentTimerExpired =>
        cancel
        currentData
      case PaymentTimerStopped =>
        cancelTimer(PaymentTimer.toString)
        currentData

    }
  }
}
