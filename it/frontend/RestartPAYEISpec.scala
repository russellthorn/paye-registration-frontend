/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package frontend

import itutil.{WiremockHelper, CachingStub, LoginStub, IntegrationSpecBase}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames
import play.api.test.FakeApplication
import play.mvc.Http.Status

class RestartPAYEISpec extends IntegrationSpecBase
                          with LoginStub
                          with CachingStub
                          with BeforeAndAfterEach
                          with WiremockHelper {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  override implicit lazy val app = FakeApplication(additionalConfiguration = Map(
    "play.filters.csrf.header.bypassHeaders.X-Requested-With" -> "*",
    "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.cachable.session-cache.host" -> s"$mockHost",
    "microservice.services.cachable.session-cache.port" -> s"$mockPort",
    "microservice.services.cachable.session-cache.domain" -> "keystore",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.paye-registration.host" -> s"$mockHost",
    "microservice.services.paye-registration.port" -> s"$mockPort",
    "microservice.services.company-registration.host" -> s"$mockHost",
    "microservice.services.company-registration.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  ))

  override def beforeEach() {
    resetWiremock()
  }

  def enableCompanyRegistrationFeature() = buildClient("/test-only/feature-flag/companyRegistration/true").get()

  val regId = "6"
  val txID = "tx1234567"
  val companyName = "Test Company"

  "Restarting a paye registration" should {
    "clear down keystore and redirect to the start of the journey if PAYE registration deletion is successful" when {
      "there is no current profile in Keystore" in {

        setupSimpleAuthMocks()
        stubSuccessfulLogin()
        stubEmptyKeystore(SessionId)
        stubKeystoreDelete(SessionId)

        stubGet("/business-registration/business-tax-registration", 200,
          s"""{
             |  "registrationID": "$regId",
             |  "completionCapacity": "Director",
             |  "language": "EN"
             |}
          """.stripMargin
        )

        stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200,
          s"""{
             |  "status": "Acknowledged",
             |  "confirmationReferences": {
             |    "transaction-id": "tx1234567"
             |  }
             |}
          """.stripMargin
        )

        stubFor(delete(urlMatching(s"/paye-registration/$regId/delete"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
        )

        await(enableCompanyRegistrationFeature())

        val fResponse = buildClient("/re-register-as-an-employer").
          withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
          get()

        val response = await(fResponse)

        response.status shouldBe 303
        response.header("Location") shouldBe Some("/register-for-paye")
        verify(deleteRequestedFor(urlEqualTo(s"/keystore/paye-registration-frontend/$SessionId")))
        verify(getRequestedFor(urlEqualTo(s"/business-registration/business-tax-registration")))
        verify(getRequestedFor(urlEqualTo(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration")))
      }

      "current profile is stored in Keystore" in {

        setupSimpleAuthMocks()
        stubSuccessfulLogin()
        stubKeystoreMetadata(SessionId, regId)
        stubKeystoreDelete(SessionId)

        stubFor(delete(urlMatching(s"/paye-registration/$regId/delete"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
        )

        val fResponse = buildClient("/re-register-as-an-employer").
          withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
          get()

        val response = await(fResponse)

        response.status shouldBe 303
        response.header("Location") shouldBe Some("/register-for-paye")
        verify(deleteRequestedFor(urlEqualTo(s"/keystore/paye-registration-frontend/$SessionId")))
      }
    }

    "redirect to the dashboard" when {
      "deleting the reg document fails due to incorrect PAYE registration status" in {

        setupSimpleAuthMocks()
        stubSuccessfulLogin()
        stubKeystoreMetadata(SessionId, regId)

        stubFor(delete(urlMatching(s"/paye-registration/$regId/delete"))
          .willReturn(
            aResponse()
              .withStatus(Status.PRECONDITION_FAILED)
          )
        )

        val fResponse = buildClient("/re-register-as-an-employer").
          withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
          get()

        val response = await(fResponse)

        response.status shouldBe 303
        response.header("Location") shouldBe Some("/register-for-paye/business-registration-overview")
      }
    }

    "show an error page" when {
      "deleting the reg document fails due to an error in the backend" in {

        setupSimpleAuthMocks()
        stubSuccessfulLogin()
        stubKeystoreMetadata(SessionId, regId)

        stubFor(delete(urlMatching(s"/paye-registration/$regId/delete"))
          .willReturn(
            aResponse()
              .withStatus(Status.INTERNAL_SERVER_ERROR)
          )
        )

        val fResponse = buildClient("/re-register-as-an-employer").
          withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
          get()

        val response = await(fResponse)

        response.status shouldBe 500
      }
    }
  }
}
