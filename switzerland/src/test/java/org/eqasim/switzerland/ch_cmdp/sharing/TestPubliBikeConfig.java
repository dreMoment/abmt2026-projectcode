package org.eqasim.switzerland.ch_cmdp.sharing;

import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.junit.Test;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class TestPubliBikeConfig {
	/**
	 * The tour-based model estimates a trip once per mode combination of its tour unless the mode is cached. The
	 * PubliBike modes route up to several transfer stations per trip, so without the cache mode choice is many
	 * times slower.
	 */
	@Test
	public void testPubliBikeModesAreCached() {
		Config config = ConfigUtils.createConfig(new EqasimConfigGroup(), new DiscreteModeChoiceConfigGroup());
		DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules()
				.get(DiscreteModeChoiceConfigGroup.GROUP_NAME);
		dmcConfig.setCachedModes(List.of("car", "pt"));

		PubliBikeSharing.configure(config, "publibike_service.xml");
		PubliBikePt.configure(config);

		Collection<String> cachedModes = dmcConfig.getCachedModes();
		assertTrue(cachedModes.contains(PubliBikeSharing.SERVICE_MODE));
		assertTrue(cachedModes.containsAll(PubliBikePt.MODES));
		assertTrue(cachedModes.contains("car") && cachedModes.contains("pt"));
	}
}
