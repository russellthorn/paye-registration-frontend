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

import javax.inject.{Inject, Singleton}

import connectors.{KeystoreConnector, PAYERegistrationConnect, PAYERegistrationConnector}
import enums.{CacheKeys, DownstreamOutcome}
import models.api.{Employment => EmploymentAPI}
import models.view.{CompanyPension, EmployingStaff, Subcontractors, Employment => EmploymentView, FirstPayment => FirstPaymentView}
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.DateUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait SavedResponse
case object S4LSaved extends SavedResponse
case object MongoSaved extends SavedResponse

@Singleton
class EmploymentService @Inject()(keystoreConn: KeystoreConnector, payeRegistrationConn: PAYERegistrationConnector, s4LServ: S4LService) extends EmploymentSrv {
  override val keystoreConnector = keystoreConn
  override val payeRegConnector = payeRegistrationConn
  override val s4LService = s4LServ
}

trait EmploymentSrv extends CommonService with DateUtil {

  val payeRegConnector: PAYERegistrationConnect
  val s4LService: S4LSrv

  implicit val formatRecordSet = Json.format[EmploymentView]

  private[services] def viewToAPI(viewData: EmploymentView): Either[EmploymentView, EmploymentAPI] = viewData match {
    case EmploymentView(Some(EmployingStaff(true)), Some(pension), Some(cis), Some(pay)) =>
      Right(EmploymentAPI(true, Some(pension.pensionProvided), cis.hasContractors, pay.firstPayDate))
    case EmploymentView(Some(EmployingStaff(false)), _, Some(cis), Some(pay)) =>
      Right(EmploymentAPI(false, None, cis.hasContractors, pay.firstPayDate))
    case _ => Left(viewData)
  }

  private[services] def apiToView(apiData: EmploymentAPI): EmploymentView = apiData match {
    case EmploymentAPI(true, Some(pensionProvided), hasContractors, pay) =>
      EmploymentView(Some(EmployingStaff(true)), Some(CompanyPension(pensionProvided)), Some(Subcontractors(hasContractors)), Some(FirstPaymentView(apiData.firstPayDate)))
    case EmploymentAPI(false, _, hasContractors, pay) =>
      EmploymentView(Some(EmployingStaff(false)), None, Some(Subcontractors(hasContractors)), Some(FirstPaymentView(apiData.firstPayDate)))
  }

  def fetchEmploymentView()(implicit hc: HeaderCarrier): Future[EmploymentView] =
    s4LService.fetchAndGet(CacheKeys.Employment.toString) flatMap {
      case Some(employment) => Future.successful(employment)
      case None => for {
        regID <- fetchRegistrationID
        regResponse <- payeRegConnector.getEmployment(regID)
      } yield regResponse match {
        case Some(employment) => apiToView(employment)
        case None => EmploymentView(None, None, None, None)
      }
    }

  def saveEmploymentView(viewData: EmploymentView)(implicit hc: HeaderCarrier): Future[SavedResponse] =
    viewToAPI(viewData) match {
      case Left(view) => s4LService.saveForm[EmploymentView](CacheKeys.Employment.toString, view) map(_ => S4LSaved)
      case Right(api) => for {
        regID <- fetchRegistrationID
        regResponse <- payeRegConnector.upsertEmployment(regID, api)
      } yield MongoSaved
    }

  def saveEmployment(viewData: EmploymentView)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] =
    saveEmploymentView(viewData) flatMap {
      case MongoSaved => s4LService.clear() map (_ => DownstreamOutcome.Success)
      case _ => Future.successful(DownstreamOutcome.Success)
    }

  def saveEmployingStaff(viewData: EmployingStaff)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] =
    fetchEmploymentView() flatMap {
      case employment => {
        saveEmployment(EmploymentView(Some(viewData), employment.companyPension, employment.subcontractors, employment.firstPayment))
      }
    }

  def saveCompanyPension(viewData: CompanyPension)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] =
    fetchEmploymentView() flatMap {
      case employment => {
        saveEmployment(EmploymentView(employment.employing, Some(viewData), employment.subcontractors, employment.firstPayment))
      }
    }

  def saveSubcontractors(viewData: Subcontractors)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] =
    fetchEmploymentView() flatMap {
      case employment => {
        saveEmployment(EmploymentView(employment.employing, employment.companyPension, Some(viewData), employment.firstPayment))
      }
    }

  def saveFirstPayment(viewData: FirstPaymentView)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] =
    fetchEmploymentView() flatMap {
      case employment => {
        saveEmployment(EmploymentView(employment.employing, employment.companyPension, employment.subcontractors, Some(viewData)))
      }
    }
}
