package reactive2.FSM

import akka.actor.{Actor, ActorRef}

object PaymentActions {
  sealed trait Events
  case object PaymentConfirmed
  case object PaymentReceived
}

class Payment(checkoutActor: ActorRef) extends Actor{
  override def receive: Receive = {
    case Messages.Pay =>
      println("       Payment received: pay")
      sender ! PaymentActions.PaymentConfirmed
      checkoutActor ! PaymentActions.PaymentReceived
    case _ => Messages.Failed
  }
}
