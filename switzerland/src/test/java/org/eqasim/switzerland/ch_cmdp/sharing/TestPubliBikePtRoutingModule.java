package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikePtRoutingModule.Direction;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.Facility;

public class TestPubliBikePtRoutingModule {
	static private final double DEPARTURE_TIME = 8.0 * 3600.0;

	private final PubliBikeTestNetwork test = new PubliBikeTestNetwork();
	private final Link origin = test.link("origin", 0.0, 0.0);
	private final Link destination = test.link("destination", 10000.0, 0.0);
	private final Link near = transferStation("near", 1000.0, 0.0);
	private final Link far = transferStation("far", 2000.0, 0.0);
	private final TimeInterpretation timeInterpretation = TimeInterpretation.create(ConfigUtils.createConfig());

	private Link transferStation(String id, double x, double y) {
		test.stop(id + "_rail", x, y, "rail");
		return test.station(id, x, y);
	}

	@Test
	public void testAccessKeepsTheTransferStationWithTheEarliestArrival() {
		// riding to the far station takes 200 s longer, but its train is 600 s faster
		Map<Link, Double> ptTimes = Map.of(near, 1200.0, far, 600.0);
		PubliBikePtRoutingModule router = router(Direction.ACCESS, publibike(Set.of()), pt(ptTimes, Set.of()));

		List<? extends PlanElement> route = route(router);

		int transfer = transferIndex(route, PubliBikePt.ACCESS_MODE);
		Activity interaction = (Activity) route.get(transfer);
		assertEquals(far.getId(), interaction.getLinkId());
		assertEquals(0.0, interaction.getMaximumDuration().seconds(), 0.0);
		assertTrue(PubliBikePtRoutingModule.hasPickup(route.subList(0, transfer)));
		assertTrue(PubliBikePtRoutingModule.hasTransitLeg(route.subList(transfer + 1, route.size())));
	}

	@Test
	public void testEgressStartsWithPublicTransport() {
		PubliBikeTestNetwork egress = new PubliBikeTestNetwork();
		Link from = egress.link("origin", 0.0, 0.0);
		Link to = egress.link("destination", 10000.0, 0.0);
		egress.stop("rail", 9000.0, 0.0, "rail");
		Link station = egress.station("station", 9000.0, 0.0);

		PubliBikePtRoutingModule router = new PubliBikePtRoutingModule(PubliBikePt.EGRESS_MODE, Direction.EGRESS,
				publibike(Set.of()), pt(Map.of(), Set.of()), egress.transferStations(300.0),
				PopulationUtils.getFactory(), timeInterpretation);

		List<? extends PlanElement> route = router.calcRoute(DefaultRoutingRequest.withoutAttributes(
				new LinkWrapperFacility(from), new LinkWrapperFacility(to), DEPARTURE_TIME, null));

		int transfer = transferIndex(route, PubliBikePt.EGRESS_MODE);
		assertEquals(station.getId(), ((Activity) route.get(transfer)).getLinkId());
		assertTrue(PubliBikePtRoutingModule.hasTransitLeg(route.subList(0, transfer)));
		assertTrue(PubliBikePtRoutingModule.hasPickup(route.subList(transfer + 1, route.size())));
	}

	@Test
	public void testCandidatesWithoutRideOrVehicleLegAreSkipped() {
		Map<Link, Double> ptTimes = Map.of(near, 1200.0, far, 600.0);

		// public transport only walks from the far station
		List<? extends PlanElement> walkOnly = route(
				router(Direction.ACCESS, publibike(Set.of()), pt(ptTimes, Set.of(far))));
		assertEquals(near.getId(), ((Activity) walkOnly.get(transferIndex(walkOnly, PubliBikePt.ACCESS_MODE)))
				.getLinkId());

		// no ride to the far station
		List<? extends PlanElement> noRide = route(
				router(Direction.ACCESS, publibike(Set.of(far)), pt(ptTimes, Set.of())));
		assertEquals(near.getId(),
				((Activity) noRide.get(transferIndex(noRide, PubliBikePt.ACCESS_MODE))).getLinkId());

		// nothing works
		assertNull(route(router(Direction.ACCESS, publibike(Set.of(near, far)), pt(ptTimes, Set.of()))));
	}

	@Test
	public void testNoRouteWithoutTransferStationInReach() {
		PubliBikeTestNetwork remote = new PubliBikeTestNetwork();
		Link from = remote.link("origin", 0.0, 0.0);
		Link to = remote.link("destination", 20000.0, 0.0);
		remote.stop("rail", 15000.0, 0.0, "rail");
		remote.station("station", 15000.0, 0.0); // 15 km from the origin

		PubliBikePtRoutingModule router = new PubliBikePtRoutingModule(PubliBikePt.ACCESS_MODE, Direction.ACCESS,
				publibike(Set.of()), pt(Map.of(), Set.of()), remote.transferStations(300.0),
				PopulationUtils.getFactory(), timeInterpretation);

		assertNull(router.calcRoute(DefaultRoutingRequest.withoutAttributes(new LinkWrapperFacility(from),
				new LinkWrapperFacility(to), DEPARTURE_TIME, null)));
	}

	private PubliBikePtRoutingModule router(Direction direction, RoutingModule publibike, RoutingModule pt) {
		String mode = direction == Direction.ACCESS ? PubliBikePt.ACCESS_MODE : PubliBikePt.EGRESS_MODE;
		return new PubliBikePtRoutingModule(mode, direction, publibike, pt, test.transferStations(300.0),
				PopulationUtils.getFactory(), timeInterpretation);
	}

	private List<? extends PlanElement> route(PubliBikePtRoutingModule router) {
		return router.calcRoute(DefaultRoutingRequest.withoutAttributes(new LinkWrapperFacility(origin),
				new LinkWrapperFacility(destination), DEPARTURE_TIME, null));
	}

	private static int transferIndex(List<? extends PlanElement> route, String mode) {
		for (int i = 0; i < route.size(); i++) {
			if (route.get(i) instanceof Activity activity
					&& activity.getType().equals(PubliBikePt.interactionActivity(mode))) {
				return i;
			}
		}
		throw new AssertionError("No transfer in " + route);
	}

	/** Rides at 5 m/s between the two links; returns no route if either end is in {@code noRide}. */
	private static RoutingModule publibike(Set<Link> noRide) {
		return request -> {
			Facility from = request.getFromFacility();
			Facility to = request.getToFacility();
			if (isAt(from, noRide) || isAt(to, noRide)) {
				return null;
			}

			double time = request.getDepartureTime();
			List<PlanElement> elements = new ArrayList<>();
			time = addLeg(elements, TransportMode.walk, time, 60.0);
			elements.add(stageActivity(SharingUtils.PICKUP_ACTIVITY, from.getLinkId()));
			time += 60.0;
			double distance = CoordUtils.calcEuclideanDistance(from.getCoord(), to.getCoord());
			time = addLeg(elements, PubliBikeSharing.MODE, time, distance / 5.0);
			elements.add(stageActivity(SharingUtils.DROPOFF_ACTIVITY, to.getLinkId()));
			time += 60.0;
			addLeg(elements, TransportMode.walk, time, 0.0);
			return elements;
		};
	}

	/**
	 * Walks 120 s, rides for the time given for the station end of the trip (1000 s otherwise), walks 60 s. Only
	 * walks if the station end is in {@code walkOnly}.
	 */
	private static RoutingModule pt(Map<Link, Double> rideTimes, Set<Link> walkOnly) {
		return request -> {
			Facility from = request.getFromFacility();
			Facility to = request.getToFacility();
			Facility station = isAt(from, rideTimes.keySet()) || isAt(from, walkOnly) ? from : to;

			List<PlanElement> elements = new ArrayList<>();
			double time = request.getDepartureTime();
			if (isAt(station, walkOnly)) {
				addLeg(elements, TransportMode.walk, time, 3600.0);
				return elements;
			}
			double rideTime = rideTimes.entrySet().stream()
					.filter(entry -> entry.getKey().getId().equals(station.getLinkId())).mapToDouble(Map.Entry::getValue)
					.findFirst().orElse(1000.0);
			time = addLeg(elements, TransportMode.walk, time, 120.0);
			elements.add(stageActivity("pt interaction", from.getLinkId()));
			time = addLeg(elements, TransportMode.pt, time, rideTime);
			elements.add(stageActivity("pt interaction", to.getLinkId()));
			addLeg(elements, TransportMode.walk, time, 60.0);
			return elements;
		};
	}

	private static double addLeg(List<PlanElement> elements, String mode, double departureTime, double travelTime) {
		Leg leg = PopulationUtils.createLeg(mode);
		leg.setDepartureTime(departureTime);
		leg.setTravelTime(travelTime);
		elements.add(leg);
		return departureTime + travelTime;
	}

	private static boolean isAt(Facility facility, Set<Link> links) {
		return links.stream().anyMatch(link -> link.getId().equals(facility.getLinkId()));
	}

	private static Activity stageActivity(String type, Id<Link> linkId) {
		Activity activity = PopulationUtils.createActivityFromLinkId(type, linkId);
		activity.setMaximumDuration(type.equals("pt interaction") ? 0.0 : 60.0);
		return activity;
	}
}
