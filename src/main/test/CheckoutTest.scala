//import java.net.URI
//
//import akka.actor.{ActorRef, ActorSystem}
//import akka.testkit.{TestFSMRef, TestKit}
//import akka.util.Timeout
//import akka.pattern.ask
//import org.scalatest.concurrent.{Eventually, ScalaFutures}
//import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
//import reactive2.FSM._
//
//import scala.concurrent.duration._
//
//class CheckoutTest
//  extends TestKit(ActorSystem("CheckoutTest"))
//    with WordSpecLike
//    with BeforeAndAfterAll
//    with ScalaFutures
//    with Matchers
//    with Eventually {
//
//  implicit val timeout: Timeout = 1.second
//
//  "A checkoutFSM" must {
//    "have a proper reactions" in {
//
//      def sendMessageAndValidateState(
//                                       checkoutFSM: TestFSMRef[CheckoutState, CheckoutData, CheckoutFSM],
//                                       message: Any,
//                                       expectedState: CheckoutState
//                                     ): Unit = {
//        checkoutFSM ! message
//        checkoutFSM.stateName shouldBe expectedState
//      }
//
//      val orderManager = TestFSMRef[OrderManagerState, OrderManagerData, OrderManager](new OrderManager())
//      val cartFSM = TestFSMRef[CartState, CartData, CartFSM](new CartFSM())
//
//      val checkoutFSM = TestFSMRef[CheckoutState, CheckoutData, CheckoutFSM](new CheckoutFSM(orderManager, cartFSM, Cart(Map())))
//      checkoutFSM.stateName shouldBe Init
//
//      sendMessageAndValidateState(checkoutFSM, CheckoutStarted(null), SelectingDelivery)
//
//      sendMessageAndValidateState(checkoutFSM, SelectDeliveryMethod("letter"), SelectingPaymentMethod)
//
//      sendMessageAndValidateState(checkoutFSM, SelectPaymentMethod("cart"), ProcessingPayment)
//
//      checkoutFSM ! PaymentReceive
//
//      intercept[Exception] {
//        implicit val timeout: Timeout = Timeout(5 seconds)
//        (checkoutFSM ? "hello").futureValue
//      }
//    }
//
//  }
//}