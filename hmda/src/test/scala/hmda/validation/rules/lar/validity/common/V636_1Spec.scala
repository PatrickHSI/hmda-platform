package hmda.validation.rules.lar.validity

import hmda.validation.rules.EditCheck
import hmda.validation.rules.lar.LarEditCheckSpec
import hmda.model.filing.lar.LarGenerators._
import hmda.model.filing.lar._2018.LoanApplicationRegister
import hmda.model.filing.lar.enums.InvalidRaceObservedCode

class V636_1Spec extends LarEditCheckSpec {
  override def check: EditCheck[LoanApplicationRegister] = V636_1

  property("Applicant Observed Race Code Must be Valid") {
    forAll(larGen) { lar =>
      whenever(lar.applicant.race.raceObserved != InvalidRaceObservedCode) {
        lar.mustPass
      }
      lar
        .copy(
          applicant = lar.applicant.copy(race =
            lar.applicant.race.copy(raceObserved = InvalidRaceObservedCode)))
        .mustFail

    }
  }
}
