package grooves.example.javaee.queries;

import com.github.rahulsom.grooves.api.EventApplyOutcome;
import com.github.rahulsom.grooves.api.snapshots.Snapshot;
import com.github.rahulsom.grooves.queries.QuerySupport;
import com.github.rahulsom.grooves.queries.internal.Pair;
import grooves.example.javaee.Database;
import grooves.example.javaee.domain.Patient;
import grooves.example.javaee.domain.PatientEvent;
import org.apache.commons.lang3.SerializationUtils;
import org.reactivestreams.Publisher;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.rahulsom.grooves.api.EventApplyOutcome.CONTINUE;
import static grooves.example.javaee.Database.isTimestampInRange;
import static java.util.stream.Collectors.toList;
import static rx.Observable.from;
import static rx.Observable.just;
import static rx.RxReactiveStreams.toObservable;
import static rx.RxReactiveStreams.toPublisher;

// tag::documented[]
public interface CustomQuerySupport<
        SnapshotT extends Snapshot<Patient, Long, Long, PatientEvent> & Serializable // <1>
        > extends QuerySupport<Patient, Long, PatientEvent, Long, SnapshotT> { // <2>

    // end::documented[]
    Database getDatabase();

    Class<SnapshotT> getSnapshotClass();

    // tag::documented[]
    @Override
    default Publisher<SnapshotT> getSnapshot(long maxPosition, Patient aggregate) {
        // <3>
        // end::documented[]
        final Stream<SnapshotT> stream = getDatabase().snapshots(getSnapshotClass());
        return toPublisher(from(stream::iterator)
                .flatMap(it -> just(it).zipWith(
                        toObservable(it.getAggregateObservable()), Pair::new))
                .filter(it -> it.getSecond().equals(aggregate)
                        && it.getFirst().getLastEventPosition() < maxPosition)
                .map(Pair::getFirst)
                .sorted((x, y) -> (int) (x.getLastEventPosition() - y.getLastEventPosition()))
                .takeFirst(it -> true)
                .filter(Objects::nonNull)
                .map(this::copy));
        // tag::documented[]
    }

    @Override
    default Publisher<SnapshotT> getSnapshot(Date maxTimestamp, Patient aggregate) {
        // <4>
        // end::documented[]
        final Stream<SnapshotT> stream = getDatabase().snapshots(getSnapshotClass());
        return toPublisher(from(stream::iterator)
                .flatMap(it -> just(it).zipWith(
                        toObservable(it.getAggregateObservable()), Pair::new))
                .filter(it -> it.getSecond().equals(aggregate)
                        && it.getFirst().getLastEventTimestamp().compareTo(maxTimestamp) < 1)
                .map(Pair::getFirst)
                .sorted((x, y) -> (int) (x.getLastEventPosition() - y.getLastEventPosition()))
                .takeFirst(it -> true)
                .filter(Objects::nonNull)
                .map(this::copy));
        // tag::documented[]
    }
    // end::documented[]

    default SnapshotT copy(SnapshotT it) {
        return SerializationUtils.clone(it);
    }

    // tag::documented[]
    @Override
    default boolean shouldEventsBeApplied(SnapshotT snapshot) { // <5>
        return true;
    }

    @Override
    default Publisher<EventApplyOutcome> onException(
            Exception e, SnapshotT snapshot, PatientEvent event) { // <6>
        getLog().error("Error computing snapshot", e);
        return toPublisher(just(CONTINUE));
        // tag::documented[]
    }

    @Override
    default Publisher<PatientEvent> getUncomputedEvents(
            Patient aggregate, SnapshotT lastSnapshot, long version) {
        // <7>
        // end::documented[]
        Predicate<PatientEvent> patientEventPredicate = x -> {
            Long eventPosition = x.getPosition();
            Long snapshotPosition = 0L;
            if (lastSnapshot != null) {
                snapshotPosition = lastSnapshot.getLastEventPosition();
            }
            return Objects.equals(x.getAggregate(), aggregate)
                    && (snapshotPosition == null || eventPosition > snapshotPosition)
                    && eventPosition <= version;
        };
        final List<PatientEvent> patientEvents = getDatabase().events()
                .filter(patientEventPredicate)
                .collect(toList());
        return toPublisher(from(patientEvents));
        // tag::documented[]
    }

    @Override
    default Publisher<PatientEvent> getUncomputedEvents(
            Patient aggregate, SnapshotT lastSnapshot, Date snapshotTime) {
        // <8>
        // end::documented[]
        Predicate<PatientEvent> patientEventPredicate = it -> aggregate.equals(it.getAggregate())
                && isTimestampInRange(
                lastSnapshot.getLastEventTimestamp(), it.getTimestamp(), snapshotTime);
        final List<PatientEvent> patientEvents = getDatabase().events()
                .filter(patientEventPredicate)
                .collect(toList());
        return toPublisher(from(patientEvents));
        // tag::documented[]
    }

}
// end::documented[]
