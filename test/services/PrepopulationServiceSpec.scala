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

import connectors.BusinessRegistrationConnect
import models.DigitalContactDetails
import models.view.PAYEContactDetails
import common.exceptions.DownstreamExceptions.S4LFetchException
import models.Address
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.Format
import testHelpers.PAYERegSpec
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class PrepopulationServiceSpec extends PAYERegSpec {
  implicit val hc = new HeaderCarrier()
  val mockS4LService = mock[S4LService]

  trait Setup {
    val service = new PrepopulationSrv {
      override val busRegConnector: BusinessRegistrationConnect = mockBusinessRegistrationConnector
      override val s4LService = mockS4LService
    }
  }

  val addr1 = Address(
    line1 = "line 1",
    line2 = "line 2",
    line3 = Some("line 3"),
    line4 = Some("line 4"),
    postCode = Some("TE1 1ST"),
    country = None,
    auditRef = Some("tstAuditRef")
  )

  val addr2 = Address(
    line1 = "line one",
    line2 = "line two",
    line3 = None,
    line4 = None,
    postCode = Some("TE1 2ST"),
    country = None
  )

  val addr3 = Address(
    line1 = "line",
    line2 = "other line",
    line3 = Some("line 3"),
    line4 = Some("line 4"),
    postCode = None,
    country = Some("UK"),
    auditRef = Some("tstAuditRef2")
  )

  val regId = "55555"

  val validDigitalContact = DigitalContactDetails(Some("a@b.c"), Some("123"), Some("321"))
  val validContactDetails = PAYEContactDetails("testName", validDigitalContact)

  "getBusinessContactDetails" should {
    "return optional digital contact details" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveContactDetails(ArgumentMatchers.eq(regId))(ArgumentMatchers.eq(hc), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validContactDetails)))

      await(service.getBusinessContactDetails(regId)) shouldBe Some(validDigitalContact)
    }
    "return no digital contact details" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveContactDetails(ArgumentMatchers.eq(regId))(ArgumentMatchers.eq(hc), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      await(service.getBusinessContactDetails(regId)) shouldBe None
    }
  }

  "getPAYEContactDetails" should {
    "return optional contact details" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveContactDetails(ArgumentMatchers.eq(regId))(ArgumentMatchers.eq(hc), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validContactDetails)))

      await(service.getPAYEContactDetails(regId)) shouldBe Some(validContactDetails)
    }
    "return no contact details" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveContactDetails(ArgumentMatchers.eq(regId))(ArgumentMatchers.eq(hc), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      await(service.getPAYEContactDetails(regId)) shouldBe None
    }
  }

  "saveContactDetails" should {
    "save contact details" in new Setup {
      when(mockBusinessRegistrationConnector.upsertContactDetails(ArgumentMatchers.eq(regId), ArgumentMatchers.any[PAYEContactDetails])(ArgumentMatchers.eq(hc)))
        .thenReturn(Future.successful(validContactDetails))

      await(service.saveContactDetails(regId, validContactDetails)) shouldBe validContactDetails
    }
  }

  "FilterAddresses" should {
    "return an address map when no duplicates and no address is different" in new Setup {
      val addresses = Seq(addr1, addr2)
      val resMap = Map(0 -> addr1, 1 -> addr2)
      service.filterAddresses(addresses, Seq(addr3)) shouldBe resMap
    }

    "filter out duplicates" in new Setup {
      val addresses = Seq(addr1, addr2, addr1)
      val resMap = Map(0 -> addr1, 1 -> addr2)
      service.filterAddresses(addresses, Seq(addr3)) shouldBe resMap
    }

    "filter out address when it is the same as one of the passed addresses" in new Setup {
      val addresses = Seq(addr1, addr2, addr3)
      val resMap = Map(0 -> addr2, 1 -> addr3)
      service.filterAddresses(addresses, Seq(addr1)) shouldBe resMap
    }

    "filter out multiple addresses when it is the same as one of the passed addresses" in new Setup {
      val addresses = Seq(addr1, addr2, addr3)
      val resMap = Map(0 -> addr2)
      service.filterAddresses(addresses, Seq(addr1, addr3)) shouldBe resMap
    }

    "handle an empty list" in new Setup {
      val addresses = Seq.empty
      val resMap = Map.empty
      service.filterAddresses(addresses, Seq(addr3, addr2)) shouldBe resMap
    }
  }

  "GetAddresses" should {
    "return a list of addresses" in new Setup {
      val regId = "regID"
      when(mockBusinessRegistrationConnector.retrieveAddresses(ArgumentMatchers.contains(regId))(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(addr1, addr2)))
      when(mockS4LService.saveIntMap(ArgumentMatchers.contains("PrePopAddresses"), ArgumentMatchers.any(), ArgumentMatchers.contains(regId))
          (ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[Address]]()))
        .thenReturn(Future.successful(CacheMap("PrePopAddresses", Map.empty)))

      await(service.getPrePopAddresses(regId, addr3, None, None)) shouldBe Map(0 -> addr1, 1 -> addr2)
    }
  }

  "SaveAddress" should {
    "save an address" in new Setup {
      val regId = "12345"
      when(mockBusinessRegistrationConnector.upsertAddress(ArgumentMatchers.contains(regId), ArgumentMatchers.any[Address]())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(addr1))

      await(service.saveAddress(regId, addr1)) shouldBe addr1
    }
  }

  "GetAddress" should {
    "fetch an address by ID" in new Setup {
      val regId = "9999"
      when(mockS4LService.fetchAndGetIntMap(ArgumentMatchers.contains("PrePopAddresses"), ArgumentMatchers.contains(regId))
          (ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[Address]]()))
        .thenReturn(Future.successful(Some(Map(0 -> addr1, 1 -> addr2, 2 -> addr3))))

      await(service.getAddress(regId, 1)) shouldBe addr2
    }
    "throw an exception when no addresses are returned from S4L" in new Setup {
      val regId = "9999"
      when(mockS4LService.fetchAndGetIntMap(ArgumentMatchers.contains("PrePopAddresses"), ArgumentMatchers.contains(regId))
      (ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[Address]]()))
        .thenReturn(Future.successful(None))

      intercept[S4LFetchException](await(service.getAddress(regId, 1)))
    }
    "throw an exception when there is no address corresponding to the passed key returned from S4L" in new Setup {
      val regId = "9999"
      when(mockS4LService.fetchAndGetIntMap(ArgumentMatchers.contains("PrePopAddresses"), ArgumentMatchers.contains(regId))
      (ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[Format[Address]]()))
        .thenReturn(Future.successful(Some(Map(0 -> addr1, 1 -> addr2))))

      intercept[S4LFetchException](await(service.getAddress(regId, 2)))
    }
  }
}
