package org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.estimators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eqasim.core.components.calibration.VariablesWriter;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCmdpModeParameters;
import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCostParameters;
import org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.predictors.SwissPersonPredictor;
import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikePt;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

/**
 * Utility of a trip that combines a PubliBike ride with public transport (see {@link PubliBikePt}).
 *
 * The trip is split at the transfer activity. The public transport part gets the full pt utility, including the
 * purpose, destination and distance terms of the whole trip. The PubliBike part adds the ride utility of
 * {@link SwissPubliBikeUtilityEstimator#estimateRideUtility}: bike constant, ride and walk time, fare and the
 * person terms of bike. The trip terms are not counted twice.
 *
 * Instead of {@code publibike.alpha_u}, combined trips get their own constant {@code publibike.alphaPt_u}, so the
 * split between PubliBike-only and combined trips can be calibrated.
 */
public class SwissPubliBikePtUtilityEstimator implements UtilityEstimator {
	/** Combined trips are not written to the calibration variables, which describe single modes. */
	static private final VariablesWriter NO_VARIABLES = new VariablesWriter() {
		@Override
		public void init(String filePath) {
		}

		@Override
		public void close() {
		}

		@Override
		public void writeVariables(String mode, String personId, int tripIndex, double departureTime,
				double utility, Map<String, String> attributes) {
		}

		@Override
		public boolean isInitiated() {
			return false;
		}
	};

	/** Utility of a candidate without a transfer; the trip constraint removes such candidates anyway. */
	static final double INVALID_UTILITY = -1000.0;

	private final UtilityEstimator rideEstimator;
	private final UtilityEstimator ptEstimator;
	private final SwissCmdpModeParameters parameters;

	@Inject
	public SwissPubliBikePtUtilityEstimator(SwissPubliBikeUtilityEstimator publibikeEstimator,
			SwissCmdpModeParameters parameters, SwissCostParameters costParameters, PtPredictor ptPredictor,
			SwissPersonPredictor personPredictor) {
		this(publibikeEstimator::estimateRideUtility, new SwissPtDetailedUtilityEstimator(parameters, ptPredictor,
				personPredictor, NO_VARIABLES, costParameters), parameters);
	}

	/** @param rideEstimator utility of the PubliBike part, see {@link SwissPubliBikeUtilityEstimator#estimateRideUtility} */
	SwissPubliBikePtUtilityEstimator(UtilityEstimator rideEstimator, UtilityEstimator ptEstimator,
			SwissCmdpModeParameters parameters) {
		this.rideEstimator = rideEstimator;
		this.ptEstimator = ptEstimator;
		this.parameters = parameters;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		int transferIndex = findTransferIndex(elements);

		if (transferIndex < 0) {
			return INVALID_UTILITY;
		}

		List<? extends PlanElement> first = elements.subList(0, transferIndex);
		List<? extends PlanElement> second = elements.subList(transferIndex + 1, elements.size());
		boolean publibikeFirst = first.stream().anyMatch(SwissPubliBikePtUtilityEstimator::isPickup);

		List<? extends PlanElement> publibikePart = publibikeFirst ? first : second;
		List<? extends PlanElement> ptPart = publibikeFirst ? second : first;

		// The pt predictors cache their variables per trip object. A copy of the trip keeps the variables of this
		// pt part apart from those of the pt-only alternative of the same trip.
		DiscreteModeChoiceTrip tripCopy = copy(trip);
		return ptEstimator.estimateUtility(person, tripCopy, new ArrayList<>(ptPart))
				+ rideEstimator.estimateUtility(person, tripCopy, new ArrayList<>(publibikePart))
				+ parameters.publibike.alphaPt_u;
	}

	static int findTransferIndex(List<? extends PlanElement> elements) {
		for (int i = 0; i < elements.size(); i++) {
			if (elements.get(i) instanceof Activity activity && PubliBikePt.MODES.stream()
					.anyMatch(mode -> activity.getType().equals(PubliBikePt.interactionActivity(mode)))) {
				return i;
			}
		}

		return -1;
	}

	private static boolean isPickup(PlanElement element) {
		return element instanceof Activity activity && activity.getType().equals(SharingUtils.PICKUP_ACTIVITY);
	}

	private static DiscreteModeChoiceTrip copy(DiscreteModeChoiceTrip trip) {
		DiscreteModeChoiceTrip copy = new DiscreteModeChoiceTrip(trip.getOriginActivity(),
				trip.getDestinationActivity(), trip.getInitialMode(), trip.getInitialElements(), 0, 0,
				trip.getIndex(), trip.getTripAttributes());
		copy.setDepartureTime(trip.getDepartureTime());
		return copy;
	}
}
