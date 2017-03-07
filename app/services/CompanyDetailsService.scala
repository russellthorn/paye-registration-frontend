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

import connectors._
import enums.{CacheKeys, DownstreamOutcome}
import models.api.{CompanyDetails => CompanyDetailsAPI}
import models.view.{CompanyDetails => CompanyDetailsView, TradingName => TradingNameView}
import models.{Address, DigitalContactDetails}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CompanyDetailsService @Inject()(injKeystoreConnector: KeystoreConnector,
                                      injPAYERegistrationConnector: PAYERegistrationConnector,
                                      injCoHoAPIService: CoHoAPIService,
                                      injS4LService: S4LService,
                                      injCompRegConnector : CompanyRegistrationConnector,
                                      injCohoAPIConnector: CoHoAPIConnector) extends CompanyDetailsSrv {

  override val keystoreConnector = injKeystoreConnector
  override val payeRegConnector = injPAYERegistrationConnector
  override val compRegConnector = injCompRegConnector
  override val cohoAPIConnector = injCohoAPIConnector
  override val cohoService = injCoHoAPIService
  override val s4LService = injS4LService
}

trait CompanyDetailsSrv extends CommonService {

  val payeRegConnector: PAYERegistrationConnect
  val compRegConnector: CompanyRegistrationConnect
  val cohoAPIConnector: CoHoAPIConnect
  val cohoService: CoHoAPISrv
  val s4LService: S4LSrv

  def getCompanyDetails(implicit hc: HeaderCarrier): Future[CompanyDetailsView] = {
    s4LService.fetchAndGet[CompanyDetailsView](CacheKeys.CompanyDetails.toString) flatMap {
      case Some(companyDetails) => Future.successful(companyDetails)
      case None => for {
        regID    <- fetchRegistrationID
        oDetails <- payeRegConnector.getCompanyDetails(regID)
        details  <- convertOrCreateCompanyDetailsView(oDetails)
        viewDetails <- saveToS4L(details)
      } yield viewDetails
    }
  }

  private[services] def convertOrCreateCompanyDetailsView(oAPI: Option[CompanyDetailsAPI])(implicit hc: HeaderCarrier): Future[CompanyDetailsView] = {
    oAPI match {
      case Some(detailsAPI) => Future.successful(apiToView(detailsAPI))
      case None => for {
        cName   <- cohoService.getStoredCompanyName
        roAddress <- retrieveRegisteredOfficeAddress
      } yield CompanyDetailsView(None, cName, None, roAddress, None, None)
    }
  }

  private def saveToS4L(viewData: CompanyDetailsView)(implicit hc: HeaderCarrier): Future[CompanyDetailsView] = {
    s4LService.saveForm[CompanyDetailsView](CacheKeys.CompanyDetails.toString, viewData).map(_ => viewData)
  }

  private[services] def saveCompanyDetails(viewModel: CompanyDetailsView)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] = {
    viewToAPI(viewModel) fold(
      incompleteView =>
        saveToS4L(incompleteView) map {_ => DownstreamOutcome.Success},
      completeAPI =>
        for {
          regID     <- fetchRegistrationID
          details   <- payeRegConnector.upsertCompanyDetails(regID, completeAPI)
          clearData <- s4LService.clear
        } yield DownstreamOutcome.Success
      )
  }

  def submitTradingName(tradingNameView: TradingNameView)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] = {
    for {
      details <- getCompanyDetails
      outcome <- saveCompanyDetails(details.copy(tradingName = Some(tradingNameView)))
    } yield outcome
  }

  private[services] def retrieveRegisteredOfficeAddress(implicit hc : HeaderCarrier): Future[Address] = {
    for {
      regId <- fetchRegistrationID
      tID <- compRegConnector.getTransactionId(regId)
      address <- cohoAPIConnector.getRegisteredOfficeAddress(tID)
    } yield {
      address
    }
  }

  def getPPOBPageAddresses(companyDetailsView: CompanyDetailsView): (Map[String, Address]) = {
    companyDetailsView.ppobAddress.map {
      case companyDetailsView.roAddress => Map("ppob" -> companyDetailsView.roAddress)
      case addr: Address => Map("ro" -> companyDetailsView.roAddress, "ppob" -> addr)
      }.getOrElse(Map("ro" -> companyDetailsView.roAddress))
  }

  def copyROAddrToPPOBAddr()(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] = {
    for {
      details <- getCompanyDetails
      outcome <- saveCompanyDetails(details.copy(ppobAddress = Some(details.roAddress)))
    } yield outcome
  }

  def submitPPOBAddr(ppobAddr: Address)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] = {
    for {
      details <- getCompanyDetails
      outcome <- saveCompanyDetails(details.copy(ppobAddress = Some(ppobAddr)))
    } yield outcome
  }

  def submitBusinessContact(businessContact: DigitalContactDetails)(implicit hc: HeaderCarrier): Future[DownstreamOutcome.Value] = {
    for {
      details <- getCompanyDetails
      outcome <- saveCompanyDetails(details.copy(businessContactDetails = Some(businessContact)))
    } yield outcome
  }

  private[services] def apiToView(apiModel: CompanyDetailsAPI): CompanyDetailsView = {
    val tradingNameView = apiModel.tradingName.map {
      tName => Some(TradingNameView(differentName = true, Some(tName)))
    }.getOrElse {
      Some(TradingNameView(differentName = false, None))
    }

    CompanyDetailsView(
      apiModel.crn,
      apiModel.companyName,
      tradingNameView,
      apiModel.roAddress,
      Some(apiModel.ppobAddress),
      Some(apiModel.businessContactDetails)
    )
  }

  private[services] def viewToAPI(viewData: CompanyDetailsView): Either[CompanyDetailsView, CompanyDetailsAPI] = viewData match {
    case CompanyDetailsView(crn, companyName, Some(tradingName), roAddress, Some(ppobAddress), Some(businessContactDetails)) =>
      Right(CompanyDetailsAPI(crn, companyName, tradingNameAPIValue(tradingName), roAddress, ppobAddress, businessContactDetails))
    case _ => Left(viewData)
  }

  private def tradingNameAPIValue(tradingNameView: TradingNameView): Option[String] = {
    if (tradingNameView.differentName) tradingNameView.tradingName else None
  }
}
