import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{TestFSMRef, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import reactive2.FSM.Messages.{AddItem, Buy, Pay}
import reactive2.FSM.OrderManagerState.{Finished, InPayment, Open, Uninitialized}
import reactive2.FSM._

class OrderManagerTest
  extends TestKit(ActorSystem("OrderManagerTest"))
    with WordSpecLike
    with BeforeAndAfterAll
    with ScalaFutures
    with Matchers {

  implicit val timeout: Timeout = 1.second

  "An order manager" must {
    "supervise whole order process" in {

      def sendMessageAndValidateState(
                                       orderManager: TestFSMRef[OrderManagerState, OrderManagerData, OrderManager],
                                       message: Any,
                                       expectedState: OrderManagerState
                                     ): Unit = {
        (orderManager ? message).mapTo[Ack].futureValue shouldBe Done
        orderManager.stateName shouldBe expectedState
      }

      val orderManager = TestFSMRef[OrderManagerState, OrderManagerData, OrderManager](new OrderManager())
      orderManager.stateName shouldBe Uninitialized

      sendMessageAndValidateState(orderManager, AddItem("rollerblades", 2), Open)

      sendMessageAndValidateState(orderManager, Buy, OrderManagerState.InCheckout)

      sendMessageAndValidateState(orderManager, OrderManagerEvent.SelectDeliveryAndPaymentMethod("paypal", "inpost"), InPayment)

      sendMessageAndValidateState(orderManager, Pay, Finished)
    }
  }

}