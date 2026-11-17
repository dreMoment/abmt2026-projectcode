package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.DefaultRoutedTripCandidate;
import org.matsim.core.population.PopulationUtils;

public class TestSharingStationConstraint {
	private final SharingStationConstraint constraint = new SharingStationConstraint();

	@Test
	public void testPubliBikeNeedsAPickup() {
		assertTrue(isValid(PubliBikeSharing.SERVICE_MODE, ride()));
		assertFalse(isValid(PubliBikeSharing.SERVICE_MODE, walk()));
	}

	@Test
	public void testCombinedModesNeedAPickupAndAVehicleLeg() {
		List<PlanElement> rideAndTrain = new ArrayList<>(ride());
		rideAndTrain.add(PopulationUtils.createActivityFromLinkId(
				PubliBikePt.interactionActivity(PubliBikePt.ACCESS_MODE), Id.createLinkId("link")));
		rideAndTrain.add(PopulationUtils.createLeg(TransportMode.pt));

		for (String mode : PubliBikePt.MODES) {
			assertTrue(isValid(mode, rideAndTrain));
			assertFalse(isValid(mode, ride()));
			assertFalse(isValid(mode, walk()));
		}
	}

	@Test
	public void testOtherModesAreNotChecked() {
		assertTrue(isValid(TransportMode.walk, walk()));
	}

	private boolean isValid(String mode, List<PlanElement> elements) {
		return constraint.validateAfterEstimation(null, new DefaultRoutedTripCandidate(0.0, mode, elements, 0.0),
				List.of());
	}

	private static List<PlanElement> ride() {
		return List.of(PopulationUtils.createLeg(TransportMode.walk),
				PopulationUtils.createActivityFromLinkId(SharingUtils.PICKUP_ACTIVITY, Id.createLinkId("link")),
				PopulationUtils.createLeg(PubliBikeSharing.MODE));
	}

	private static List<PlanElement> walk() {
		return List.of(PopulationUtils.createLeg(TransportMode.walk));
	}
}
