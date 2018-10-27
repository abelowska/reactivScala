package reactive2.FSM

import akka.actor.Actor

object PaymentActions {
  sealed trait Events
  case object PaymentConfirmed
  case object PaymentReceived
}

class Payment extends Actor{
  override def receive: Receive = {
    case Messages.Pay =>
      println("received in Payment Actor pay")
      sender ! PaymentActions.PaymentConfirmed
      context.parent ! PaymentActions.PaymentReceived
    case _ => Messages.Failed
  }
}
