package frontend

import controllers.userJourney.routes
import itutil.{WiremockHelper, CachingStub, LoginStub, IntegrationSpecBase}
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication

class PageVisibilityISpec extends IntegrationSpecBase
with LoginStub
with CachingStub
with BeforeAndAfterEach
with WiremockHelper {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  override implicit lazy val app = FakeApplication(additionalConfiguration = Map(
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.paye-registration.host" -> s"$mockHost",
    "microservice.services.paye-registration.port" -> s"$mockPort",
    "microservice.services.cachable.session-cache.host" -> s"$mockHost",
    "microservice.services.cachable.session-cache.port" -> s"$mockPort",
    "microservice.services.cachable.session-cache.domain" -> "keystore",
    "microservice.services.cachable.short-lived-cache.host" -> s"$mockHost",
    "microservice.services.cachable.short-lived-cache.port" -> s"$mockPort",
    "microservice.services.cachable.short-lived-cache.domain" -> "save4later"
  ))

  override def beforeEach() {
    resetWiremock()
  }

  val regId = "98765"

  def stubCompanyDetailsBackendFetch() = {
    val roDoc = s"""{"line1":"1", "line2":"2", "postCode":"TE1 1ST"}"""
    val payeDoc =
      s"""{
         |"companyName": "TstCompanyName",
         |"roAddress": $roDoc,
         |"ppobAddress": $roDoc,
         |"businessContactDetails": {}
         |}""".stripMargin
    stubGet(s"/paye-registration/$regId/company-details", 200, payeDoc)
  }

  def stubEmptyNatureOfBusiness() = stubGet(s"/paye-registration/$regId/sic-codes", 200, "[]")

  def currentProfileJsonString(regSubmitted: Option[Boolean], regId: String = "12345") = Json.parse(
    s"""{
       | "CurrentProfile": {
       |    "registrationID":"$regId",
       |    "completionCapacity":"Director",
       |    "companyTaxRegistration":{
       |      "status":"acknowledged",
       |      "transactionId":"40-654321"
       |    },
       |    "language":"ENG"${regSubmitted.map(bool => s""", "payeRegistrationSubmitted":$bool """).getOrElse("")}
       |  }
       |}
     """.stripMargin).toString()

  "GET Nature of business" should {

    "Show the page when paye registration has not been submitted" in {
      setupSimpleAuthMocks()

      stubSuccessfulLogin()

      stubKeystoreGet(SessionId, currentProfileJsonString(regSubmitted = Some(false), regId = regId))

      stubGet(s"/save4later/paye-registration-frontend/$regId", 404, "")

      stubEmptyNatureOfBusiness()

      stubCompanyDetailsBackendFetch()

      val dummyS4LResponse = s"""{"id":"xxx", "data": {} }"""
      stubPut(s"/save4later/paye-registration-frontend/$regId/data/CompanyDetails", 200, dummyS4LResponse)

      val fResponse = buildClient("/what-company-does").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
        get()

      val response = await(fResponse)

      response.status shouldBe 200
    }

    "Show the page when payeRegistrationSubmitted is not present in Keystore" in {
      setupSimpleAuthMocks()

      stubSuccessfulLogin()

      stubKeystoreGet(SessionId, currentProfileJsonString(regSubmitted = None, regId = regId))

      stubGet(s"/save4later/paye-registration-frontend/$regId", 404, "")

      stubEmptyNatureOfBusiness()

      stubCompanyDetailsBackendFetch()

      val dummyS4LResponse = s"""{"id":"xxx", "data": {} }"""
      stubPut(s"/save4later/paye-registration-frontend/$regId/data/CompanyDetails", 200, dummyS4LResponse)

      val fResponse = buildClient("/what-company-does").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
        get()

      val response = await(fResponse)

      response.status shouldBe 200
    }

    "Redirect to the dashboard when paye registration has been submitted" in {
      setupSimpleAuthMocks()

      stubSuccessfulLogin()

      stubKeystoreGet(SessionId, currentProfileJsonString(regSubmitted = Some(true), regId = regId))

      stubGet(s"/save4later/paye-registration-frontend/$regId", 404, "")

      stubEmptyNatureOfBusiness()

      stubCompanyDetailsBackendFetch()

      val dummyS4LResponse = s"""{"id":"xxx", "data": {} }"""
      stubPut(s"/save4later/paye-registration-frontend/$regId/data/CompanyDetails", 200, dummyS4LResponse)

      val fResponse = buildClient("/what-company-does").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
        get()

      val response = await(fResponse)

      response.status shouldBe 303
      response.header("Location") shouldBe Some(routes.DashboardController.dashboard().url)
    }
  }

  "GET Acknowledgement screen" should {

    "Show the page when paye registration has been submitted" in {
      setupSimpleAuthMocks()

      stubSuccessfulLogin()

      stubKeystoreGet(SessionId, currentProfileJsonString(regSubmitted = Some(true), regId = regId))

      stubGet(s"/save4later/paye-registration-frontend/$regId", 404, "")

      stubGet(s"/paye-registration/$regId/acknowledgement-reference", 200, "\"ackRef\"")

      val fResponse = buildClient("/application-submitted").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
        get()

      val response = await(fResponse)

      response.status shouldBe 200
    }
  }

}
