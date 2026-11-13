package org.eqasim.core.components.fast_calibration;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

public class AlphaCalibrationUtils {
    public static Boolean isConsideredPerson(Person person) {
        Boolean isCrossBorder = (Boolean) person.getAttributes().getAttribute("isCrossBorder");
        Boolean isFreight = (Boolean) person.getAttributes().getAttribute("isFreight");
        return !((isCrossBorder != null && isCrossBorder) || (isFreight != null && isFreight));
    }

    public static Boolean isConsideredTrip(DiscreteModeChoiceTrip trip) {
        String originActivity = trip.getOriginActivity().getType();
        String destinationActivity = trip.getDestinationActivity().getType();
        return !originActivity.equals("outside") && !destinationActivity.equals("outside");
    }

    public static Boolean isConsideredPerson(Person person, String additionalSubPopulation) {
        Boolean isInThisSubPopulation = (Boolean) person.getAttributes().getAttribute(additionalSubPopulation);
        return !(isInThisSubPopulation != null && isInThisSubPopulation);
    }

    /**
     * Parses "mode:trips,mode:trips" (e.g. "sharing_publibike:5629"). The split is at the last colon, so mode names
     * may contain colons. "=" is not used because the MATSim command line splits every argument at "=".
     */
    public static Map<String, Double> parseTripsPerDay(String value) {
        Map<String, Double> result = new HashMap<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.lastIndexOf(':');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw new IllegalArgumentException("Expected mode:tripsPerDay, got: " + trimmed);
            }
            double trips = Double.parseDouble(trimmed.substring(separator + 1).trim());
            if (!(trips > 0.0)) {
                throw new IllegalArgumentException("Trips per day must be positive: " + trimmed);
            }
            result.put(trimmed.substring(0, separator).trim(), trips);
        }
        return result;
    }

    /**
     * Target share of a mode with an observed count: observed trips per day of the full population, scaled to
     * the simulated sample, divided by the considered trips of the sample.
     */
    public static double targetShareFromTrips(double tripsPerDay, double sampleSize, int consideredTrips) {
        if (consideredTrips <= 0) {
            throw new IllegalStateException("No considered trips to compute a target share from");
        }
        return tripsPerDay * sampleSize / consideredTrips;
    }
}
