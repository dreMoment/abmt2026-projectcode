package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;

public class TestPubliBikePtMainModeIdentifier {
	private final PubliBikePtMainModeIdentifier identifier = new PubliBikePtMainModeIdentifier();

	@Test
	public void testCombinedTripsUseTheirRoutingMode() {
		for (String mode : PubliBikePt.MODES) {
			List<PlanElement> trip = List.of(leg(TransportMode.walk, mode), leg(PubliBikeSharing.MODE, mode),
					PopulationUtils.createActivityFromLinkId(PubliBikePt.interactionActivity(mode), Id.createLinkId("s")),
					leg(TransportMode.pt, mode), leg(TransportMode.walk, mode));

			assertEquals(mode, identifier.identifyMainMode(trip));
		}
	}

	@Test
	public void testOtherTripsKeepTheDefault() {
		String bikeOnly = PubliBikeSharing.SERVICE_MODE;
		assertEquals(PubliBikeSharing.MODE, identifier.identifyMainMode(List.of(leg(TransportMode.walk, bikeOnly),
				leg(PubliBikeSharing.MODE, bikeOnly), leg(TransportMode.walk, bikeOnly))));

		assertEquals(TransportMode.pt, identifier.identifyMainMode(List.of(leg(TransportMode.walk, TransportMode.pt),
				leg(TransportMode.pt, TransportMode.pt), leg(TransportMode.walk, TransportMode.pt))));
	}

	private static Leg leg(String mode, String routingMode) {
		Leg leg = PopulationUtils.createLeg(mode);
		TripStructureUtils.setRoutingMode(leg, routingMode);
		return leg;
	}
}
