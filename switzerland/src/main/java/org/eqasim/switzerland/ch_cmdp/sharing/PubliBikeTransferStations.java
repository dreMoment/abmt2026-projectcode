package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.shared_mobility.io.SharingStationSpecification;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.collections.QuadTrees;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * PubliBike stations next to a railway stop, where a PubliBike ride and public transport can be combined.
 *
 * Stations are placed at the coordinate of their link, as the sharing router does.
 */
public class PubliBikeTransferStations {
	static public final String RAIL_MODE = "rail";

	private final List<Link> transferStations;
	private final QuadTree<Link> allStations; // null without any station
	private final double maximumAccessDistance_m;

	PubliBikeTransferStations(Collection<Link> transferStations, Collection<Link> allStations,
			double maximumAccessDistance_m) {
		this.transferStations = transferStations.stream().distinct().toList(); // stations may share a link
		this.allStations = allStations.isEmpty() ? null
				: QuadTrees.createQuadTree(allStations.stream().distinct().toList(), Link::getCoord, 1.0);
		this.maximumAccessDistance_m = maximumAccessDistance_m;
	}

	/**
	 * Keeps the PubliBike stations that have a stop served by a rail route within {@code radius_m}.
	 * {@code maximumAccessDistance_m} is the walking limit to a station of the PubliBike service.
	 */
	static public PubliBikeTransferStations create(Collection<? extends SharingStationSpecification> stations,
			TransitSchedule schedule, Network network, double radius_m, double maximumAccessDistance_m) {
		List<TransitStopFacility> railStops = new ArrayList<>();

		for (TransitLine line : schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				if (route.getTransportMode().equals(RAIL_MODE)) {
					for (TransitRouteStop stop : route.getStops()) {
						railStops.add(stop.getStopFacility());
					}
				}
			}
		}

		List<Link> stationLinks = new ArrayList<>();
		for (SharingStationSpecification station : stations) {
			Link link = network.getLinks().get(station.getLinkId());
			if (link != null) {
				stationLinks.add(link);
			}
		}

		List<Link> transferLinks = new ArrayList<>();

		if (!railStops.isEmpty()) {
			QuadTree<TransitStopFacility> stopIndex = QuadTrees.createQuadTree(
					railStops.stream().distinct().toList(), TransitStopFacility::getCoord, 1.0);

			for (Link link : stationLinks) {
				if (!stopIndex.getDisk(link.getCoord().getX(), link.getCoord().getY(), radius_m).isEmpty()) {
					transferLinks.add(link);
				}
			}
		}

		return new PubliBikeTransferStations(transferLinks, stationLinks, maximumAccessDistance_m);
	}

	public int size() {
		return transferStations.size();
	}

	/**
	 * Transfer stations for a trip whose bike part starts or ends at {@code bikeEnd}, while public transport covers
	 * the way to or from {@code otherEnd}. The stations with the shortest beeline detour
	 * {@code bikeEnd -> station -> otherEnd} come first.
	 *
	 * Without any PubliBike station within walking distance of {@code bikeEnd}, the sharing router finds no ride,
	 * so there are no candidates.
	 */
	public List<Link> findCandidates(Coord bikeEnd, Coord otherEnd, int maximumCount) {
		if (allStations == null
				|| allStations.getDisk(bikeEnd.getX(), bikeEnd.getY(), maximumAccessDistance_m).isEmpty()) {
			return List.of();
		}

		return transferStations.stream()
				.sorted(Comparator.comparingDouble((Link link) -> CoordUtils.calcEuclideanDistance(bikeEnd,
						link.getCoord()) + CoordUtils.calcEuclideanDistance(link.getCoord(), otherEnd))
						.thenComparing(link -> link.getId().toString()))
				.limit(maximumCount) //
				.toList();
	}
}
