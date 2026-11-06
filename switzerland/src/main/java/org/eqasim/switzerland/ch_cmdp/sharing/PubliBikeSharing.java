package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.HashSet;
import java.util.Set;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.switzerland.ch_cmdp.mode_choice.SwissModeChoiceModule;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.shared_mobility.run.SharingConfigGroup;
import org.matsim.contrib.shared_mobility.run.SharingServiceConfigGroup;
import org.matsim.contrib.shared_mobility.run.SharingServiceConfigGroup.ServiceScheme;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ModeParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ScoringParameterSet;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;

/**
 * Config setup for station-based bike sharing (PubliBike).
 *
 * The shared bike leg uses its own mode "publibike", routed over the bike
 * network by {@link PubliBikeRoutingModule}. It is not a QSim main mode, so it
 * is teleported along its route: the contrib would otherwise put a default
 * (car) vehicle into traffic, and the bikes would mix into private bike volumes.
 */
public final class PubliBikeSharing {
	static public final String MODE = "publibike";
	static public final String SERVICE_MODE = SharingUtils.MODE_PREFIX + MODE; // the mode choice alternative

	static private final double MAXIMUM_ACCESS_EGRESS_DISTANCE_M = 1000.0;

	private PubliBikeSharing() {
	}

	static public boolean isEnabled(Config config) {
		return config.getModules().containsKey(SharingConfigGroup.GROUP_NAME);
	}

	/** Adds the PubliBike service and its routing, scoring and mode choice settings. */
	static public void configure(Config config, String serviceInputFile) {
		SharingServiceConfigGroup service = new SharingServiceConfigGroup();
		service.setId(MODE);
		service.setMode(MODE);
		service.setServiceScheme(ServiceScheme.StationBased);
		service.setServiceInputFile(serviceInputFile);
		service.setMaximumAccessEgressDistance(MAXIMUM_ACCESS_EGRESS_DISTANCE_M);

		SharingConfigGroup sharingConfig = new SharingConfigGroup();
		sharingConfig.addService(service);
		config.addModule(sharingConfig);

		for (ScoringParameterSet scoring : config.scoring().getScoringParametersPerSubpopulation().values()) {
			scoring.addModeParams(new ModeParams(MODE));

			for (String activityType : new String[] { SharingUtils.BOOKING_ACTIVITY, SharingUtils.PICKUP_ACTIVITY,
					SharingUtils.DROPOFF_ACTIVITY }) {
				scoring.addActivityParams(new ActivityParams(activityType).setScoringThisActivityAtAll(false));
			}
		}

		EqasimConfigGroup.get(config).setEstimator(SERVICE_MODE, SwissModeChoiceModule.PUBLIBIKE_ESTIMATOR_NAME);

		DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules()
				.get(DiscreteModeChoiceConfigGroup.GROUP_NAME);
		Set<String> tripConstraints = new HashSet<>(dmcConfig.getTripConstraints());
		tripConstraints.add(SharingStationConstraint.NAME);
		dmcConfig.setTripConstraints(tripConstraints);
	}

	/**
	 * Allows shared bikes on the bike links of the largest strongly connected
	 * part of the bike network.
	 * Stations and routes both use these links.
	 */
	static public void adjustNetwork(Network network) {
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains(TransportMode.bike)) {
				NetworkUtils.addAllowedMode(link, MODE);
			}
		}

		new MultimodalNetworkCleaner(network).run(Set.of(MODE));
	}
}
