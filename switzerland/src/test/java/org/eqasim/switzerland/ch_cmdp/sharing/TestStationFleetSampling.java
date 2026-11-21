package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

public class TestStationFleetSampling {
	@Test
	public void eachStationGetsTheWholePartPlusAtMostOneBike() {
		Map<String, Double> means = new LinkedHashMap<>();
		means.put("small", 8.27); // 0.827 -> 0 or 1
		means.put("busy", 23.0); // 2.3 -> 2 or 3
		means.put("exact", 10.0); // 1.0 -> always 1
		means.put("empty", 0.0); // always 0

		for (int seed = 0; seed < 200; seed++) {
			Map<String, Integer> counts = RunCreatePubliBikeService.sampleStationFleet(means, 0.1, new Random(seed));

			assertTrue(counts.get("small") == 0 || counts.get("small") == 1);
			assertTrue(counts.get("busy") == 2 || counts.get("busy") == 3);
			assertEquals(1, (int) counts.get("exact"));
			assertEquals(0, (int) counts.get("empty"));
		}
	}

	@Test
	public void theFractionalPartIsOneMoreBikeWithThatProbability() {
		Map<String, Double> means = Map.of("station", 8.0); // 0.8 at a 10% share
		Random random = new Random(0);
		int draws = 100000;
		int bikes = 0;

		for (int i = 0; i < draws; i++) {
			bikes += RunCreatePubliBikeService.sampleStationFleet(means, 0.1, random).get("station");
		}

		assertEquals(0.8, (double) bikes / draws, 0.01);
	}

	@Test
	public void aFullShareKeepsTheRoundedDownMeanAtLeast() {
		Map<String, Double> means = Map.of("station", 4.5);
		int count = RunCreatePubliBikeService.sampleStationFleet(means, 1.0, new Random(0)).get("station");
		assertTrue(count == 4 || count == 5);
	}
}
