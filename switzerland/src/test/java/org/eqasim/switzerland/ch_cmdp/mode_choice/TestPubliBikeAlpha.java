package org.eqasim.switzerland.ch_cmdp.mode_choice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eqasim.core.simulation.mode_choice.ParameterDefinition;
import org.eqasim.switzerland.ch_cmdp.mode_choice.parameters.SwissCmdpModeParameters;
import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikePt;
import org.eqasim.switzerland.ch_cmdp.sharing.PubliBikeSharing;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestPubliBikeAlpha {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testAlphaCalibratorCanReadAndWritePubliBikeAlpha() {
		SwissCmdpModeParameters parameters = SwissCmdpModeParameters.buildDefault();
		parameters.publibike.alpha_u = 1.9;
		assertEquals(1.9, parameters.getASCs().get(PubliBikeSharing.SERVICE_MODE), 1e-12);

		double carAlpha = parameters.car.alpha_u;
		parameters.setASCs(Map.of(PubliBikeSharing.SERVICE_MODE, 2.3));
		assertEquals(2.3, parameters.publibike.alpha_u, 1e-12);
		assertEquals(carAlpha, parameters.car.alpha_u, 1e-12); // modes missing from the map keep their alpha
	}

	@Test
	public void testCombinedTripsHaveTheirOwnAlpha() {
		SwissCmdpModeParameters parameters = SwissCmdpModeParameters.buildDefault();
		parameters.publibike.alpha_u = 1.9;
		parameters.publibike.alphaPt_u = -0.4;
		assertEquals(-0.4, parameters.getASCs().get(PubliBikePt.CALIBRATED_MODE), 1e-12);

		parameters.setASCs(Map.of(PubliBikePt.CALIBRATED_MODE, 0.7));
		assertEquals(0.7, parameters.publibike.alphaPt_u, 1e-12);
		assertEquals(1.9, parameters.publibike.alpha_u, 1e-12);
	}

	@Test
	public void testBothDirectionsCountAsOneCalibratedMode() {
		for (String mode : PubliBikePt.MODES) {
			assertEquals(PubliBikePt.CALIBRATED_MODE,
					PubliBikePt.calibrationModes().getOrDefault(mode, mode));
		}
		// PubliBike-only trips are counted on their own
		assertTrue(!PubliBikePt.calibrationModes().containsKey(PubliBikeSharing.SERVICE_MODE));
	}

	@Test
	public void testPubliBikeAlphasSurviveTheParameterFile() throws IOException {
		SwissCmdpModeParameters parameters = SwissCmdpModeParameters.buildDefault();
		parameters.publibike.alpha_u = 1.9;
		parameters.publibike.alphaPt_u = -0.4;
		File file = folder.newFile("mode_parameters.yml");
		parameters.saveToYamlFile(file.getPath());

		SwissCmdpModeParameters loaded = SwissCmdpModeParameters.buildDefault();
		ParameterDefinition.applyFile(file, loaded);
		assertEquals(1.9, loaded.publibike.alpha_u, 1e-12);
		assertEquals(-0.4, loaded.publibike.alphaPt_u, 1e-12);
	}
}
