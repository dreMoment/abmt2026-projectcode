package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.HashSet;
import java.util.Set;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.switzerland.ch_cmdp.mode_choice.SwissModeChoiceModule;
import org.matsim.contrib.shared_mobility.run.SharingConfigGroup;
import org.matsim.contrib.shared_mobility.run.SharingServiceConfigGroup;
import org.matsim.contrib.shared_mobility.run.SharingServiceConfigGroup.ServiceScheme;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.RoutingConfigGroup.TeleportedModeParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ModeParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ScoringParameterSet;

/**
 * Config setup for station-based bike sharing (PubliBike).
 *
 * The shared bike leg uses its own teleported mode "publibike". This version
 * of the shared_mobility contrib puts a default (car) vehicle on the network
 * for network-routed modes, so routing it as "bike" would give shared bikes car
 * speeds and mix them into private bike volumes.
 */
public final class PubliBikeSharing {
	static public final String MODE = "publibike";
	static public final String SERVICE_MODE = SharingUtils.MODE_PREFIX + MODE; // the mode choice alternative

	static private final double MAXIMUM_ACCESS_EGRESS_DISTANCE_M = 1000.0;

	// Same as teleported bike in SwitzerlandConfigurator.removeBikeFromRoutedModes
	static private final double TELEPORTED_SPEED_M_S = 4.0;
	static private final double BEELINE_DISTANCE_FACTOR = 1.4;

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

		TeleportedModeParams routing = new TeleportedModeParams(MODE);
		routing.setTeleportedModeSpeed(TELEPORTED_SPEED_M_S);
		routing.setBeelineDistanceFactor(BEELINE_DISTANCE_FACTOR);
		config.routing().addTeleportedModeParams(routing);

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
}
