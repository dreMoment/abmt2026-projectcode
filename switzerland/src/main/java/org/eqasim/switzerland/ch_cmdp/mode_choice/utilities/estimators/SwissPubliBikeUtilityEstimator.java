package org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.components.calibration.VariablesWriter;
import org.eqasim.core.simulation.mode_choice.utilities.variables.BikeVariables;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PredictorUtils;
import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCmdpModeParameters;
import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCostParameters;
import org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.predictors.SwissBikePredictor;
import org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.predictors.SwissPersonPredictor;
import org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.variables.SwissPersonVariables;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.core.router.TripStructureUtils;

import com.google.inject.Inject;

/**
 * Utility of a station-based bike sharing trip (PubliBike).
 *
 * It uses every term of the bike utility, with travel time taken from the
 * shared bike leg only. Walking to and from the stations is valued like walk
 * travel time. An additional constant (publibike.alpha_u) is zero until
 * calibrated. The PubliBike pay-per-ride fare enters like car and pt costs.
 */
public class SwissPubliBikeUtilityEstimator extends SwissBikeDetailedUtilityEstimator {
	private final SwissCmdpModeParameters parameters;
	private final SwissPersonPredictor personPredictor;
	private final SwissCostParameters costParameters;

	@Inject
	public SwissPubliBikeUtilityEstimator(SwissCmdpModeParameters parameters, SwissCostParameters costParameters,
			SwissPersonPredictor personPredictor, SwissBikePredictor bikePredictor, VariablesWriter variablesWriter) {
		super(parameters, personPredictor, bikePredictor, variablesWriter);
		this.parameters = parameters;
		this.costParameters = costParameters;
		this.personPredictor = personPredictor;
	}

	/** Pay-per-ride fare for a rental of the given duration. */
	static public double calculateFare_CHF(SwissCostParameters costParameters, double rideTime_min) {
		double extraMinutes = Math.max(0.0, Math.ceil(rideTime_min - 30.0));
		double fare = costParameters.publibikeFirst30min_CHF + extraMinutes * costParameters.publibikeExtraMinute_CHF;
		return Math.min(fare, costParameters.publibikeMaximum_CHF);
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		double rideTime_min = 0.0;
		double walkTime_min = 0.0;

		for (Leg leg : TripStructureUtils.getLegs(elements)) {
			double travelTime_min = leg.getTravelTime().seconds() / 60.0;

			if (leg.getMode().equals(TransportMode.walk)) {
				walkTime_min += travelTime_min;
			} else {
				rideTime_min += travelTime_min;
			}
		}

		SwissPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);

		double utility = 0.0;
		utility += estimateConstantUtility() + parameters.publibike.alpha_u;
		utility += estimateTravelTimeUtility(new BikeVariables(rideTime_min));
		// fare, valued like car and pt costs (with the same distance and income interaction)
		utility += parameters.betaCost_u_MU * calculateFare_CHF(costParameters, rideTime_min) * Utils.interaction(
				PredictorUtils.calculateEuclideanDistance_km(trip), personVariables.income, parameters);
		utility += parameters.walk.betaTravelTime_u_min
				* Math.pow(walkTime_min / parameters.timeScale_min, parameters.walk.travelTimeExponent);
		// same person, purpose, location, distance and canton terms as bike
		utility += estimateAgeUtility(personVariables);
		utility += estimateSexUtility(personVariables);
		utility += estimateRetiredUtility(personVariables);
		utility += estimateJuniorUtility(personVariables);
		utility += estimateLowIncomeUtility(personVariables);
		utility += estimateHighIncomeUtility(personVariables);
		utility += estimateHomeDestinationUtility(trip);
		utility += estimateWorkDestinationUtility(trip);
		utility += estimateEducationDestinationUtility(trip);
		utility += estimateShoppingDestinationUtility(trip);
		utility += estimateLeisureDestinationUtility(trip);
		utility += estimateOtherDestinationUtility(trip);
		utility += estimateHomeOriginUtility(trip);
		utility += estimateDensitiesUtility(trip);
		utility += estimateRegionalUtility(personVariables);
		utility += estimateShortDistanceUtility(trip);
		utility += estimatedLongDistanceUtility(trip);
		utility += estimateUrbanDestinationUtility(trip);
		utility += estimateUrbancoreDestinationUtility(trip);
		utility += estimateCantonUtility(person);
		return utility;
	}
}
