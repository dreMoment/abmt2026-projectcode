package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.Collection;
import java.util.List;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
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
 *
 * The same holds for PubliBike combined with public transport
 * ({@link PubliBikePt}): without a transfer station the router returns no
 * route. A valid combined trip has a pickup and a public transport leg.
 */
public class SharingStationConstraint extends AbstractTripConstraint {
	static public final String NAME = "SharingStation";

	@Override
	public boolean validateAfterEstimation(DiscreteModeChoiceTrip trip, TripCandidate candidate,
			List<TripCandidate> previousCandidates) {
		boolean isCombined = PubliBikePt.MODES.contains(candidate.getMode());

		if (!isCombined && !candidate.getMode().equals(PubliBikeSharing.SERVICE_MODE)) {
			return true;
		}

		if (!(candidate instanceof RoutedTripCandidate routed)) {
			throw new IllegalStateException("Sharing candidates must carry their route");
		}

		List<? extends PlanElement> elements = routed.getRoutedPlanElements();
		return PubliBikePtRoutingModule.hasPickup(elements)
				&& (!isCombined || PubliBikePtRoutingModule.hasTransitLeg(elements));
	}

	static public class Factory implements TripConstraintFactory {
		@Override
		public TripConstraint createConstraint(Person person, List<DiscreteModeChoiceTrip> planTrips,
				Collection<String> availableModes) {
			return new SharingStationConstraint();
		}
	}
}
