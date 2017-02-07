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

package models.view

import play.api.libs.json.Json

case class CompanyDetails(
                           crn: Option[String],
                           companyName: String,
                           tradingName: Option[TradingName],
                           address: Option[Address]
                           )

case class TradingName (
                         differentName: Boolean,
                         tradingName:Option[String]
                         )

case class Address(
                    line1: String,
                    line2: String,
                    line3: Option[String],
                    line4: Option[String],
                    postCode: Option[String],
                    country: Option[String] = None
                  )

object TradingName {
  implicit val format = Json.format[TradingName]
}

object Address {
  implicit val format = Json.format[Address]
}

object CompanyDetails {
  implicit val format = Json.format[CompanyDetails]
}
