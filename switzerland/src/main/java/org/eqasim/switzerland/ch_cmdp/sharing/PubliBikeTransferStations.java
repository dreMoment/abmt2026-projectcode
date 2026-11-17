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

	private final QuadTree<Link> stations; // null without any transfer station

	PubliBikeTransferStations(Collection<Link> stationLinks) {
		List<Link> links = stationLinks.stream().distinct().toList(); // stations may share a link
		this.stations = links.isEmpty() ? null : QuadTrees.createQuadTree(links, Link::getCoord, 1.0);
	}

	/** Keeps the PubliBike stations that have a stop served by a rail route within the given radius. */
	static public PubliBikeTransferStations create(Collection<? extends SharingStationSpecification> stations,
			TransitSchedule schedule, Network network, double radius_m) {
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

		List<Link> transferLinks = new ArrayList<>();

		if (!railStops.isEmpty()) {
			QuadTree<TransitStopFacility> stopIndex = QuadTrees.createQuadTree(
					railStops.stream().distinct().toList(), TransitStopFacility::getCoord, 1.0);

			for (SharingStationSpecification station : stations) {
				Link link = network.getLinks().get(station.getLinkId());

				if (link != null && !stopIndex.getDisk(link.getCoord().getX(), link.getCoord().getY(), radius_m)
						.isEmpty()) {
					transferLinks.add(link);
				}
			}
		}

		return new PubliBikeTransferStations(transferLinks);
	}

	public int size() {
		return stations == null ? 0 : stations.size();
	}

	/**
	 * Transfer stations for a trip whose bike part starts or ends at {@code bikeEnd}, while public transport covers
	 * the way to or from {@code otherEnd}.
	 *
	 * A station qualifies if it is at most {@code maximumBikeDistance_m} (beeline) from {@code bikeEnd} and closer
	 * to {@code otherEnd} than {@code bikeEnd} is, so the bike never rides away from the other end. The stations
	 * with the shortest beeline detour {@code bikeEnd -> station -> otherEnd} come first.
	 */
	public List<Link> findCandidates(Coord bikeEnd, Coord otherEnd, double maximumBikeDistance_m, int maximumCount) {
		if (stations == null) {
			return List.of();
		}

		double tripDistance = CoordUtils.calcEuclideanDistance(bikeEnd, otherEnd);

		return stations.getDisk(bikeEnd.getX(), bikeEnd.getY(), maximumBikeDistance_m).stream()
				.filter(link -> CoordUtils.calcEuclideanDistance(link.getCoord(), otherEnd) < tripDistance)
				.sorted(Comparator.comparingDouble((Link link) -> CoordUtils.calcEuclideanDistance(bikeEnd,
						link.getCoord()) + CoordUtils.calcEuclideanDistance(link.getCoord(), otherEnd))
						.thenComparing(link -> link.getId().toString()))
				.limit(maximumCount) //
				.toList();
	}
}
