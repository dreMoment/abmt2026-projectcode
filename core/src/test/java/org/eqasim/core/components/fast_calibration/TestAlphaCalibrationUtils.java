package org.eqasim.core.components.fast_calibration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class TestAlphaCalibrationUtils {
    @Test
    public void testParseTripsPerDayKeepsColonsInModeNames() {
        Map<String, Double> trips = AlphaCalibrationUtils.parseTripsPerDay(" sharing_publibike : 5629 , mode:with:colons:10.5");
        assertEquals(2, trips.size());
        assertEquals(5629.0, trips.get("sharing_publibike"), 1e-9);
        assertEquals(10.5, trips.get("mode:with:colons"), 1e-9);
    }

    @Test
    public void testParseTripsPerDayEmpty() {
        assertTrue(AlphaCalibrationUtils.parseTripsPerDay("").isEmpty());
        assertTrue(AlphaCalibrationUtils.parseTripsPerDay(null).isEmpty());
    }

    @Test
    public void testParseTripsPerDayRejectsMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> AlphaCalibrationUtils.parseTripsPerDay("sharing_publibike"));
        assertThrows(IllegalArgumentException.class, () -> AlphaCalibrationUtils.parseTripsPerDay(":5"));
        assertThrows(IllegalArgumentException.class, () -> AlphaCalibrationUtils.parseTripsPerDay("bike:0"));
    }

    @Test
    public void testTargetShareFromTripsScalesToTheSample() {
        // 5629 rides per day in the full population are 562.9 rides in a 10% sample
        assertEquals(562.9 / 500_000.0, AlphaCalibrationUtils.targetShareFromTrips(5629.0, 0.1, 500_000), 1e-12);
        assertThrows(IllegalStateException.class, () -> AlphaCalibrationUtils.targetShareFromTrips(5629.0, 0.1, 0));
    }

    @Test
    public void testConfigReadsTripsPerDay() {
        Config config = ConfigUtils.createConfig();
        AlphaCalibratorConfig calibration = AlphaCalibratorConfig.getOrCreate(config);
        calibration.setTripsPerDay("sharing_publibike:5629");
        assertEquals(Map.of("sharing_publibike", 5629.0), calibration.getTripsPerDayMap());
        assertThrows(IllegalArgumentException.class, () -> calibration.setTripsPerDay("sharing_publibike"));
    }
}
