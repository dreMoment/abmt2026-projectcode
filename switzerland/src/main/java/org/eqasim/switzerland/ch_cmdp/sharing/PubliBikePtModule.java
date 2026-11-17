package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikePtRoutingModule.Direction;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.shared_mobility.io.SharingServiceSpecification;
import org.matsim.contrib.shared_mobility.run.SharingModes;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.utils.timing.TimeInterpretation;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/** Routing for the PubliBike + public transport modes, see {@link PubliBikePt}. */
public class PubliBikePtModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger(PubliBikePtModule.class);

	@Override
	public void install() {
		addRoutingModuleBinding(PubliBikePt.ACCESS_MODE)
				.toProvider(new RoutingProvider(PubliBikePt.ACCESS_MODE, Direction.ACCESS));
		addRoutingModuleBinding(PubliBikePt.EGRESS_MODE)
				.toProvider(new RoutingProvider(PubliBikePt.EGRESS_MODE, Direction.EGRESS));
	}

	@Provides
	@Singleton
	PubliBikeTransferStations provideTransferStations(Scenario scenario, Injector injector) {
		SharingServiceSpecification service = injector.getInstance(
				Key.get(SharingServiceSpecification.class, SharingModes.mode(PubliBikeSharing.SERVICE_MODE)));

		PubliBikeTransferStations stations = PubliBikeTransferStations.create(service.getStations(),
				scenario.getTransitSchedule(), scenario.getNetwork(), PubliBikePt.TRANSFER_RADIUS_M);

		logger.info(String.format("%d of %d PubliBike stations are within %.0f m of a railway stop",
				stations.size(), service.getStations().size(), PubliBikePt.TRANSFER_RADIUS_M));
		return stations;
	}

	static private class RoutingProvider implements Provider<RoutingModule> {
		private final String mode;
		private final Direction direction;

		@Inject
		Map<String, Provider<RoutingModule>> routingModules;
		@Inject
		Provider<PubliBikeTransferStations> transferStations;
		@Inject
		Scenario scenario;
		@Inject
		TimeInterpretation timeInterpretation;

		RoutingProvider(String mode, Direction direction) {
			this.mode = mode;
			this.direction = direction;
		}

		@Override
		public RoutingModule get() {
			return new PubliBikePtRoutingModule(mode, direction,
					routingModules.get(PubliBikeSharing.SERVICE_MODE).get(),
					routingModules.get(TransportMode.pt).get(), transferStations.get(),
					scenario.getPopulation().getFactory(), timeInterpretation);
		}
	}
}
