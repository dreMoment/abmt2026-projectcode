package org.eqasim.switzerland.ch_cmdp.sharing;

import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.router.DefaultRoutingModules;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Routes the shared bike leg over the links that allow bikes, with the
 * slope-based speed and route preferences of network-routed bike. Without a
 * vehicle, the speed has no crossing penalties.
 *
 * The mode is not a QSim main mode, so the QSim teleports the rider, taking the
 * routed travel time and distance. Car congestion never delays the bike. The
 * pure network router needs no person vehicle, unlike MATSim's default one.
 */
public class PubliBikeRoutingModule extends AbstractModule {
	@Override
	public void install() {
		addRoutingModuleBinding(PubliBikeSharing.MODE).toProvider(RoutingProvider.class);
	}

	static private class RoutingProvider implements Provider<RoutingModule> {
		@Inject
		Scenario scenario;
		@Inject
		SingleModeNetworksCache networks;
		@Inject
		Map<String, TravelTime> travelTimes;
		@Inject
		Map<String, TravelDisutilityFactory> travelDisutilityFactories;
		@Inject
		LeastCostPathCalculatorFactory pathCalculatorFactory;

		@Override
		public RoutingModule get() {
			Network network = networks.getOrCreateSingleModeNetwork(PubliBikeSharing.MODE);
			TravelTime travelTime = travelTimes.get(TransportMode.bike);

			return DefaultRoutingModules.createPureNetworkRouter(PubliBikeSharing.MODE,
					scenario.getPopulation().getFactory(), network,
					pathCalculatorFactory.createPathCalculator(network,
							travelDisutilityFactories.get(TransportMode.bike).createTravelDisutility(travelTime),
							travelTime));
		}
	}
}
