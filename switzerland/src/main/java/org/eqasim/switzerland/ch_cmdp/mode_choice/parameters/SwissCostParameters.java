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

	// PubliBike fare per ride: a base fare for the first minutes, then a price per minute, capped
	public double publibikeBaseFare_CHF = 0.0;
	public double publibikeIncludedMinutes = 0.0;
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

		// PubliBike: one subscription with one fare per ride, following Schütze (2023, ETH master
		// thesis, PubliBike in Zurich, Table 1). The three tariffs (e-bike prices, annual fees not
		// counted) are weighted by their share of rides in Zurich: BusinessBike 76% (CHF 0 base,
		// max 40), B-Fit 15.5% (CHF 3.50 base, max 48), B-Quick 8.5% (CHF 5.50 base, max 48).
		// The base fare covers the first 30 minutes, then all charge CHF 0.10 per minute.
		parameters.publibikeBaseFare_CHF = 0.76 * 0.0 + 0.155 * 3.50 + 0.085 * 5.50; // 1.01
		parameters.publibikeIncludedMinutes = 30.0;
		parameters.publibikeExtraMinute_CHF = 0.10;
		parameters.publibikeMaximum_CHF = 0.76 * 40.0 + 0.155 * 48.0 + 0.085 * 48.0; // 41.92

		return parameters;
	}
}
