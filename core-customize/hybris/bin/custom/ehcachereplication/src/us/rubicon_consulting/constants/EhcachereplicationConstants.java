/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package us.rubicon_consulting.constants;

/**
 * Global class for all Ehcachereplication constants. You can add global constants for your extension into this class.
 */
public final class EhcachereplicationConstants extends GeneratedEhcachereplicationConstants
{
	public static final String EXTENSIONNAME = "ehcachereplication";

	private EhcachereplicationConstants()
	{
		//empty to avoid instantiating this constant class
	}

	// implement here constants used by this extension

	public static final String PLATFORM_LOGO_CODE = "ehcachereplicationPlatformLogo";

	public final class EhcacheConfigConstants {
		public static final String JGROUPS_UDP_MCAST_ADDR = "ehcachereplication.jgroups.udp.mcast_addr";
		public static final String JGROUPS_UDP_MCAST_PORT = "ehcachereplication.jgroups.udp.mcast_port";
		public static final String JGROUPS_UDP_IP_TTL = "ehcachereplication.jgroups.udp.ip_ttl";
	}
}
