package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.List;
import java.util.Map;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.switzerland.ch_cmdp.mode_choice.SwissModeChoiceModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ScoringParameterSet;

/**
 * Config setup for trips that combine a PubliBike ride with public transport.
 *
 * Two mode choice alternatives cover the two directions:
 * <ul>
 * <li>{@link #ACCESS_MODE}: PubliBike to a station next to a railway stop, then public transport.</li>
 * <li>{@link #EGRESS_MODE}: public transport to a railway stop, then PubliBike from a station next to it.</li>
 * </ul>
 * Each trip of these modes contains exactly one PubliBike ride. A trip with a ride at both ends is not modelled.
 *
 * The bike part of the trip shares {@code publibike.alpha_u} with the PubliBike-only mode, so a single constant
 * is calibrated against all observed rides.
 */
public final class PubliBikePt {
	/** PubliBike first, then public transport. */
	static public final String ACCESS_MODE = "publibike_pt";
	/** Public transport first, then PubliBike. */
	static public final String EGRESS_MODE = "pt_publibike";
	static public final List<String> MODES = List.of(ACCESS_MODE, EGRESS_MODE);

	/** A PubliBike station counts as a transfer station if a railway stop is at most this far away. */
	static public final double TRANSFER_RADIUS_M = 300.0;
	/** Transfer stations further away (beeline) from the trip origin or destination are not tried. */
	static public final double MAXIMUM_BIKE_DISTANCE_M = 4000.0;
	/** Number of transfer stations routed per trip. The one with the earliest arrival is kept. */
	static public final int CANDIDATE_STATIONS = 3;

	private PubliBikePt() {
	}

	static public boolean isEnabled(Config config) {
		return EqasimConfigGroup.get(config).getEstimators().containsKey(ACCESS_MODE);
	}

	/** Stage activity between the PubliBike part and the public transport part of the trip. */
	static public String interactionActivity(String mode) {
		return mode + " interaction";
	}

	/** Trip modes that contain one PubliBike ride, mapped to the mode whose constant is calibrated for them. */
	static public Map<String, String> rideModes() {
		return Map.of(ACCESS_MODE, PubliBikeSharing.SERVICE_MODE, EGRESS_MODE, PubliBikeSharing.SERVICE_MODE);
	}

	/** Adds the combined modes. Requires {@link PubliBikeSharing#configure} to have run before. */
	static public void configure(Config config) {
		if (!PubliBikeSharing.isEnabled(config)) {
			throw new IllegalStateException("PubliBike + public transport needs the PubliBike service");
		}

		for (ScoringParameterSet scoring : config.scoring().getScoringParametersPerSubpopulation().values()) {
			for (String mode : MODES) {
				scoring.addActivityParams(
						new ActivityParams(interactionActivity(mode)).setScoringThisActivityAtAll(false));
			}
		}

		EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);
		for (String mode : MODES) {
			eqasimConfig.setEstimator(mode, SwissModeChoiceModule.PUBLIBIKE_PT_ESTIMATOR_NAME);
		}
	}
}
