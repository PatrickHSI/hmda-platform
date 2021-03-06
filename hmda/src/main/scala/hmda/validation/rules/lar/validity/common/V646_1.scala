package hmda.validation.rules.lar.validity

import hmda.model.filing.lar.LoanApplicationRegister
import hmda.model.filing.lar.enums._
import hmda.validation.dsl.PredicateCommon._
import hmda.validation.dsl.PredicateSyntax._
import hmda.validation.dsl.ValidationResult
import hmda.validation.rules.EditCheck

object V646_1 extends EditCheck[LoanApplicationRegister] {
  override def name: String = "V646-1"

  override def parent: String = "V646"

  val validValues = List(Male, Female, SexInformationNotProvided, SexNotApplicable, SexNoCoApplicant, MaleAndFemale)

  override def apply(lar: LoanApplicationRegister): ValidationResult =
    lar.coApplicant.sex.sexEnum is containedIn(validValues)
}
