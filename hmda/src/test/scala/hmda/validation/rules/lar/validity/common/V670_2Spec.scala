package hmda.validation.rules.lar.validity

import hmda.model.filing.lar.LarGenerators._
import hmda.model.filing.lar._2018.LoanApplicationRegister
import hmda.model.filing.lar.enums._
import hmda.validation.rules.EditCheck
import hmda.validation.rules.lar.LarEditCheckSpec

class V670_2Spec extends LarEditCheckSpec {
  override def check: EditCheck[LoanApplicationRegister] = V670_2

  property("If a denial reason is provided, application should be denied") {
    forAll(larGen) { lar =>
      val unappLar =
        lar.copy(denial = lar.denial.copy(denialReason1 = EmptyDenialValue))
      unappLar.mustPass

      val appLar =
        lar.copy(denial = lar.denial.copy(denialReason1 = DebtToIncomeRatio))
      appLar
        .copy(
          action =
            appLar.action.copy(actionTakenType = InvalidActionTakenTypeCode))
        .mustFail
      appLar
        .copy(action = appLar.action.copy(actionTakenType = ApplicationDenied))
        .mustPass
    }
  }
}
