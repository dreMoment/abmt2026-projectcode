package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.shared_mobility.service.SharingUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.Facility;
import org.matsim.utils.objectattributes.attributable.Attributes;

/**
 * Routes a trip that combines one PubliBike ride with public transport, joined at a PubliBike station next to a
 * railway stop.
 *
 * Up to {@link PubliBikePt#CANDIDATE_STATIONS} transfer stations are tried (see
 * {@link PubliBikeTransferStations#findCandidates}). The route with the earliest arrival is returned. A
 * candidate is dropped if the sharing router finds no ride (no station near the trip end, or the same station
 * at both ends) or if public transport finds no vehicle leg. Without any candidate the result is null, and
 * MATSim falls back to a walk leg that {@link SharingStationConstraint} removes from mode choice.
 *
 * The PubliBike part comes from the regular PubliBike router, so the QSim books, picks up and drops off the bike
 * as for a PubliBike-only trip.
 */
public class PubliBikePtRoutingModule implements RoutingModule {
	public enum Direction {
		/** PubliBike first, then public transport. */
		ACCESS,
		/** Public transport first, then PubliBike. */
		EGRESS
	}

	private final String mode;
	private final Direction direction;
	private final RoutingModule publibikeRouter;
	private final RoutingModule ptRouter;
	private final PubliBikeTransferStations transferStations;
	private final PopulationFactory populationFactory;
	private final TimeInterpretation timeInterpretation;

	public PubliBikePtRoutingModule(String mode, Direction direction, RoutingModule publibikeRouter,
			RoutingModule ptRouter, PubliBikeTransferStations transferStations, PopulationFactory populationFactory,
			TimeInterpretation timeInterpretation) {
		this.mode = mode;
		this.direction = direction;
		this.publibikeRouter = publibikeRouter;
		this.ptRouter = ptRouter;
		this.transferStations = transferStations;
		this.populationFactory = populationFactory;
		this.timeInterpretation = timeInterpretation;
	}

	@Override
	public List<? extends PlanElement> calcRoute(RoutingRequest request) {
		Facility from = request.getFromFacility();
		Facility to = request.getToFacility();
		double departureTime = request.getDepartureTime();

		Coord bikeEnd = direction == Direction.ACCESS ? from.getCoord() : to.getCoord();
		Coord otherEnd = direction == Direction.ACCESS ? to.getCoord() : from.getCoord();

		List<PlanElement> bestRoute = null;
		double bestArrivalTime = Double.POSITIVE_INFINITY;

		for (Link station : transferStations.findCandidates(bikeEnd, otherEnd, PubliBikePt.CANDIDATE_STATIONS)) {
			List<PlanElement> route = routeVia(station, request);

			if (route != null) {
				double arrivalTime = timeInterpretation.decideOnElementsEndTime(route, departureTime).seconds();

				if (arrivalTime < bestArrivalTime) {
					bestRoute = route;
					bestArrivalTime = arrivalTime;
				}
			}
		}

		return bestRoute;
	}

	private List<PlanElement> routeVia(Link station, RoutingRequest request) {
		Facility transfer = new LinkWrapperFacility(station);
		Person person = request.getPerson();
		Attributes attributes = request.getAttributes();

		RoutingModule firstRouter = direction == Direction.ACCESS ? publibikeRouter : ptRouter;
		RoutingModule secondRouter = direction == Direction.ACCESS ? ptRouter : publibikeRouter;

		List<? extends PlanElement> first = firstRouter.calcRoute(DefaultRoutingRequest.of(request.getFromFacility(),
				transfer, request.getDepartureTime(), person, attributes));
		if (!isValidPart(first, direction == Direction.ACCESS)) {
			return null;
		}

		double transferTime = timeInterpretation.decideOnElementsEndTime(first, request.getDepartureTime())
				.seconds();

		List<? extends PlanElement> second = secondRouter.calcRoute(
				DefaultRoutingRequest.of(transfer, request.getToFacility(), transferTime, person, attributes));
		if (!isValidPart(second, direction == Direction.EGRESS)) {
			return null;
		}

		Activity interaction = populationFactory.createActivityFromLinkId(PubliBikePt.interactionActivity(mode),
				station.getId());
		interaction.setCoord(station.getCoord());
		interaction.setMaximumDuration(0.0);

		List<PlanElement> route = new ArrayList<>(first.size() + second.size() + 1);
		route.addAll(first);
		route.add(interaction);
		route.addAll(second);
		return route;
	}

	/** A PubliBike part needs a bike pickup, a public transport part needs a vehicle leg. */
	private static boolean isValidPart(List<? extends PlanElement> elements, boolean isPubliBikePart) {
		if (elements == null) {
			return false;
		}

		return isPubliBikePart ? hasPickup(elements) : hasTransitLeg(elements);
	}

	static boolean hasPickup(List<? extends PlanElement> elements) {
		return elements.stream().anyMatch(element -> element instanceof Activity activity
				&& activity.getType().equals(SharingUtils.PICKUP_ACTIVITY));
	}

	static boolean hasTransitLeg(List<? extends PlanElement> elements) {
		return elements.stream()
				.anyMatch(element -> element instanceof Leg leg && leg.getMode().equals(TransportMode.pt));
	}
}
