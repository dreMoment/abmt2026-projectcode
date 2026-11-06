package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.Collection;
import java.util.List;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.constraints.AbstractTripConstraint;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraint;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.RoutedTripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

/**
 * Removes a sharing option when no station is within reach.
 *
 * The sharing router returns no route when origin or destination has no
 * station within the maximum access distance, or both use the same station.
 * MATSim then replaces the trip with a plain walk leg. Such a trip contains no
 * pickup and is not a real sharing trip.
 */
public class SharingStationConstraint extends AbstractTripConstraint {
	static public final String NAME = "SharingStation";

	@Override
	public boolean validateAfterEstimation(DiscreteModeChoiceTrip trip, TripCandidate candidate,
			List<TripCandidate> previousCandidates) {
		if (!candidate.getMode().equals(PubliBikeSharing.SERVICE_MODE)) {
			return true;
		}

		if (!(candidate instanceof RoutedTripCandidate routed)) {
			throw new IllegalStateException("Sharing candidates must carry their route");
		}

		return routed.getRoutedPlanElements().stream().anyMatch(element -> element instanceof Activity activity
				&& activity.getType().equals(SharingUtils.PICKUP_ACTIVITY));
	}

	static public class Factory implements TripConstraintFactory {
		@Override
		public TripConstraint createConstraint(Person person, List<DiscreteModeChoiceTrip> planTrips,
				Collection<String> availableModes) {
			return new SharingStationConstraint();
		}
	}
}
