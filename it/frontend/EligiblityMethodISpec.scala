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

import itutil.{CachingStub, IntegrationSpecBase, LoginStub, WiremockHelper}
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames
import play.api.test.FakeApplication


class EligiblityMethodISpec extends IntegrationSpecBase
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
    "microservice.services.cachable.short-lived-cache.host" -> s"$mockHost",
    "microservice.services.cachable.short-lived-cache.port" -> s"$mockPort",
    "microservice.services.cachable.short-lived-cache.domain" -> "save4later",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.paye-registration.host" -> s"$mockHost",
    "microservice.services.paye-registration.port" -> s"$mockPort",
    "microservice.services.company-registration-frontend.www.url" -> s"$mockHost",
    "microservice.services.company-registration-frontend.www.uri" -> "/test-uri",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort"
  ))

  override def beforeEach() {
    resetWiremock()
  }

  val regId = "3"

  "GET Company Eligibility" should {

    "Return a populated page if PayeReg returns a company eligibility response" in {
      setupSimpleAuthMocks()

      stubSuccessfulLogin()

      stubPayeRegDocumentStatus(regId)

      stubKeystoreMetadata(SessionId, regId)

      stubGet(s"/save4later/paye-registration-frontend/$regId", 404, "")

      val eligibility =
        """
          |{
          | "companyEligibility" : false,
          | "directorEligibility" : false
          |}
        """.stripMargin

      stubGet(s"/paye-registration/$regId/eligibility", 200, eligibility)
      val dummyS4LResponse = s"""{"id":"xxx", "data": {} }"""
      stubPut(s"/save4later/paye-registration-frontend/$regId/data/CompanyDetails", 200, dummyS4LResponse)

      val fResponse = buildClient("/offshore-employer").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
        get()

      val response = await(fResponse)

      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val document = Jsoup.parse(response.body)
      document.title() shouldBe "Is the company an offshore employer outside the European Economic Area that doesn't pay UK National Insurance?"
      document.getElementById("isEligible-true").attr("checked") shouldBe ""
      document.getElementById("isEligible-false").attr("checked") shouldBe "checked"
    }
  }

  "GET Director Eligibility" should {

    "Return a populated page if PayeReg returns a company eligibility response" in {
      setupSimpleAuthMocks()

      stubSuccessfulLogin()

      stubPayeRegDocumentStatus(regId)

      stubKeystoreMetadata(SessionId, regId)

      stubGet(s"/save4later/paye-registration-frontend/$regId", 404, "")

      val eligibility =
        """
          |{
          | "companyEligibility" : false,
          | "directorEligibility" : false
          |}
        """.stripMargin

      stubGet(s"/paye-registration/$regId/eligibility", 200, eligibility)
      val dummyS4LResponse = s"""{"id":"xxx", "data": {} }"""
      stubPut(s"/save4later/paye-registration-frontend/$regId/data/DirectorDetails", 200, dummyS4LResponse)

      val fResponse = buildClient("/provide-non-cash-awards").
        withHeaders(HeaderNames.COOKIE -> getSessionCookie()).
        get()

      val response = await(fResponse)

      response.status shouldBe 200
      val mdtpCookieData = getCookieData(response.cookie("mdtp").get)
      mdtpCookieData("csrfToken") shouldNot be("")
      mdtpCookieData("sessionId") shouldBe SessionId
      mdtpCookieData("userId") shouldBe userId

      val document = Jsoup.parse(response.body)
      document.title() shouldBe "Does the company provide non-cash awards to employees and pay any tax due in a Taxed Award Scheme?"
      document.getElementById("isEligible-true").attr("checked") shouldBe ""
      document.getElementById("isEligible-false").attr("checked") shouldBe "checked"
    }
  }
}
