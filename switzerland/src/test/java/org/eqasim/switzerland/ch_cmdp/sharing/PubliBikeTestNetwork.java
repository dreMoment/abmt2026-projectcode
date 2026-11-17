package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.shared_mobility.io.ImmutableSharingStationSpecification;
import org.matsim.contrib.shared_mobility.io.SharingStationSpecification;
import org.matsim.contrib.shared_mobility.service.SharingStation;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/** Small network, schedule and PubliBike stations for the PubliBike + pt tests. */
class PubliBikeTestNetwork {
	final Network network = NetworkUtils.createNetwork();
	final TransitSchedule schedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();
	final List<SharingStationSpecification> stations = new ArrayList<>();

	/** Adds a short link whose coordinate (its middle) is (x, y). */
	Link link(String id, double x, double y) {
		Node from = NetworkUtils.createAndAddNode(network, Id.createNodeId(id + "_from"), new Coord(x - 1.0, y));
		Node to = NetworkUtils.createAndAddNode(network, Id.createNodeId(id + "_to"), new Coord(x + 1.0, y));
		return NetworkUtils.createAndAddLink(network, Id.createLinkId(id), from, to, 2.0, 10.0, 1000.0, 1.0);
	}

	/** Adds a PubliBike station on a new link at (x, y). */
	Link station(String id, double x, double y) {
		Link link = link(id, x, y);
		stations.add(ImmutableSharingStationSpecification.newBuilder().id(Id.create(id, SharingStation.class))
				.linkId(link.getId()).capacity(10).build());
		return link;
	}

	/** Adds a stop at (x, y) served by a route of the given transport mode. */
	void stop(String id, double x, double y, String transportMode) {
		TransitScheduleFactory factory = schedule.getFactory();
		TransitStopFacility facility = factory.createTransitStopFacility(Id.create(id, TransitStopFacility.class),
				new Coord(x, y), false);
		schedule.addStopFacility(facility);

		TransitRouteStop routeStop = factory.createTransitRouteStop(facility, 0.0, 0.0);
		TransitRoute route = factory.createTransitRoute(Id.create(id, TransitRoute.class), null, List.of(routeStop),
				transportMode);
		TransitLine line = factory.createTransitLine(Id.create(id, TransitLine.class));
		line.addRoute(route);
		schedule.addTransitLine(line);
	}

	PubliBikeTransferStations transferStations(double radius_m) {
		return PubliBikeTransferStations.create(stations, schedule, network, radius_m, 1000.0);
	}
}
