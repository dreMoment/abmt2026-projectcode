package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.List;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.router.DefaultAnalysisMainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;

/**
 * Main mode for analysis (mode stats, mode choice coverage) that knows the PubliBike + public transport modes.
 *
 * MATSim's default identifier accepts one unknown leg mode per trip, so PubliBike-only trips work, but a trip with a
 * "publibike" leg and a "pt" leg makes it throw. Such trips are counted under their routing mode
 * ({@link PubliBikePt#ACCESS_MODE} or {@link PubliBikePt#EGRESS_MODE}); all other trips go to the default.
 */
public class PubliBikePtMainModeIdentifier implements AnalysisMainModeIdentifier {
	private final AnalysisMainModeIdentifier delegate = new DefaultAnalysisMainModeIdentifier();

	@Override
	public String identifyMainMode(List<? extends PlanElement> tripElements) {
		for (PlanElement element : tripElements) {
			if (element instanceof Leg leg) {
				String routingMode = TripStructureUtils.getRoutingMode(leg);

				if (routingMode != null && PubliBikePt.MODES.contains(routingMode)) {
					return routingMode;
				}
			}
		}

		return delegate.identifyMainMode(tripElements);
	}
}
