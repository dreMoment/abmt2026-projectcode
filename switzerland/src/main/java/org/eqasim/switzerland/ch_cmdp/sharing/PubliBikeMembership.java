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
 * Who: set by the {@link Profile}. The default, {@link Profile#UNIFORM}, follows
 * Schütze (2023, ETH master thesis, PubliBike in Zurich): in each area, the
 * members are drawn with the same probability among the agents aged 16 to 65
 * living there. {@link Profile#COMMUTER} draws the same number of members per
 * area, but only among agents aged 18 to 45 whose plan has a work or education
 * activity, the user profile of Reck and Axhausen (young, employed, educated).
 * The plan stands in for "employed or student", because the population has no
 * education attribute. {@link Profile#COMMUTER_INCOME} also weights the draw by
 * the household income per capita.
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
	static public final int ADULT_AGE = 18;
	static public final String INCOME_ATTRIBUTE = "incomePerCapita";

	/** Who may be a member, and how members are drawn among them. */
	public enum Profile {
		/** Aged 16 to 65, all with the same chance (Schütze 2023). */
		UNIFORM(16, 65, false, false),
		/** Aged 18 to 45 with a work or education activity, all with the same chance. */
		COMMUTER(18, 45, true, false),
		/** As COMMUTER, with the chance proportional to household income per capita. */
		COMMUTER_INCOME(18, 45, true, true);

		public final int minimumAge;
		public final int maximumAge;
		public final boolean requiresWorkOrEducation;
		public final boolean incomeWeighted;

		Profile(int minimumAge, int maximumAge, boolean requiresWorkOrEducation, boolean incomeWeighted) {
			this.minimumAge = minimumAge;
			this.maximumAge = maximumAge;
			this.requiresWorkOrEducation = requiresWorkOrEducation;
			this.incomeWeighted = incomeWeighted;
		}

		/** Parses "uniform", "commuter" or "commuter_income" (also with a dash). */
		static public Profile parse(String value) {
			return valueOf(value.trim().toUpperCase().replace('-', '_'));
		}
	}

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

	static boolean isEligible(Person person, int age, Profile profile) {
		if (age < profile.minimumAge || age > profile.maximumAge) {
			return false;
		}

		return !profile.requiresWorkOrEducation || hasWorkOrEducation(person);
	}

	/** Whether the selected plan has a work or education activity. */
	static boolean hasWorkOrEducation(Person person) {
		if (person.getSelectedPlan() == null) {
			return false;
		}

		for (PlanElement element : person.getSelectedPlan().getPlanElements()) {
			if (element instanceof Activity activity
					&& (activity.getType().equals("work") || activity.getType().equals("education"))) {
				return true;
			}
		}

		return false;
	}

	/** Draw weight: 1, or the household income per capita (missing or negative counts as 0). */
	static double getWeight(Person person, Profile profile) {
		if (!profile.incomeWeighted) {
			return 1.0;
		}

		Object income = person.getAttributes().getAttribute(INCOME_ATTRIBUTE);
		double value = income instanceof Number number ? number.doubleValue()
				: income == null ? 0.0 : Double.parseDouble(income.toString());
		return Math.max(0.0, value);
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

	/** Draws the members with the {@link Profile#UNIFORM} profile. */
	static public int assign(Population population, Rates rates, long seed) {
		return assign(population, rates, Profile.UNIFORM, seed);
	}

	/**
	 * Draws the members and stores {@value #ATTRIBUTE} on every person. Each area
	 * gets rate x adults members, whatever the profile.
	 *
	 * @return the number of members
	 */
	static public int assign(Population population, Rates rates, Profile profile, long seed) {
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
		double[] weight = new double[areas];
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

			if (isEligible(person, age, profile)) {
				eligible[area.ordinal()]++;
				weight[area.ordinal()] += getWeight(person, profile);
			}
		}

		if (withArea == 0) {
			throw new IllegalStateException("No person has a home activity with a municipalityType attribute, "
					+ "so PubliBike members cannot be drawn by home area.");
		}

		// members per area = rate x adults, spread over the eligible persons by weight:
		// a person's chance is targetMembers x weight / total weight (at most 1)
		double[] scale = new double[areas];
		double[] targetMembers = new double[areas];

		for (Area area : Area.values()) {
			int a = area.ordinal();
			targetMembers[a] = rates.get(area) * adults[a];
			scale[a] = weight[a] > 0.0 ? targetMembers[a] / weight[a] : 0.0;
		}

		Random random = new Random(seed);
		int[] members = new int[areas];
		int memberTotal = 0;

		for (Person person : persons) {
			boolean member = false;
			Integer age = getAge(person);
			Area area = age == null ? null : getHomeArea(person);

			if (age != null && area != null && isEligible(person, age, profile)) {
				double probability = Math.min(1.0, scale[area.ordinal()] * getWeight(person, profile));
				member = random.nextDouble() < probability;

				if (member) {
					members[area.ordinal()]++;
					memberTotal++;
				}
			}

			person.getAttributes().putAttribute(ATTRIBUTE, member);
		}

		logger.info(String.format("PubliBike members (profile %s): %d of %d persons", profile, memberTotal,
				persons.size()));

		for (Area area : Area.values()) {
			int a = area.ordinal();
			logger.info(String.format(
					"  %s (rate %.2f%% of %d adults = %.0f): %d members of %d eligible persons aged %d-%d%s%s", area,
					100.0 * rates.get(area), adults[a], targetMembers[a], members[a], eligible[a],
					profile.minimumAge, profile.maximumAge,
					profile.requiresWorkOrEducation ? " with work or education" : "",
					profile.incomeWeighted ? ", weighted by income per capita" : ""));

			if (eligible[a] > 0 && targetMembers[a] > 0.0 && members[a] < 0.9 * targetMembers[a]) {
				logger.warn(String.format("  %s: fewer members than the rate gives (too few eligible persons, "
						+ "or chances capped at 1)", area));
			}
		}

		if (withoutArea > 0) {
			logger.warn(String.format("%d persons have no home municipality type and are not PubliBike members",
					withoutArea));
		}

		return memberTotal;
	}
}
