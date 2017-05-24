package code.api.v1_4_0

import java.text.SimpleDateFormat
import java.util.Date

import code.api.util.{APIUtil, ApiRole}
import code.api.v1_4_0.JSONFactory1_4_0.{AddCustomerMessageJson, CustomerFaceImageJson, CustomerJsonV140, CustomerMessagesJson}
import code.customer.{Customer, MappedCustomerMessage, MockCustomerFaceImage}
import code.model.BankId
import code.usercustomerlinks.UserCustomerLink
import code.api.util.APIUtil.OAuth._
import code.api.v2_0_0.CreateCustomerJson
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import net.liftweb.common.{Empty, Full}

//TODO: API test must be independent of CustomerMessages implementation
class MappedCustomerMessagesTest extends V140ServerSetup with DefaultUsers {
  implicit val format = APIUtil.formats

  //TODO: need better tests
  feature("Customer messages") {
    scenario("Getting messages when none exist") {
      Given("No messages exist")
      MappedCustomerMessage.count() must equal(0)

      When("We get the messages")
      val request = (v1_4Request / "banks" / mockBankId1.value / "customer" / "messages").GET <@ user1
      val response = makeGetRequest(request)

      Then("We must get a 200")
      response.code must equal(200)

      And("We must get no messages")
      val json = response.body.extract[CustomerMessagesJson]
      json.messages.size must equal(0)
    }

    scenario("Adding a message") {
      //first add a customer to send message to
      var request = (v1_4Request / "banks" / mockBankId1.value / "customer").POST <@ user1
      val customerJson = CreateCustomerJson(
                                            user_id = resourceUser1.userId,
                                            customer_number = mockCustomerNumber,
                                            legal_name = "Someone",
                                            mobile_phone_number = "125245",
                                            email = "hello@hullo.com",
                                            face_image = CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate),
                                            date_of_birth = exampleDate,
                                            relationship_status = "Single",
                                            dependants = 1,
                                            dob_of_dependants = List(exampleDate),
                                            highest_education_attained = "Bachelor’s Degree",
                                            employment_status = "Employed",
                                            kyc_status = true,
                                            last_ok_date = exampleDate)

      When("We add all required entitlement")
      Entitlement.entitlement.vend.addEntitlement(mockBankId1.value, resourceUser1.userId, ApiRole.CanCreateCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(mockBankId1.value, resourceUser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      var response = makePostRequest(request, write(customerJson))

      val customer: Box[Customer] = Customer.customerProvider.vend.getCustomerByCustomerNumber(mockCustomerNumber, mockBankId1)
      val customerId = customer match {
        case Full(c) => c.customerId
        case Empty => "Empty"
        case _ => "Failure"
      }

      When("We add a message")
      request = (v1_4Request / "banks" / mockBankId1.value / "customer" / customerId / "messages").POST <@ user1
      val messageJson = AddCustomerMessageJson("some message", "some department", "some person")
      response = makePostRequest(request, write(messageJson))
      Then("We must get a 201")
      response.code must equal(201)

      And("We should get that message when we do a get messages request ")
      val getMessagesRequest = (v1_4Request / "banks" / mockBankId1.value / "customer" / "messages").GET  <@ user1
      val getMessagesResponse = makeGetRequest(getMessagesRequest)
      val json = getMessagesResponse.body.extract[CustomerMessagesJson]
      json.messages.size must equal(1)

      val msg = json.messages(0)
      msg.message must equal(messageJson.message)
      msg.from_department must equal(messageJson.from_department)
      msg.from_person must equal(messageJson.from_person)
      msg.id.nonEmpty must equal(true)
    }
  }


  override def beforeAll(): Unit = {
    super.beforeAll()
    MappedCustomerMessage.bulkDelete_!!()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
    Customer.customerProvider.vend.bulkDeleteCustomers()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    MappedCustomerMessage.bulkDelete_!!()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
    Customer.customerProvider.vend.bulkDeleteCustomers()
  }

}
