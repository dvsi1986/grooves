package grooves.grails.rdbms

import com.github.rahulsom.grooves.api.EventApplyOutcome
import com.github.rahulsom.grooves.grails.GormQuerySupport
import com.github.rahulsom.grooves.groovy.transformations.Query
import grails.compiler.GrailsCompileStatic
import org.reactivestreams.Publisher

import static com.github.rahulsom.grooves.api.EventApplyOutcome.CONTINUE
import static rx.Observable.just
import static rx.RxReactiveStreams.toPublisher

/**
 * Performs a query that shows the health of a patient
 *
 * @author Rahul Somasunderam
 */
@Query(aggregate = Patient, snapshot = PatientHealth)
@GrailsCompileStatic
class PatientHealthQuery implements
        GormQuerySupport<Long, Patient, Long, PatientEvent, Long, PatientHealth> {

    final Class<PatientHealth> snapshotClass = PatientHealth
    final Class<PatientEvent> eventClass = PatientEvent

    @Override
    PatientHealth createEmptySnapshot() { new PatientHealth(deprecates: [], procedures: []) }

    @Override
    boolean shouldEventsBeApplied(PatientHealth snapshot) {
        true
    }

    @Override
    void addToDeprecates(PatientHealth snapshot, Patient deprecatedAggregate) {
        snapshot.addToDeprecates(deprecatedAggregate)
    }

    @Override
    Publisher<EventApplyOutcome> onException(
            Exception e, PatientHealth snapshot, PatientEvent event) {
        // ignore exceptions. Look at the mongo equivalent to see one possible way to
        // handle exceptions
        toPublisher(just(CONTINUE))
    }

    Publisher<EventApplyOutcome> applyPatientCreated(
            PatientCreated event, PatientHealth snapshot) {
        if (snapshot.aggregateId == event.aggregate.id) {
            snapshot.name = event.name
        }
        toPublisher(just(CONTINUE))
    }

    Publisher<EventApplyOutcome> applyProcedurePerformed(
            ProcedurePerformed event, PatientHealth snapshot) {
        snapshot.addToProcedures(code: event.code, date: event.timestamp)
        toPublisher(just(CONTINUE))
    }

    @SuppressWarnings(['UnusedMethodParameter'])
    Publisher<EventApplyOutcome> applyPaymentMade(
            PaymentMade event, PatientHealth snapshot) {
        // Ignore payments
        toPublisher(just(CONTINUE))
    }

    @Override PatientHealth detachSnapshot(PatientHealth snapshot) {
        def retval = new PatientHealth(
                lastEventPosition: snapshot.lastEventPosition,
                lastEventTimestamp: snapshot.lastEventTimestamp,
                deprecatedBy: snapshot.deprecatedBy,
                aggregateId: snapshot.aggregateId,
                name: snapshot.name,
        )
        snapshot.deprecates.each { retval.addToDeprecates it }
        snapshot.procedures.each { retval.addToProcedures(code: it.code, date: it.date) }
        retval
    }
}
