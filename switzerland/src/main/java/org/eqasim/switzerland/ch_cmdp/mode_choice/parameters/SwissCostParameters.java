package org.eqasim.switzerland.ch_cmdp.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.ParameterDefinition;

public class SwissCostParameters implements ParameterDefinition {
	public double carCost_CHF_km = 0.0;

	public double urbancoreParkingCost_CHF_h = 0.0;
	public double urbanParkingCost_CHF_h = 0.0;
	public double suburbanParkingCost_CHF_h = 0.0;
	public double parkingPriceReductionForWork = 1.0;

	public double ptCost_CHF_km = 0.0;
	public double ptCost_CHF_km2 = 0.0;
	public double ptMinimumCost_CHF = 0.0;

	public double ptRegionalRadius_km = 0.0;

	// PubliBike pay-per-ride fare (B-Quick, no subscription)
	public double publibikeFirst30min_CHF = 0.0;
	public double publibikeExtraMinute_CHF = 0.0;
	public double publibikeMaximum_CHF = 0.0;

	public static SwissCostParameters buildDefault() {
		SwissCostParameters parameters = new SwissCostParameters();
		// car
		parameters.carCost_CHF_km = 0.26;
		parameters.urbancoreParkingCost_CHF_h = 1.5;
		parameters.urbanParkingCost_CHF_h = 1.0;
		parameters.suburbanParkingCost_CHF_h = 0.5;
		parameters.parkingPriceReductionForWork = 1.0;
		// pt
		parameters.ptCost_CHF_km = 0.21 * 2;
		parameters.ptCost_CHF_km2 = -0.00015 * 2;
		parameters.ptMinimumCost_CHF = 2.8;

		parameters.ptRegionalRadius_km = 10.0;

		// PubliBike B-Quick tariff (2026): bike CHF 3 / e-bike CHF 5 for 30 min, then CHF 0.10 / 0.20
		// per minute, capped at CHF 24 / 48. The model pools both, so prices are weighted by the
		// e-bike share of the Zurich fleet (1439 of 2298 bikes = 62.6%, snapshot 3 Nov 2026).
		parameters.publibikeFirst30min_CHF = 4.25;
		parameters.publibikeExtraMinute_CHF = 0.163;
		parameters.publibikeMaximum_CHF = 39.0;

		return parameters;
	}
}
