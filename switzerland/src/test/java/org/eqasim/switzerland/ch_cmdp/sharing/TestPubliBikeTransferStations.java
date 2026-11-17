package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;

public class TestPubliBikeTransferStations {
	@Test
	public void testOnlyStationsNearRailStopsAreTransferStations() {
		PubliBikeTestNetwork test = new PubliBikeTestNetwork();
		test.stop("rail", 1000.0, 0.0, "rail");
		test.stop("bus", 3000.0, 0.0, "bus");

		Link nearRail = test.station("nearRail", 1100.0, 0.0); // 100 m from the rail stop
		test.station("farFromRail", 1500.0, 0.0); // 500 m
		test.station("nearBus", 3050.0, 0.0); // next to a bus stop only

		PubliBikeTransferStations stations = test.transferStations(300.0);
		assertEquals(1, stations.size());
		assertEquals(List.of(nearRail), stations.findCandidates(new Coord(1500.0, 0.0), new Coord(10000.0, 0.0), 3));
	}

	@Test
	public void testWithoutRailStopsThereAreNoCandidates() {
		PubliBikeTestNetwork test = new PubliBikeTestNetwork();
		test.stop("bus", 1000.0, 0.0, "bus");
		test.station("station", 1000.0, 0.0);

		PubliBikeTransferStations stations = test.transferStations(300.0);
		assertEquals(0, stations.size());
		assertTrue(stations.findCandidates(new Coord(1000.0, 0.0), new Coord(10000.0, 0.0), 3).isEmpty());
	}

	@Test
	public void testCandidatesAreSortedByDetour() {
		PubliBikeTestNetwork test = new PubliBikeTestNetwork();
		test.station("home", 0.0, 0.0);
		Link onTheWay = transferStation(test, "onTheWay", 1000.0, 0.0);
		Link aside = transferStation(test, "aside", 1000.0, 2000.0);
		Link behind = transferStation(test, "behind", -500.0, 0.0); // a short ride away from the destination
		transferStation(test, "farAside", 0.0, 8000.0);

		PubliBikeTransferStations stations = test.transferStations(300.0);
		assertEquals(4, stations.size());

		Coord origin = new Coord(0.0, 0.0);
		Coord destination = new Coord(10000.0, 0.0);
		// detours: onTheWay 10000 m, behind 11000 m, aside about 11456 m, farAside about 20806 m
		assertEquals(List.of(onTheWay, behind, aside), stations.findCandidates(origin, destination, 3));
		assertEquals(List.of(onTheWay), stations.findCandidates(origin, destination, 1));
	}

	@Test
	public void testNoCandidatesWithoutAStationWithinWalkingDistance() {
		PubliBikeTestNetwork test = new PubliBikeTestNetwork();
		transferStation(test, "station", 3000.0, 0.0);

		PubliBikeTransferStations stations = test.transferStations(300.0);
		assertTrue(stations.findCandidates(new Coord(0.0, 0.0), new Coord(10000.0, 0.0), 3).isEmpty());
		assertEquals(1, stations.findCandidates(new Coord(2500.0, 0.0), new Coord(10000.0, 0.0), 3).size());
	}

	private static Link transferStation(PubliBikeTestNetwork test, String id, double x, double y) {
		test.stop(id + "_rail", x, y, "rail");
		return test.station(id, x, y);
	}
}
