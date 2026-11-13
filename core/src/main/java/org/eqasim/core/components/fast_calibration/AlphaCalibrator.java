package org.eqasim.core.components.fast_calibration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.replanning.TripListConverter;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ShutdownEvent;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class AlphaCalibrator implements FastCalibration {
    // This class tracks the mode share at the beginning of each iteration and calibrates the alpha value
    // based on the mode share from the previous iteration and the target mode shares.

    // Logging
    private static final Logger logger = LogManager.getLogger(AlphaCalibrator.class);

    // Calibration parameters
    private final Map<String, Double> targetModeShares;
    // observed trips per day of the full population, for modes whose target is a count (e.g. bike sharing rides)
    private final Map<String, Double> targetTripsPerDay;
    private final double beta;
    private final boolean isActivated;
    private final List<String> modesToCalibrate;

    // MATSim scenario and utilities
    private final Scenario scenario;
    private final IdMap<Person, Double> utilities = new IdMap<>(Person.class);
    private final Map<Id<Person>, List<Integer>> personsTracker = new HashMap<>();

    // Mode share tracking
    private final Map<String, Double> shares = new HashMap<>();
    private final Set<String> consideredModes = new HashSet<>(Set.of(TransportMode.car, TransportMode.pt, TransportMode.walk, TransportMode.bike, "car_passenger"));
    private int replannedTripsCount = 0;
    private int changedUtilityCount = 0;


    // Output and mode choice
    private final OutputDirectoryHierarchy outputHierarchy;
    private final ModeParameters modeParameters;
    private final TripListConverter tripListConverter;
    private String lastParametersFile = "";
    private final EqasimConfigGroup eqasimConfigGroup;

    public AlphaCalibrator(Scenario scenario,
                           OutputDirectoryHierarchy outputHierarchy,
                           ModeParameters modeParameters,
                           TripListConverter tripListConverter,
                           Map<String, Double> targetModeShares,
                           List<String> modesToCalibrate,
                           double beta,
                           boolean isActivated,
                           EqasimConfigGroup eqasimConfigGroup) {
        this(scenario, outputHierarchy, modeParameters, tripListConverter, targetModeShares, Map.of(),
                modesToCalibrate, beta, isActivated, eqasimConfigGroup);
    }

    /**
     * @param targetTripsPerDay observed trips per day of the full population for modes whose target is a count.
     *                          Each iteration, the count is turned into a target share using the sample size and
     *                          the number of considered trips in the selected plans.
     */
    public AlphaCalibrator(Scenario scenario,
                           OutputDirectoryHierarchy outputHierarchy,
                           ModeParameters modeParameters,
                           TripListConverter tripListConverter,
                           Map<String, Double> targetModeShares,
                           Map<String, Double> targetTripsPerDay,
                           List<String> modesToCalibrate,
                           double beta,
                           boolean isActivated,
                           EqasimConfigGroup eqasimConfigGroup) {

        this.targetModeShares = new HashMap<>(targetModeShares);
        this.targetTripsPerDay = Map.copyOf(targetTripsPerDay);
        this.consideredModes.addAll(targetModeShares.keySet());
        this.consideredModes.addAll(targetTripsPerDay.keySet());
        this.scenario = scenario;
        this.outputHierarchy = outputHierarchy;
        this.modeParameters = modeParameters;
        this.tripListConverter = tripListConverter;
        this.beta = beta;
        this.isActivated = isActivated;
        this.modesToCalibrate = modesToCalibrate;
        this.eqasimConfigGroup = eqasimConfigGroup;

        if (isActivated) {
            // if the calibration is activated, check the target mode shares and reset the plans creation flag
            checkTargetModeShares();
            resetPlansCreationFlag();
        }
    }

    public void checkTargetModeShares() {
        // Check first that the required modes are present in the target mode shares
        for (String mode : modesToCalibrate) {
            if (!targetModeShares.containsKey(mode) && !targetTripsPerDay.containsKey(mode)) {
                throw new IllegalArgumentException("Target mode shares or target trips per day must contain the mode: " + mode);
            }
            if (!modeParameters.getASCs().containsKey(mode)) {
                throw new IllegalArgumentException("The mode parameters have no alpha for the calibrated mode: " + mode);
            }
        }
        // pt is the reference mode only if it is calibrated. Without pt, each listed mode is calibrated on its own
        // and all other constants stay as they are, so the shares of the other modes need not add up.
        if (!usesPtReference()) {
            logger.info("pt is not in the calibrated modes: calibrating " + modesToCalibrate + " without a reference mode.");
            return;
        }
        // ensure the sum of target mode shares is below 1.0 (if passenger_car is not given, else it is 1.0)
        double sumOfShares = targetModeShares.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sumOfShares > 1.0) {
            logger.warn("The sum of target mode shares is greater than 1.0: " + sumOfShares + ". This may lead to unexpected results in the calibration process. " +
                        "Please check the target mode shares in the configuration file.");
        } else if (sumOfShares < 0.999) {
            logger.warn("The sum of target mode shares is less than 1.0: " + sumOfShares + ". This may lead to unexpected results in the calibration process. " +
                    "Please check the target mode shares in the configuration file.");
        }
    }

    @Override
    public void notifyIterationStarts(IterationStartsEvent event) {
        if (!isActivated) {
            return; // If the calibration is not activated, do nothing
        }
        int iteration = event.getIteration();
        // if a plan is replanned in the last iteration, the createdLastIteration attribute is set to true
        resetPlansCreationFlag();
        // Starting calibration at the beginning of the iteration
        if (iteration>1) {
            updateShares();
            updateAlphas(iteration);
        }
        // Save the shares and alphas to files at the end of each iteration
        saveSharesToFile(iteration);
        saveAlphasToFile(iteration);
        try {
            saveParametersToYaml(iteration);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void resetPlansCreationFlag() {
        changedUtilityCount = 0; // Counter for checking consistency when estimating mode shares
        double epsilon = 0.001102; // some small random value to capture plans that were replanned, but sill same mode and same utility

        for (Person person : scenario.getPopulation().getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            plan.getAttributes().putAttribute("createdLastIteration", false);

            // this part is for checking the utilities' changes
            Double utility = (Double) plan.getAttributes().getAttribute("utility");
            Double lastIterationUtility = utilities.get(person.getId());

            if (utility != null && lastIterationUtility != null) { // this happens in the first iteration, but anyway, calibration starts after the first iteration
                if (Math.abs(utility - lastIterationUtility) > 1e-5) {
                    List<DiscreteModeChoiceTrip> trips = tripListConverter.convert(plan);
                    changedUtilityCount += trips.size();
                    plan.getAttributes().putAttribute("createdLastIteration", true);
                }
            }

            double newUtility = utility==null ? 0.0 : Math.round(utility * 1_000_000.0) / 1_000_000.0 + epsilon; // add this to track changes
            plan.getAttributes().putAttribute("utility", newUtility);
            utilities.put(person.getId(), newUtility);
        }
    }

    private void updateShares() {
        Map<String, Double> estimatedShares = computeModeSharesFromPlans();
        updateTripTargets();
        for (String mode : targetModeShares.keySet()) {
            double replannedTripsShare = estimatedShares.getOrDefault(mode, 0.0); //0.0 because maybe no trip was recorded for this mode
            shares.put(mode, replannedTripsShare);
        }
    }

    /**
     * Turns the trips-per-day targets into target shares: observed trips, scaled to the sample, divided by the
     * considered trips of all selected plans (counted with the same filter as the replanned trips).
     */
    private void updateTripTargets() {
        if (targetTripsPerDay.isEmpty()) {
            return;
        }
        int consideredTrips = 0;
        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (NotConsideredPerson(person)) {
                continue;
            }
            for (DiscreteModeChoiceTrip trip : tripListConverter.convert(person.getSelectedPlan())) {
                if (isCountedTrip(trip)) {
                    consideredTrips++;
                }
            }
        }
        for (Map.Entry<String, Double> entry : targetTripsPerDay.entrySet()) {
            double share = AlphaCalibrationUtils.targetShareFromTrips(entry.getValue(),
                    eqasimConfigGroup.getSampleSize(), consideredTrips);
            targetModeShares.put(entry.getKey(), share);
            logger.info(String.format("Target share of %s: %.6f (%.0f trips per day, %d considered trips in the sample)",
                    entry.getKey(), share, entry.getValue(), consideredTrips));
        }
    }

    /** A trip counts for the shares if it has a considered mode, does not start or end outside, and moves. */
    private boolean isCountedTrip(DiscreteModeChoiceTrip trip) {
        boolean sameLocation = trip.getOriginActivity().getCoord().equals(trip.getDestinationActivity().getCoord());
        return AlphaCalibrationUtils.isConsideredTrip(trip) && consideredModes.contains(trip.getInitialMode()) && !sameLocation;
    }

    private boolean usesPtReference() {
        return modesToCalibrate.contains(TransportMode.pt);
    }

    protected boolean NotConsideredPerson(Person person){
        return !AlphaCalibrationUtils.isConsideredPerson(person);
    }

    private Map<String, Double> computeModeSharesFromPlans(){
        Map<String, Double> estimatedShares = new HashMap<>();
        replannedTripsCount = 0; // Reset the count of replanned plans
        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (NotConsideredPerson(person)) {
                continue; // Skip cross-border and freight agents
            }

            // Check if the plan was created in the last iteration
            Plan plan = person.getSelectedPlan();
            if ((Boolean) plan.getAttributes().getAttribute("createdLastIteration")) {
                List<DiscreteModeChoiceTrip> trips = tripListConverter.convert(plan);
                List<Integer> tripIdx = personsTracker.getOrDefault(person.getId(), new ArrayList<>());

                for (DiscreteModeChoiceTrip trip : trips) {
                    String mode = trip.getInitialMode();
                    // only consider trips with considered modes and different locations
                    if (isCountedTrip(trip)) {
                        estimatedShares.put(mode, estimatedShares.getOrDefault(mode, 0.0) + 1.0);
                        tripIdx.add(trip.getIndex());
                        replannedTripsCount += 1; // Count the number of replanned plans
                    }
                }
                personsTracker.put(person.getId(), tripIdx);
            }
        }

        // Normalize the shares
        double total = estimatedShares.values().stream().mapToDouble(Double::doubleValue).sum()+1e-10; // Avoid division by zero
        for (String mode : estimatedShares.keySet()) {
            double share = estimatedShares.get(mode) / total;
            estimatedShares.put(mode, share);
        }

        if (replannedTripsCount>changedUtilityCount){
            logger.warn("The number of replanned trips (as estimated: " + replannedTripsCount + ") does not match " +
                        "the number of trips in the plans whose utilities have changed (" + changedUtilityCount + "). " +
                        "This might indicate an inconsistency in the calibration process. Please check this part of the code.");
        }

        return estimatedShares;
    }

    private void updateAlphas(int iteration) {
        Map<String, Double> alphas = getAlphas();
        Map<String, Double> newAlphas = new HashMap<>();
        double epsilon = 0.0001; // Small value to avoid log(0) issues
        // If pt is calibrated, it is the reference mode: its alpha is set to 0.0 and the other updates are relative to it.
        // Otherwise, each calibrated mode is moved towards its own target and pt keeps its alpha.
        double referenceCorrection = 0.0;
        if (usesPtReference()) {
            double mo = Math.max(shares.get("pt"), epsilon);
            double zo = Math.max(targetModeShares.get("pt"), epsilon);
            referenceCorrection = Math.log(zo) - Math.log(mo);
            newAlphas.put("pt", 0.0);
        }
        // update alphas for other modes
        for (String mode : modesToCalibrate) {
            if (mode.equals("pt")) {
                continue; // Skip the reference mode
            }

            double mi = Math.max(shares.get(mode), epsilon);
            double zi = Math.max(targetModeShares.get(mode), epsilon);
            double alpha = alphas.get(mode);
            // Calculate the new alpha value based on the current share and the target share
            double newAlpha = alpha + (Math.log(zi)-Math.log(mi)) - referenceCorrection;
            // update it using EMA
            double effectiveBeta = getEffectiveBeta(iteration);
            newAlpha = effectiveBeta * alpha + (1.0 - effectiveBeta) * newAlpha;
            // put the new alpha in the map
            newAlphas.put(mode, newAlpha);
        }
        // Update the alphas in the mode parameters
        setAlphas(newAlphas);
    }

    private double getEffectiveBeta(int iteration) {
        // For regional updates, use beta based on iteration number
        if (iteration >= 120) {
            return 0.99;
        } else if (iteration > 90) {
            return 0.95;
        } else if (iteration < 10) {
            return 0.0;
        } else {
            // Gradually increase beta as iterations progress
            return Math.min(0.99, beta + (0.99 - beta) * (1.0 - 1.0 / (0.08 * (iteration - 10.0) + 1.0)));
        }
    }

    private Map<String, Double> getAlphas() {
        return modeParameters.getASCs();
    }

    private void setAlphas(Map<String, Double> alphas) {
        modeParameters.setASCs(alphas);
    }

    private void saveAlphasToFile(int iteration) {
        File outputFile = new File(outputHierarchy.getIterationFilename(iteration, "alphas.csv"));
        Map<String, Double> alphas = getAlphas();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("mode,alpha");
            for (Map.Entry<String, Double> entry : alphas.entrySet()) {
              writer.printf("%s,%.4f%n", entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
          throw new RuntimeException("Error writing alphas to file: " + outputFile.getAbsolutePath(), e);
        }
    }

    private void saveSharesToFile(int iteration) {
        File outputFile = new File(outputHierarchy.getIterationFilename(iteration, "shares.csv"));
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("mode,share,replanned_trips_count,changed_utility_count,target_share");
            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                String mode = entry.getKey();
                writer.printf(Locale.ROOT, "%s,%.6f,%d,%d,%.6f%n", mode, entry.getValue(), replannedTripsCount, changedUtilityCount,
                        targetModeShares.getOrDefault(mode, Double.NaN));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing shares to file: " + outputFile.getAbsolutePath(), e);
        }

//        File outputFile2 = new File(outputHierarchy.getIterationFilename(iteration, "replannedAgents.csv"));
//        try (PrintWriter writer2 = new PrintWriter(outputFile2)) {
//            writer2.println("person;replanned_trips_indices");
//            for (Map.Entry<Id<Person>, List<Integer>> entry : personsTracker.entrySet()) {
//                Id<Person> personId = entry.getKey();
//                List<Integer> tripIndices = entry.getValue();
//                writer2.printf("%s;%s%n", personId.toString(), tripIndices.toString());
//            }
//        } catch (IOException e) {
//            throw new RuntimeException("Error writing shares to file: " + outputFile.getAbsolutePath(), e);
//        }


    }

    private void saveParametersToYaml(int iteration) throws IOException {
        lastParametersFile = outputHierarchy.getIterationFilename(iteration, "mode_parameters.yml");
        modeParameters.saveToYamlFile(lastParametersFile);
    }

    @Override
    public void notifyShutdown(ShutdownEvent shutdownEvent) {
        // copy the last parameters file to the main output directory
        if (isActivated && !lastParametersFile.isEmpty()) {
            File sourceFile = new File(lastParametersFile);
            eqasimConfigGroup.setModeParametersPath(sourceFile.getAbsolutePath());

            File destFile = new File(outputHierarchy.getOutputFilenameWithOutputPrefix("mode_parameters.yml"));
            try {
                java.nio.file.Files.copy(sourceFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                eqasimConfigGroup.setModeParametersPath(sourceFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Error copying mode parameters file to main output directory.", e);
            }
        }
    }
}
