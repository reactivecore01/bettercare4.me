/*
 * Copyright (c) 2014 Dufresne Management Consulting LLC.
 */
package com.nickelsoftware.bettercare4me.hedis.hedis2014

import scala.util.Random
import org.joda.time.DateTime
import org.joda.time.Interval
import com.nickelsoftware.bettercare4me.hedis.HEDISRule
import com.nickelsoftware.bettercare4me.hedis.Scorecard
import com.nickelsoftware.bettercare4me.models.Claim
import com.nickelsoftware.bettercare4me.models.LabClaim
import com.nickelsoftware.bettercare4me.models.MedClaim
import com.nickelsoftware.bettercare4me.models.Patient
import com.nickelsoftware.bettercare4me.models.PatientHistory
import com.nickelsoftware.bettercare4me.models.PersistenceLayer
import com.nickelsoftware.bettercare4me.models.Provider
import com.nickelsoftware.bettercare4me.models.RuleConfig
import com.nickelsoftware.bettercare4me.utils.Utils

object CIS_PC {

  val name = "CIS-PC-HEDIS-2014"

  val pcVaccine = "Pneumococcal Conjugate Vaccine"

  /**
   * CPT codes for Pneumococcal Conjugate Vaccine
   */
  val cptA = List("90723", "90740", "90744", "90747", "90748")
  val cptAS = cptA.toSet

  /**
   * HCPCS for Pneumococcal Conjugate Vaccine
   */
  val hcpcsA = List("G0010")
  val hcpcsAS = hcpcsA.toSet
}

/**
 * Pneumococcal Conjugate Vaccine
 *
 * Pneumococcal Conjugate Vaccine indicates whether a child, who turned 2 years old during the measurement year, received four
 * (4) pneumococcal conjugate vaccinations. This excludes children who had a previous adverse reaction to a vaccine, as well as
 * those with a vaccine contraindication such as immunodeficiency syndrome, HIV, lymphoreticular or histiocytic tissue cancer,
 * multiple myeloma, or leukemia.
 *
 * NUMERATOR:
 * Identifies children, who turned 2 years old during the measurement year, and received at least four (4) pneumococcal conjugate
 * vaccinations with different dates of service on or before the child's 2nd birthday. Only evidence of the antigen or vaccine is
 * counted in the numerator.
 *
 */
class CIS_PC_Rule(config: RuleConfig, hedisDate: DateTime) extends CIS_RuleBase(config, hedisDate) {

  val name = CIS_PC.name
  val fullName = "Pneumococcal Conjugate Vaccine"
  val description = "Pneumococcal Conjugate Vaccine indicates whether a child, who turned 2 years old during the measurement year, received four " +
    "(4) pneumococcal conjugate vaccinations. This excludes children who had a previous adverse reaction to a vaccine, as well as " +
    "those with a vaccine contraindication such as immunodeficiency syndrome, HIV, lymphoreticular or histiocytic tissue cancer, " +
    "multiple myeloma, or leukemia."

  import CIS_PC._
  override def generateMeetMeasureClaims(pl: PersistenceLayer, patient: Patient, provider: Provider): List[Claim] = {

    // after 42 days after birth and before 2 years of age
    val days = Utils.daysBetween(patient.dob.plusDays(42), patient.dob.plusMonths(20))
    val dos1 = patient.dob.plusDays(42 + Random.nextInt(days))
    val dos2 = dos1.plusDays(20)
    val dos3 = dos2.plusDays(20)
    val dos4 = dos3.plusDays(20)

    /* 3 hepatitis B vaccinations received on different dates
     * of service (anytime prior to the child's 2nd birthday),
     * or a history of the disease */
    pickOne(List(

      // Possible set: CPT
      () => List(
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos1, dos1, cpt = pickOne(cptA)),
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos2, dos2, cpt = pickOne(cptA)),
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos3, dos3, cpt = pickOne(cptA)),
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos4, dos4, cpt = pickOne(cptA))),

      // Another possible set: HCPCS
      () => List(
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos1, dos1, hcpcs = pickOne(hcpcsA)),
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos2, dos2, hcpcs = pickOne(hcpcsA)),
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos3, dos3, hcpcs = pickOne(hcpcsA)),
        pl.createMedClaim(patient.patientID, patient.firstName, patient.lastName, provider.providerID, provider.firstName, provider.lastName, dos4, dos4, hcpcs = pickOne(hcpcsA)))
        ))()
  }

  override def scorePatientMeetMeasure(scorecard: Scorecard, patient: Patient, ph: PatientHistory): Scorecard = {

    // after 42 days after birth and before 2 years of age
    val measurementInterval = new Interval(patient.dob.plusDays(42), patient.dob.plusMonths(24).plusDays(1))

    def rules = List[(Scorecard) => Scorecard](

      // Check for patient has CPT
      (s: Scorecard) => {
        val claims1 = filterClaims(ph.cpt, cptAS, { claim: MedClaim => measurementInterval.contains(claim.dos) })
        val claims2 = filterClaims(ph.hcpcs, hcpcsAS, { claim: MedClaim => measurementInterval.contains(claim.dos) })
        val claims = List.concat(claims1, claims2)

        // need to have 4 claims with different dates
        if (hasDifferentDates(4, claims)) s.addScore(name, fullName, HEDISRule.meetMeasure, pcVaccine, claims)
        else s
      })

    applyRules(scorecard, rules)
  }
}
