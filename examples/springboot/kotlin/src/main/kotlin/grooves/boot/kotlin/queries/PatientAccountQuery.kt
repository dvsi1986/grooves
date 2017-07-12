package grooves.boot.kotlin.queries

import com.github.rahulsom.grooves.api.EventApplyOutcome.CONTINUE
import com.github.rahulsom.grooves.queries.QuerySupport
import com.github.rahulsom.grooves.queries.internal.SimpleExecutor
import com.github.rahulsom.grooves.queries.internal.SimpleQuery
import grooves.boot.kotlin.domain.Patient
import grooves.boot.kotlin.domain.PatientAccount
import grooves.boot.kotlin.domain.PatientEvent
import grooves.boot.kotlin.repositories.PatientAccountRepository
import grooves.boot.kotlin.repositories.PatientEventRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable
import rx.Observable.just
import java.lang.Exception
import java.util.*

@Component
//tag::documented[]
class PatientAccountQuery :
        QuerySupport<String, Patient, String, PatientEvent, String, PatientAccount,
                PatientAccountQuery>, // <1>
        SimpleQuery<String, Patient, String, PatientEvent, PatientEvent.Applicable, String,
                PatientAccount, PatientAccountQuery> { // <2>

    override fun getExecutor() = SimpleExecutor<String, Patient, String, PatientEvent,
            PatientEvent.Applicable, String, PatientAccount, PatientAccountQuery>() // <3>

    @Autowired lateinit var patientEventRepository: PatientEventRepository
    @Autowired lateinit var patientAccountRepository: PatientAccountRepository

    override fun createEmptySnapshot() = PatientAccount() // <4>

    override fun getSnapshot( // <5>
            maxPosition: Long, aggregate: Patient): Observable<PatientAccount> =
            patientAccountRepository.findByAggregateIdAndLastEventPositionLessThan(
                    aggregate.id!!, maxPosition)

    override fun getSnapshot( // <6>
            maxTimestamp: Date, aggregate: Patient): Observable<PatientAccount> =
            patientAccountRepository.findByAggregateIdAndLastEventTimestampLessThan(
                    aggregate.id!!, maxTimestamp)

    override fun shouldEventsBeApplied(snapshot: PatientAccount?) = true // <7>

    override fun findEventsForAggregates( // <8>
            aggregates: MutableList<Patient>): Observable<PatientEvent> =
            patientEventRepository.findAllByAggregateIdIn(aggregates.map { it.id!! })

    override fun addToDeprecates(
            snapshot: PatientAccount, deprecatedAggregate: Patient) {
        snapshot.deprecatesIds.add(deprecatedAggregate.id!!)
    }

    override fun onException( // <9>
            e: Exception, snapshot: PatientAccount, event: PatientEvent) =
            just(CONTINUE)

    override fun getUncomputedEvents( // <10>
            aggregate: Patient, lastSnapshot: PatientAccount,
            version: Long): Observable<PatientEvent> {
        return patientEventRepository.
                findAllByPositionRange(
                        aggregate.id!!, lastSnapshot.lastEventPosition ?: 0, version)
    }

    override fun getUncomputedEvents( // <11>
            aggregate: Patient, lastSnapshot: PatientAccount,
            snapshotTime: Date): Observable<PatientEvent> {
        return lastSnapshot.lastEventTimestamp?.
                let {
                    patientEventRepository.findAllByTimestampRange(
                            aggregate.id!!, it, snapshotTime)
                } ?:
                patientEventRepository.
                        findAllByAggregateIdAndTimestampLessThan(
                                aggregate.id!!, snapshotTime)
    }

    override fun applyEvent(event: PatientEvent.Applicable, snapshot: PatientAccount) =
            when (event) { // <12>
                is PatientEvent.Applicable.Created -> {
                    snapshot.name = snapshot.name ?: event.name
                    just(CONTINUE)
                }
                is PatientEvent.Applicable.ProcedurePerformed -> {
                    snapshot.balance = snapshot.balance.add(event.cost)
                    just(CONTINUE)
                }
                is PatientEvent.Applicable.PaymentMade -> {
                    snapshot.balance = snapshot.balance.subtract(event.amount)
                    snapshot.moneyMade = snapshot.moneyMade.add(event.amount)
                    just(CONTINUE)
                }
            }
}
//end::documented[]