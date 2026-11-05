/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.util.List;

/**
 * What the server tells about PlantUML in its answer to <code>markdown/environment</code>.
 *
 * @param version PlantUML's version
 * @param securityProfile the security profile PlantUML applies
 * @param urlAllowList the addresses PlantUML may read, empty if none
 * @param dotExecutable the Graphviz <code>dot</code> executable PlantUML runs, <code>null</code> if it
 *        finds none
 * @param graphvizVersion the version of that Graphviz, <code>null</code> if it is unknown, e.g. since
 *        the executable cannot be run
 */
record PlantUmlEnvironment(String version, String securityProfile, List<String> urlAllowList,
		String dotExecutable, String graphvizVersion) {
}
