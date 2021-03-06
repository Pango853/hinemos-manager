/*
 * Copyright (c) 2018 NTT DATA INTELLILINK Corporation. All rights reserved.
 *
 * Hinemos (http://www.hinemos.info/)
 *
 * See the LICENSE file for licensing information.
 */
package com.clustercontrol.xcloud.factory.task;

public enum CloudTaskStatus {
	pending,
	recovering,
	running,
	waiting,
	finished,
	failed
}
