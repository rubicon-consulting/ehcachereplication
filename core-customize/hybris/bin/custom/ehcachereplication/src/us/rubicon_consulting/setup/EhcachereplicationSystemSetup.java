/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package us.rubicon_consulting.setup;

import static us.rubicon_consulting.constants.EhcachereplicationConstants.PLATFORM_LOGO_CODE;

import de.hybris.platform.core.initialization.SystemSetup;

import java.io.InputStream;

import us.rubicon_consulting.constants.EhcachereplicationConstants;
import us.rubicon_consulting.service.EhcachereplicationService;


@SystemSetup(extension = EhcachereplicationConstants.EXTENSIONNAME)
public class EhcachereplicationSystemSetup
{
	private final EhcachereplicationService ehcachereplicationService;

	public EhcachereplicationSystemSetup(final EhcachereplicationService ehcachereplicationService)
	{
		this.ehcachereplicationService = ehcachereplicationService;
	}

	@SystemSetup(process = SystemSetup.Process.INIT, type = SystemSetup.Type.ESSENTIAL)
	public void createEssentialData()
	{
		ehcachereplicationService.createLogo(PLATFORM_LOGO_CODE);
	}

	private InputStream getImageStream()
	{
		return EhcachereplicationSystemSetup.class.getResourceAsStream("/ehcachereplication/sap-hybris-platform.png");
	}
}
