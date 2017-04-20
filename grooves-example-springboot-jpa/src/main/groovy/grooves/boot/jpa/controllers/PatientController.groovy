package grooves.boot.jpa.controllers

import grooves.boot.jpa.domain.Patient
import grooves.boot.jpa.domain.PatientAccount
import grooves.boot.jpa.domain.PatientHealth
import grooves.boot.jpa.queries.PatientAccountQuery
import grooves.boot.jpa.queries.PatientHealthQuery
import grooves.boot.jpa.repositories.PatientRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

import javax.persistence.EntityManager

@RestController
class PatientController {

    @Autowired PatientRepository patientRepository
    @Autowired EntityManager entityManager
    @Autowired PatientAccountQuery patientAccountQuery
    @Autowired PatientHealthQuery patientHealthQuery

    @GetMapping("/patient.json")
    List<Patient> patient() {
        patientRepository.findAll()
    }

    @GetMapping("/patient/show/{id}.json")
    Patient patient(@PathVariable Long id) {
        patientRepository.getOne(id)
    }

    @GetMapping("/patient/account/{id}.json")
    ResponseEntity<?>  account(
            @PathVariable Long id,
            @RequestParam(required = false) Long version,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        def patient = patientRepository.getOne(id)
        def computation = version ?
                patientAccountQuery.computeSnapshot(patient, version) :
                date ?
                        patientAccountQuery.computeSnapshot(patient, date) :
                        patientAccountQuery.computeSnapshot(patient, Long.MAX_VALUE)

        def resp = computation.toBlocking().first()
        if (!resp) {
            throw new RuntimeException('Could not compute')
        }
        if (resp.deprecatedBy) {
            ResponseEntity.status(HttpStatus.FOUND).
                    header('Location',
                            ServletUriComponentsBuilder.fromCurrentContextPath().
                                    path("/patient/account/${resp.deprecatedBy.id}.json").
                                    build().toString()).
                    build()
        } else {
            ResponseEntity.ok(resp)
        }

    }

    @GetMapping("/patient/health/{id}.json")
    ResponseEntity<?> health(
            @PathVariable Long id,
            @RequestParam(required = false) Long version,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        def patient = patientRepository.getOne(id)
        def computation = version ?
                patientHealthQuery.computeSnapshot(patient, version) :
                date ?
                        patientHealthQuery.computeSnapshot(patient, date) :
                        patientHealthQuery.computeSnapshot(patient, Long.MAX_VALUE)

        def resp = computation.toBlocking().first()
        if (!resp) {
            throw new RuntimeException('Could not compute')
        }
        if (resp.deprecatedBy) {
            ResponseEntity.status(HttpStatus.FOUND).
                    header('Location',
                            ServletUriComponentsBuilder.fromCurrentContextPath().
                                    path("/patient/health/${resp.deprecatedBy.id}.json").
                                    build().toString()).
                    build()
        } else {
            ResponseEntity.ok(resp)
        }
    }
}
