import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestFSMRef, TestKit}
import akka.util.Timeout
import akka.pattern.ask
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import reactive2.FSM._

import scala.concurrent.duration._

class CartTest
  extends TestKit(ActorSystem("CartTest"))
    with WordSpecLike
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers
    with Eventually {

  implicit val timeout: Timeout = 1.second

  "A cartFSM" must {
    "have a proper flow" in {

      def sendMessageAndValidateState(
                                       cartFSM: TestFSMRef[CartState, CartData, CartFSM],
                                       message: Any,
                                       expectedState: CartState
                                     ): Unit = {
        cartFSM ! message
        cartFSM.stateName shouldBe expectedState
      }

      val cartFSM = TestFSMRef[CartState, CartData, CartFSM](new CartFSM())
      cartFSM.stateName shouldBe Empty

      sendMessageAndValidateState(cartFSM, Messages.AddItem("rollerblades", 2), NonEmpty)

      sendMessageAndValidateState(cartFSM, Messages.RemoveItem("rollerblades", 2), Empty)

      sendMessageAndValidateState(cartFSM, Messages.AddItem("rollerblades", 2), NonEmpty)

      sendMessageAndValidateState(cartFSM, StartCheckout, InCheckout)

    }

    "handle timeout correctly" in {

      val cartFSM = TestFSMRef[CartState, CartData, CartFSM](new CartFSM())
      cartFSM.stateName shouldBe Empty

      cartFSM ! Messages.AddItem("rollerblades", 2)

      cartFSM.isTimerActive(CartTimer.toString) shouldBe true

      eventually(timeout(scaled(16 seconds))) {
        cartFSM.isTimerActive(CartTimer.toString) shouldBe false
        cartFSM.stateName shouldBe Empty
      }

    }

    "change cart content" in {

      val cartFSM = TestFSMRef[CartState, CartData, CartFSM](new CartFSM())
      cartFSM.stateName shouldBe Empty

      cartFSM ! Messages.AddItem("rollerblades", 2)
      cartFSM ! Messages.AddItem("berdw", 2)

      cartFSM.stateData match {
        case Cart(items) => items.size shouldBe 2
      }

      cartFSM ! Messages.RemoveItem("rollerblades", 1)

      cartFSM.stateData match {
        case Cart(items) => items.size shouldBe 2
      }

      cartFSM ! Messages.RemoveItem("berdw", 2)

      cartFSM.stateData match {
        case Cart(items) => items.size shouldBe 1
      }
    }

    "start checkout actor" in {

      val cartFSM = TestFSMRef[CartState, CartData, CartFSM](new CartFSM())
      cartFSM.stateName shouldBe Empty

      cartFSM ! Messages.AddItem("rollerblades", 2)
//      cartFSM ! StartCheckout

      (cartFSM ? StartCheckout).futureValue match {
        case CheckoutStarted(_: ActorRef) => None
        case _ => fail()
      }
    }

  }

}