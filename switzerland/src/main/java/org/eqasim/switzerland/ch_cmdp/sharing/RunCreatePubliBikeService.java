package org.eqasim.switzerland.ch_cmdp.sharing;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.shared_mobility.io.DefaultSharingServiceSpecification;
import org.matsim.contrib.shared_mobility.io.ImmutableSharingStationSpecification;
import org.matsim.contrib.shared_mobility.io.ImmutableSharingVehicleSpecification;
import org.matsim.contrib.shared_mobility.io.SharingServiceWriter;
import org.matsim.contrib.shared_mobility.service.SharingStation;
import org.matsim.contrib.shared_mobility.service.SharingVehicle;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;

/**
 * Builds a station-based sharing service file from PubliBike stations and a
 * bike snapshot.
 *
 * stations.csv has station_id, lat and lng. Each station is placed on the
 * nearest link that shared bikes are routed on. The start fleet comes from one
 * of two CSV files:
 *
 * - --bikes-path bikes.csv (bike_id, station_id): exactly these bikes.
 * - --station-means-path station_means.csv (station_id, mean_bikes): each station
 * gets mean_bikes * fleet-share bikes. The whole part is always placed, the
 * fractional part is one more bike with that probability (seeded). For example,
 * 0.83 gives one bike with probability 0.83, and 2.3 gives two or three bikes.
 * With --fleet-share 0.1 this builds the start fleet of a 10% scenario.
 *
 * PubliBike stations are never full, so each station can hold the whole fleet.
 *
 * --network-path network.xml.gz --stations-path stations.csv --output-path
 * publibike_service.xml (--bikes-path bikes.csv | --station-means-path
 * station_means.csv [--fleet-share 1.0])
 *
 * Station coordinates are WGS84, the network is Swiss LV95 (EPSG:2056).
 */
public class RunCreatePubliBikeService {
	static private final double MAX_LINK_DISTANCE_M = 200.0;

	static public void main(String[] args) throws ConfigurationException, IOException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("network-path", "stations-path", "output-path") //
				.allowOptions("bikes-path", "station-means-path", "fleet-share") //
				.build();

		boolean fromBikes = cmd.hasOption("bikes-path");
		if (fromBikes == cmd.hasOption("station-means-path")) {
			throw new IllegalArgumentException("Give exactly one of --bikes-path and --station-means-path");
		}
		if (fromBikes && cmd.hasOption("fleet-share")) {
			throw new IllegalArgumentException("--fleet-share needs --station-means-path");
		}

		String fleetOption = fromBikes ? "bikes-path" : "station-means-path";
		for (String option : List.of("network-path", "stations-path", fleetOption)) {
			File file = new File(cmd.getOptionStrict(option));
			if (!file.isFile()) {
				throw new IllegalArgumentException("--" + option + " not found: " + file.getAbsolutePath());
			}
		}

		double fleetShare = cmd.getOption("fleet-share").map(Double::parseDouble).orElse(1.0);
		if (!(fleetShare > 0.0 && fleetShare <= 1.0)) {
			throw new IllegalArgumentException("--fleet-share must be in (0, 1], got " + fleetShare);
		}

		// Stations are placed on the links that shared bikes are routed on
		Network fullNetwork = NetworkUtils.readNetwork(cmd.getOptionStrict("network-path"));
		PubliBikeSharing.adjustNetwork(fullNetwork);
		Network network = NetworkUtils.createNetwork();
		new TransportModeNetworkFilter(fullNetwork).filter(network, java.util.Set.of(PubliBikeSharing.MODE));

		CoordinateTransformation transformation = TransformationFactory
				.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:2056");

		List<String[]> bikes = new ArrayList<>();
		if (fromBikes) {
			for (Map<String, String> row : readCsv(cmd.getOptionStrict("bikes-path"))) {
				bikes.add(new String[] { row.get("bike_id"), row.get("station_id") });
			}
		} else {
			Map<String, Double> meanBikes = new LinkedHashMap<>();
			for (Map<String, String> row : readCsv(cmd.getOptionStrict("station-means-path"))) {
				meanBikes.put(row.get("station_id"), Double.parseDouble(row.get("mean_bikes")));
			}

			int index = 0;
			for (Map.Entry<String, Integer> entry : sampleStationFleet(meanBikes, fleetShare, new Random(0))
					.entrySet()) {
				for (int i = 0; i < entry.getValue(); i++) {
					bikes.add(new String[] { String.format("pb_%04d", ++index), entry.getKey() });
				}
			}
		}

		// PubliBike stations are never full, so each station can hold the whole fleet
		int capacity = Math.max(1, bikes.size());

		DefaultSharingServiceSpecification service = new DefaultSharingServiceSpecification();
		Map<String, Id<Link>> stationLinks = new HashMap<>();
		int farStations = 0;

		for (Map<String, String> row : readCsv(cmd.getOptionStrict("stations-path"))) {
			Coord coord = transformation.transform(
					new Coord(Double.parseDouble(row.get("lng")), Double.parseDouble(row.get("lat"))));
			Link link = NetworkUtils.getNearestLink(network, coord);

			if (NetworkUtils.getEuclideanDistance(coord, link.getCoord()) > MAX_LINK_DISTANCE_M) {
				farStations++;
			}

			stationLinks.put(row.get("station_id"), link.getId());
			service.addStation(ImmutableSharingStationSpecification.newBuilder() //
					.id(Id.create(row.get("station_id"), SharingStation.class)) //
					.linkId(link.getId()) //
					.capacity(capacity) //
					.build());
		}

		if (farStations > stationLinks.size() / 2) {
			throw new IllegalStateException(farStations + " of " + stationLinks.size()
					+ " stations are far from any bike link. Is the network in EPSG:2056?");
		}

		for (String[] bike : bikes) {
			if (!stationLinks.containsKey(bike[1])) {
				throw new IllegalStateException("A bike refers to an unknown station");
			}

			service.addVehicle(ImmutableSharingVehicleSpecification.newBuilder() //
					.id(Id.create(bike[0], SharingVehicle.class)) //
					.startStationId(Id.create(bike[1], SharingStation.class)) //
					.startLinkId(stationLinks.get(bike[1])) // needed by the contrib's vehicle source
					.build());
		}

		new SharingServiceWriter(service).write(cmd.getOptionStrict("output-path"));

		System.out.println("Wrote " + stationLinks.size() + " stations and " + bikes.size() + " bikes. "
				+ farStations + " stations are more than " + MAX_LINK_DISTANCE_M + " m from their link.");
	}

	/**
	 * Bikes per station: mean bikes times the fleet share. The whole part is always
	 * placed; the fractional part becomes one more bike with that probability.
	 */
	static Map<String, Integer> sampleStationFleet(Map<String, Double> meanBikes, double fleetShare,
			Random random) {
		Map<String, Integer> counts = new LinkedHashMap<>();

		for (Map.Entry<String, Double> entry : meanBikes.entrySet()) {
			double expected = entry.getValue() * fleetShare;
			int count = (int) Math.floor(expected);

			if (random.nextDouble() < expected - count) {
				count++;
			}

			counts.put(entry.getKey(), count);
		}

		return counts;
	}

	static private List<Map<String, String>> readCsv(String path) throws IOException {
		List<Map<String, String>> rows = new ArrayList<>();

		try (BufferedReader reader = IOUtils.getBufferedReader(path)) {
			String[] header = splitCsv(reader.readLine());
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}

				String[] values = splitCsv(line);
				Map<String, String> row = new HashMap<>();

				for (int i = 0; i < header.length; i++) {
					row.put(header[i], i < values.length ? values[i] : "");
				}

				rows.add(row);
			}
		}

		return rows;
	}

	// Minimal CSV split that respects double quotes (station names contain commas)
	static private String[] splitCsv(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;

		for (char c : line.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
			} else if (c == ',' && !quoted) {
				values.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}

		values.add(current.toString());
		return values.toArray(new String[0]);
	}
}
