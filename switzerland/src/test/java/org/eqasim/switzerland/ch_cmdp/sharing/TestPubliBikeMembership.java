package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

public class TestPubliBikeMembership {
	static private Person addPerson(Population population, int index, int age, String municipalityType) {
		Person person = population.getFactory().createPerson(Id.createPersonId(index));
		person.getAttributes().putAttribute("age", age);

		Plan plan = population.getFactory().createPlan();
		Activity home = population.getFactory().createActivityFromCoord("home", new Coord(0.0, 0.0));
		if (municipalityType != null) {
			home.getAttributes().putAttribute("municipalityType", municipalityType);
		}
		plan.addActivity(home);
		person.addPlan(plan);
		person.setSelectedPlan(plan);

		population.addPerson(person);
		return person;
	}

	static private Person addCommuter(Population population, int index, int age, String municipalityType,
			String activityType) {
		Person person = addPerson(population, index, age, municipalityType);
		Plan plan = person.getSelectedPlan();
		plan.addLeg(population.getFactory().createLeg("walk"));
		plan.addActivity(population.getFactory().createActivityFromCoord(activityType, new Coord(100.0, 0.0)));
		return person;
	}

	static private long countMembers(Population population) {
		return population.getPersons().values().stream().filter(PubliBikeMembership::isMember).count();
	}

	static private int countMembers(Population population, int minimumAge, int maximumAge, String type) {
		return (int) population.getPersons().values().stream() //
				.filter(p -> (int) p.getAttributes().getAttribute("age") >= minimumAge) //
				.filter(p -> (int) p.getAttributes().getAttribute("age") <= maximumAge) //
				.filter(p -> type.equals(((Activity) p.getSelectedPlan().getPlanElements().get(0)).getAttributes()
						.getAttribute("municipalityType"))) //
				.filter(PubliBikeMembership::isMember).count();
	}

	@Test
	public void eachAreaGetsRateTimesAdultsMembersAged16To65() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		Map<String, Double> rates = Map.of("urban", 0.020, "suburban", 0.009, "rural", 0.005);
		int perAgeAndArea = 50000;
		int index = 0;

		// adults: 20, 40, 70; aged 16 to 65: 16, 20, 40
		for (String type : rates.keySet()) {
			for (int age : new int[] { 10, 16, 20, 40, 70 }) {
				for (int i = 0; i < perAgeAndArea; i++) {
					addPerson(population, index++, age, type);
				}
			}
		}

		PubliBikeMembership.assign(population, new PubliBikeMembership.Rates(), 1234);

		for (Map.Entry<String, Double> entry : rates.entrySet()) {
			String type = entry.getKey();
			double expectedMembers = entry.getValue() * 3 * perAgeAndArea;
			assertEquals(expectedMembers, countMembers(population, 0, 200, type), 0.1 * expectedMembers);

			// nobody outside 16-65, and the same chance for every age inside
			assertEquals(0, countMembers(population, 0, 15, type));
			assertEquals(0, countMembers(population, 66, 200, type));
			int young = countMembers(population, 16, 16, type);
			int middle = countMembers(population, 40, 40, type);
			assertEquals(1.0, (double) young / middle, 0.3);
		}
	}

	@Test
	public void theAgeLimitsAre16And65() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		PubliBikeMembership.Rates rates = new PubliBikeMembership.Rates();
		rates.urban = 1.0;

		Person age15 = addPerson(population, 0, 15, "urban");
		Person age16 = addPerson(population, 1, 16, "urban");
		Person age65 = addPerson(population, 2, 65, "urban");
		Person age66 = addPerson(population, 3, 66, "urban");

		PubliBikeMembership.assign(population, rates, 0);

		assertFalse(PubliBikeMembership.isMember(age15));
		assertTrue(PubliBikeMembership.isMember(age16));
		assertTrue(PubliBikeMembership.isMember(age65));
		assertFalse(PubliBikeMembership.isMember(age66));
	}

	@Test
	public void personsWithoutHomeTypeOrFreightAreNotMembers() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		PubliBikeMembership.Rates rates = new PubliBikeMembership.Rates();
		rates.urban = 1.0;

		Person urban = addPerson(population, 0, 30, "urbancore");
		Person noType = addPerson(population, 1, 30, null);
		Person freight = addPerson(population, 2, 30, "urban");
		freight.getAttributes().putAttribute("isFreight", true);

		PubliBikeMembership.assign(population, rates, 0);

		assertEquals(true, PubliBikeMembership.isMember(urban));
		assertFalse(PubliBikeMembership.isMember(noType));
		assertFalse(PubliBikeMembership.isMember(freight));
	}

	@Test
	public void theSameSeedGivesTheSameMembers() {
		Population first = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		Population second = PopulationUtils.createPopulation(ConfigUtils.createConfig());

		for (int i = 0; i < 5000; i++) {
			addPerson(first, i, 30, "urban");
			addPerson(second, i, 30, "urban");
		}

		PubliBikeMembership.assign(first, new PubliBikeMembership.Rates(), 42);
		PubliBikeMembership.assign(second, new PubliBikeMembership.Rates(), 42);

		for (Person person : first.getPersons().values()) {
			assertEquals(PubliBikeMembership.isMember(person),
					PubliBikeMembership.isMember(second.getPersons().get(person.getId())));
		}
	}

	@Test(expected = IllegalStateException.class)
	public void aPopulationWithoutHomeTypesFailsLoudly() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		addPerson(population, 0, 30, null);
		PubliBikeMembership.assign(population, new PubliBikeMembership.Rates(), 0);
	}

	@Test
	public void commutersAre18To45WithWorkOrEducation() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		PubliBikeMembership.Rates rates = new PubliBikeMembership.Rates();
		rates.urban = 1.0;

		Person worker = addCommuter(population, 0, 30, "urban", "work");
		Person student = addCommuter(population, 1, 18, "urban", "education");
		Person age45 = addCommuter(population, 2, 45, "urban", "work");
		Person age17 = addCommuter(population, 3, 17, "urban", "education");
		Person age46 = addCommuter(population, 4, 46, "urban", "work");
		Person shopper = addCommuter(population, 5, 30, "urban", "shop");
		Person stayAtHome = addPerson(population, 6, 30, "urban");

		PubliBikeMembership.assign(population, rates, PubliBikeMembership.Profile.COMMUTER, 0);

		assertTrue(PubliBikeMembership.isMember(worker));
		assertTrue(PubliBikeMembership.isMember(student));
		assertTrue(PubliBikeMembership.isMember(age45));
		assertFalse(PubliBikeMembership.isMember(age17));
		assertFalse(PubliBikeMembership.isMember(age46));
		assertFalse(PubliBikeMembership.isMember(shopper));
		assertFalse(PubliBikeMembership.isMember(stayAtHome));
	}

	@Test
	public void commutersGetTheSameNumberOfMembersAsUniform() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		int index = 0;

		// 100000 adults, of which 20000 are eligible commuters
		for (int i = 0; i < 20000; i++) {
			addCommuter(population, index++, 30, "urban", "work");
		}
		for (int i = 0; i < 40000; i++) {
			addCommuter(population, index++, 60, "urban", "work");
		}
		for (int i = 0; i < 40000; i++) {
			addPerson(population, index++, 30, "urban");
		}

		PubliBikeMembership.assign(population, new PubliBikeMembership.Rates(), PubliBikeMembership.Profile.COMMUTER,
				7);

		// 2% of 100000 adults = 2000, all among the 20000 commuters aged 30
		assertEquals(2000.0, countMembers(population), 200.0);
		assertEquals(countMembers(population), countMembers(population, 30, 30, "urban"));
		assertEquals(0, population.getPersons().values().stream()
				.filter(p -> !PubliBikeMembership.hasWorkOrEducation(p)).filter(PubliBikeMembership::isMember)
				.count());
	}

	@Test
	public void incomeWeightingFavoursHigherIncomes() {
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		int index = 0;

		for (int i = 0; i < 50000; i++) {
			addCommuter(population, index++, 30, "urban", "work").getAttributes()
					.putAttribute(PubliBikeMembership.INCOME_ATTRIBUTE, 3000.0);
			addCommuter(population, index++, 31, "urban", "work").getAttributes()
					.putAttribute(PubliBikeMembership.INCOME_ATTRIBUTE, 9000.0);
		}

		PubliBikeMembership.assign(population, new PubliBikeMembership.Rates(),
				PubliBikeMembership.Profile.COMMUTER_INCOME, 3);

		// 2% of 100000 = 2000 members, split 1:3 by income
		int low = countMembers(population, 30, 30, "urban");
		int high = countMembers(population, 31, 31, "urban");
		assertEquals(2000.0, low + high, 200.0);
		assertEquals(3.0, (double) high / low, 0.5);
	}

	@Test
	public void uniformIsTheDefaultProfile() {
		Population first = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		Population second = PopulationUtils.createPopulation(ConfigUtils.createConfig());

		for (int i = 0; i < 5000; i++) {
			addCommuter(first, i, 20 + i % 50, "urban", i % 2 == 0 ? "work" : "shop");
			addCommuter(second, i, 20 + i % 50, "urban", i % 2 == 0 ? "work" : "shop");
		}

		PubliBikeMembership.assign(first, new PubliBikeMembership.Rates(), 42);
		PubliBikeMembership.assign(second, new PubliBikeMembership.Rates(), PubliBikeMembership.Profile.UNIFORM, 42);

		for (Person person : first.getPersons().values()) {
			assertEquals(PubliBikeMembership.isMember(person),
					PubliBikeMembership.isMember(second.getPersons().get(person.getId())));
		}
	}

	@Test
	public void profilesParseFromTheCommandLine() {
		assertEquals(PubliBikeMembership.Profile.COMMUTER, PubliBikeMembership.Profile.parse("commuter"));
		assertEquals(PubliBikeMembership.Profile.COMMUTER_INCOME,
				PubliBikeMembership.Profile.parse("commuter-income"));
		assertEquals(PubliBikeMembership.Profile.UNIFORM, PubliBikeMembership.Profile.parse("Uniform"));
	}
}
