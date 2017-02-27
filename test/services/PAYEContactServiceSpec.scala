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

package services

import connectors.{CoHoAPIConnector, CompanyRegistrationConnector, PAYERegistrationConnector}
import enums.DownstreamOutcome
import fixtures.PAYERegistrationFixture
import org.mockito.Matchers
import org.mockito.Mockito.when
import testHelpers.PAYERegSpec
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, Upstream4xxResponse}

import scala.concurrent.Future

class PAYEContactServiceSpec extends PAYERegSpec with PAYERegistrationFixture {
  implicit val hc = HeaderCarrier()

  val mockPAYERegConnector = mock[PAYERegistrationConnector]
  val mockCompRegConnector = mock[CompanyRegistrationConnector]
  val mockCohoAPIConnector = mock[CoHoAPIConnector]
  val mockCoHoService = mock[CoHoAPIService]
  val mockS4LService = mock[S4LService]

  val returnHttpResponse = HttpResponse(200)

  class Setup {
    val service = new PAYEContactService (mockKeystoreConnector, mockPAYERegConnector, mockCoHoService, mockS4LService, mockCompRegConnector, mockCohoAPIConnector)
  }

  "Calling getPAYEContact" should {
    "return the correct View response when PAYE Contact are returned from the connector" in new Setup {
      mockFetchRegID("54321")
      when(mockPAYERegConnector.getPAYEContact(Matchers.contains("54321"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(validPAYEContactDetails)))

      await(service.getPAYEContact) shouldBe Some(validPAYEContactDetails)
    }

    "return None when no PAYE Contact are returned from the connector" in new Setup {
      mockFetchRegID("54321")
      when(mockPAYERegConnector.getPAYEContact(Matchers.contains("54321"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(None))

      await(service.getPAYEContact) shouldBe None
    }

    "throw an Upstream4xxResponse when a 403 response is returned from the connector" in new Setup {
      mockFetchRegID("54321")
      when(mockPAYERegConnector.getPAYEContact(Matchers.contains("54321"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("403", 403, 403)))

      an[Upstream4xxResponse] shouldBe thrownBy(await(service.getPAYEContact))
    }

    "throw an Exception when `an unexpected response is returned from the connector" in new Setup {
      mockFetchRegID("54321")
      when(mockPAYERegConnector.getPAYEContact(Matchers.contains("54321"))(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new ArrayIndexOutOfBoundsException))

      an[Exception] shouldBe thrownBy(await(service.getPAYEContact))
    }

  }

  "Calling submitPAYEContact" should {
    "return a success response when the upsert completes successfully" in new Setup {
      mockFetchRegID("54321")
      when(mockPAYERegConnector.upsertPAYEContact(Matchers.contains("54321"), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validPAYEContactDetails))

      await(service.submitPAYEContact(validPAYEContactDetails)) shouldBe DownstreamOutcome.Success
    }
  }
}
