package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;

/**
 * Decides which agents hold a PubliBike subscription. Only members may choose
 * the bike sharing mode. There is one subscription type with one fare (see
 * SwissCostParameters).
 *
 * How many members: the bike sharing membership rates of the Swiss Mobility
 * and Transport Microcensus 2021 (FSO/ARE 2023, "Mobilitätsverhalten der
 * Bevölkerung", table T 2.1.4.1) by home municipality, applied to the adults
 * (18+) living there: urban 2.0%, intermediate 0.9%, rural 0.5%.
 *
 * Who: as in Schütze (2023, ETH master thesis, PubliBike in Zurich), only
 * agents aged 16 to 65 may be members. In each area, the members are drawn with
 * the same probability among the agents aged 16 to 65 living there.
 *
 * Municipality types of the population: "urbancore" and "urban" count as
 * urban, "suburban" as intermediate, "rural" as rural. Persons without a home
 * municipality type (e.g. agents living outside the scenario area) and freight
 * agents are not members.
 *
 * The draw is seeded, so a run with the same population and seed gives the
 * same members. The result is stored as the person attribute
 * {@value #ATTRIBUTE}, so it also appears in the output plans.
 */
public final class PubliBikeMembership {
	static private final Logger logger = LogManager.getLogger(PubliBikeMembership.class);

	static public final String ATTRIBUTE = "isPubliBikeMember";
	static public final int MINIMUM_AGE = 16;
	static public final int MAXIMUM_AGE = 65;
	static public final int ADULT_AGE = 18;

	public enum Area {
		URBAN, INTERMEDIATE, RURAL
	}

	/** Membership rate of adults per home area (MZMV 2021). */
	static public final class Rates {
		public double urban = 0.020;
		public double intermediate = 0.009;
		public double rural = 0.005;

		double get(Area area) {
			return switch (area) {
			case URBAN -> urban;
			case INTERMEDIATE -> intermediate;
			case RURAL -> rural;
			};
		}
	}

	private PubliBikeMembership() {
	}

	static public boolean isMember(Person person) {
		return Boolean.TRUE.equals(person.getAttributes().getAttribute(ATTRIBUTE));
	}

	static boolean isEligibleAge(int age) {
		return age >= MINIMUM_AGE && age <= MAXIMUM_AGE;
	}

	/** Maps a municipality type of the population to a Microcensus area, or null. */
	static Area getArea(Object municipalityType) {
		if (municipalityType == null) {
			return null;
		}

		return switch (municipalityType.toString().toLowerCase()) {
		case "urbancore", "urban" -> Area.URBAN;
		case "suburban" -> Area.INTERMEDIATE;
		case "rural" -> Area.RURAL;
		default -> null;
		};
	}

	/** Municipality type at the first home activity of the selected plan. */
	static Area getHomeArea(Person person) {
		if (person.getSelectedPlan() == null) {
			return null;
		}

		for (PlanElement element : person.getSelectedPlan().getPlanElements()) {
			if (element instanceof Activity activity && activity.getType().equals("home")) {
				return getArea(activity.getAttributes().getAttribute("municipalityType"));
			}
		}

		return null;
	}

	static private Integer getAge(Person person) {
		if (Boolean.TRUE.equals(person.getAttributes().getAttribute("isFreight"))) {
			return null;
		}

		Object age = person.getAttributes().getAttribute("age");
		return age instanceof Number number ? number.intValue() : null;
	}

	/**
	 * Draws the members and stores {@value #ATTRIBUTE} on every person.
	 *
	 * @return the number of members
	 */
	static public int assign(Population population, Rates rates, long seed) {
		for (Area area : Area.values()) {
			if (rates.get(area) < 0.0 || rates.get(area) > 1.0) {
				throw new IllegalArgumentException("PubliBike member rate must be between 0 and 1: " + area);
			}
		}

		// persons in a fixed order so the draw does not depend on map order
		List<Person> persons = new ArrayList<>(population.getPersons().values());
		persons.sort((a, b) -> a.getId().compareTo(b.getId()));

		int areas = Area.values().length;
		int[] adults = new int[areas];
		int[] eligible = new int[areas];
		int withArea = 0;
		int withoutArea = 0;

		for (Person person : persons) {
			Integer age = getAge(person);

			if (age == null) {
				continue;
			}

			Area area = getHomeArea(person);

			if (area == null) {
				withoutArea++;
				continue;
			}

			withArea++;

			if (age >= ADULT_AGE) {
				adults[area.ordinal()]++;
			}

			if (isEligibleAge(age)) {
				eligible[area.ordinal()]++;
			}
		}

		if (withArea == 0) {
			throw new IllegalStateException("No person has a home activity with a municipalityType attribute, "
					+ "so PubliBike members cannot be drawn by home area.");
		}

		// members per area = rate x adults, spread over the persons aged 16 to 65
		double[] probability = new double[areas];

		for (Area area : Area.values()) {
			int a = area.ordinal();
			double targetMembers = rates.get(area) * adults[a];
			probability[a] = eligible[a] > 0 ? Math.min(1.0, targetMembers / eligible[a]) : 0.0;
		}

		Random random = new Random(seed);
		int[] members = new int[areas];
		int memberTotal = 0;

		for (Person person : persons) {
			boolean member = false;
			Integer age = getAge(person);
			Area area = age == null ? null : getHomeArea(person);

			if (age != null && area != null && isEligibleAge(age)) {
				member = random.nextDouble() < probability[area.ordinal()];

				if (member) {
					members[area.ordinal()]++;
					memberTotal++;
				}
			}

			person.getAttributes().putAttribute(ATTRIBUTE, member);
		}

		logger.info(String.format("PubliBike members: %d of %d persons", memberTotal, persons.size()));

		for (Area area : Area.values()) {
			int a = area.ordinal();
			logger.info(String.format(
					"  %s (rate %.2f%% of %d adults): %d members of %d persons aged %d-%d (p = %.4f)", area,
					100.0 * rates.get(area), adults[a], members[a], eligible[a], MINIMUM_AGE, MAXIMUM_AGE,
					probability[a]));
		}

		if (withoutArea > 0) {
			logger.warn(String.format("%d persons have no home municipality type and are not PubliBike members",
					withoutArea));
		}

		return memberTotal;
	}
}
