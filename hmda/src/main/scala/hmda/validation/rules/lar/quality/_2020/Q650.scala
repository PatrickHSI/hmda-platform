package hmda.validation.rules.lar.quality._2020

import hmda.model.filing.lar.LoanApplicationRegister
import hmda.validation.dsl.PredicateCommon._
import hmda.validation.dsl.PredicateSyntax._
import hmda.validation.dsl.ValidationResult
import hmda.validation.rules.EditCheck

import scala.util.Try

object Q650 extends EditCheck[LoanApplicationRegister] {
  override def name: String = "Q650"

  override def parent: String = "Q650"

  override def apply(lar: LoanApplicationRegister): ValidationResult ={
    val interest = Try(lar.loan.interestRate.toDouble).getOrElse(-1.0)
    when(interest is greaterThan(0.0)) {
      interest is greaterThanOrEqual(0.5)
    }
  }
}
