package org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.estimators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCmdpModeParameters;
import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikePt;
import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikeSharing;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.core.population.PopulationUtils;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

public class TestSwissPubliBikePtUtilityEstimator {
	private final List<List<? extends PlanElement>> ridePart = new ArrayList<>();
	private final List<List<? extends PlanElement>> ptPart = new ArrayList<>();
	private final List<DiscreteModeChoiceTrip> estimatedTrips = new ArrayList<>();

	private final SwissCmdpModeParameters parameters = SwissCmdpModeParameters.buildDefault();
	private final SwissPubliBikePtUtilityEstimator estimator = new SwissPubliBikePtUtilityEstimator(
			recording(ridePart, 2.0), recording(ptPart, 5.0), parameters);

	private UtilityEstimator recording(List<List<? extends PlanElement>> parts, double utility) {
		return (person, trip, elements) -> {
			parts.add(elements);
			estimatedTrips.add(trip);
			return utility;
		};
	}

	@Test
	public void testAccessTripIsSplitAtTheTransfer() {
		List<PlanElement> bike = publibikePart();
		List<PlanElement> pt = ptPart();
		List<PlanElement> elements = new ArrayList<>(bike);
		elements.add(transfer(PubliBikePt.ACCESS_MODE));
		elements.addAll(pt);

		DiscreteModeChoiceTrip trip = trip();
		assertEquals(7.0, estimator.estimateUtility(null, trip, elements), 0.0);
		assertEquals(bike, ridePart.get(0));
		assertEquals(pt, ptPart.get(0));

		// the parts see a copy of the trip, so the pt variables cached for the pt-only alternative are not reused
		for (DiscreteModeChoiceTrip estimated : estimatedTrips) {
			assertNotSame(trip, estimated);
			assertSame(trip.getOriginActivity(), estimated.getOriginActivity());
			assertEquals(trip.getDepartureTime(), estimated.getDepartureTime(), 0.0);
		}
	}

	@Test
	public void testCombinedTripsUseTheirOwnConstant() {
		List<PlanElement> elements = new ArrayList<>(publibikePart());
		elements.add(transfer(PubliBikePt.ACCESS_MODE));
		elements.addAll(ptPart());

		parameters.publibike.alpha_u = 3.0; // PubliBike-only constant, not used here
		parameters.publibike.alphaPt_u = -0.5;
		assertEquals(6.5, estimator.estimateUtility(null, trip(), elements), 0.0);
	}

	@Test
	public void testEgressTripIsSplitAtTheTransfer() {
		List<PlanElement> pt = ptPart();
		List<PlanElement> bike = publibikePart();
		List<PlanElement> elements = new ArrayList<>(pt);
		elements.add(transfer(PubliBikePt.EGRESS_MODE));
		elements.addAll(bike);

		assertEquals(7.0, estimator.estimateUtility(null, trip(), elements), 0.0);
		assertEquals(bike, ridePart.get(0));
		assertEquals(pt, ptPart.get(0));
	}

	@Test
	public void testTripWithoutTransferIsInvalid() {
		List<PlanElement> walk = List.of(PopulationUtils.createLeg(TransportMode.walk));
		assertEquals(SwissPubliBikePtUtilityEstimator.INVALID_UTILITY, estimator.estimateUtility(null, trip(), walk),
				0.0);
		assertEquals(0, ridePart.size() + ptPart.size());
	}

	private static List<PlanElement> publibikePart() {
		return List.of(PopulationUtils.createLeg(TransportMode.walk), activity(SharingUtils.PICKUP_ACTIVITY),
				PopulationUtils.createLeg(PubliBikeSharing.MODE), activity(SharingUtils.DROPOFF_ACTIVITY),
				PopulationUtils.createLeg(TransportMode.walk));
	}

	private static List<PlanElement> ptPart() {
		return List.of(PopulationUtils.createLeg(TransportMode.walk), activity("pt interaction"),
				PopulationUtils.createLeg(TransportMode.pt), activity("pt interaction"),
				PopulationUtils.createLeg(TransportMode.walk));
	}

	private static Activity transfer(String mode) {
		return activity(PubliBikePt.interactionActivity(mode));
	}

	private static Activity activity(String type) {
		return PopulationUtils.createActivityFromLinkId(type, Id.createLinkId("link"));
	}

	private static DiscreteModeChoiceTrip trip() {
		Activity origin = PopulationUtils.createActivityFromCoord("home", new Coord(0.0, 0.0));
		Activity destination = PopulationUtils.createActivityFromCoord("work", new Coord(10000.0, 0.0));
		DiscreteModeChoiceTrip trip = new DiscreteModeChoiceTrip(origin, destination, TransportMode.pt, List.of(), 1,
				2, 0, new AttributesImpl());
		trip.setDepartureTime(8.0 * 3600.0);
		return trip;
	}
}
