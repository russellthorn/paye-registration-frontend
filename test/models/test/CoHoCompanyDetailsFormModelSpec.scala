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

package models.test

import models.external.{AreaOfIndustry, CoHoCompanyDetailsModel}
import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class CoHoCompanyDetailsFormModelSpec extends UnitSpec {
  "CoHoCompanyDetailsFormModel" should {

    val tstJson = Json.parse(
      s"""{
         |  "companyName":"TESTLTD",
         |  "sicCodes":[
         |    "150",
         |    "155",
         |    "163"
         |  ],
         |  "descriptions":[
         |    "consulting",
         |    "laundring",
         |    "cleaning"
         |  ]
         |}""".stripMargin)

    val tstModel = CoHoCompanyDetailsFormModel(
      companyName = "TESTLTD",
      sicCodes = List("150", "155", "163"),
      descriptions = List("consulting", "laundring", "cleaning")
    )

    "read from json with full data" in {
      Json.fromJson[CoHoCompanyDetailsFormModel](tstJson) shouldBe JsSuccess(tstModel)
    }
    "write to json with full data" in {
      Json.toJson[CoHoCompanyDetailsFormModel](tstModel) shouldBe tstJson
    }
  }

  "Calling toCoHoCompanyDetailsAPIModel" should {
    "return a valid CoHoCompanyDetailsFormModel" in {
      val tstModel = CoHoCompanyDetailsFormModel(
        companyName = "TESTLTD",
        sicCodes = List("150", "", "155", "163"),
        descriptions = List("consulting", "laundring", "cleaning")
      )

      val expectResult = CoHoCompanyDetailsModel("1234", "TESTLTD", List(
        AreaOfIndustry("150", "consulting"),
        AreaOfIndustry("155", "laundring"),
        AreaOfIndustry("163", "cleaning")
      ))

      tstModel.toCoHoCompanyDetailsAPIModel("1234") shouldBe expectResult
    }

    "return an exception if the list of sicCodes and descriptions are not matching" in {
      val tstModel = CoHoCompanyDetailsFormModel(
        companyName = "TESTLTD",
        sicCodes = List("150", "", "155", "163"),
        descriptions = List("consulting", "laundring", "cleaning", "failing")
      )

      intercept[Exception](tstModel.toCoHoCompanyDetailsAPIModel("1234"))
    }
  }
}
