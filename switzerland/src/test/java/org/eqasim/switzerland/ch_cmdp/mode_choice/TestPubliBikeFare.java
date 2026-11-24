package org.eqasim.switzerland.ch_cmdp.mode_choice;

import static org.junit.Assert.assertEquals;

import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCostParameters;
import org.eqasim.switzerland.ch_cmdp.mode_choice.utilities.estimators.SwissPubliBikeUtilityEstimator;
import org.junit.Test;

public class TestPubliBikeFare {
	static private double fare(double rideTime_min) {
		return SwissPubliBikeUtilityEstimator.calculateFare_CHF(SwissCostParameters.buildDefault(), rideTime_min);
	}

	@Test
	public void theBaseFareCoversTheFirst30Minutes() {
		assertEquals(1.01, fare(1.0), 1e-9);
		assertEquals(1.01, fare(10.0), 1e-9);
		assertEquals(1.01, fare(30.0), 1e-9);
	}

	@Test
	public void everyStartedMinuteAfterwardsCosts010() {
		assertEquals(1.01 + 0.10, fare(30.2), 1e-9);
		assertEquals(1.01 + 10 * 0.10, fare(40.0), 1e-9);
	}

	@Test
	public void theFareIsCapped() {
		assertEquals(41.92, fare(24 * 60.0), 1e-9);
	}
}
