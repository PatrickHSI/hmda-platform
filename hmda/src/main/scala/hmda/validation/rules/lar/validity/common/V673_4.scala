package hmda.validation.rules.lar.validity

import hmda.model.filing.lar._2018.LoanApplicationRegister
import hmda.model.filing.lar.enums.PrimarilyBusinessOrCommercialPurpose
import hmda.validation.dsl.PredicateCommon._
import hmda.validation.dsl.PredicateSyntax._
import hmda.validation.dsl.ValidationResult
import hmda.validation.rules.EditCheck

object V673_4 extends EditCheck[LoanApplicationRegister] {
  override def name: String = "V673-4"

  override def parent: String = "V673"

  override def apply(lar: LoanApplicationRegister): ValidationResult = {
    when(
      lar.businessOrCommercialPurpose is equalTo(
        PrimarilyBusinessOrCommercialPurpose)) {
      lar.loanDisclosure.totalPointsAndFees is oneOf("NA", "Exempt")
    }
  }
}
