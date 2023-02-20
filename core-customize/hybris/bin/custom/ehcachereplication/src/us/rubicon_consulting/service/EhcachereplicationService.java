/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package us.rubicon_consulting.service;

public interface EhcachereplicationService
{
	String getHybrisLogoUrl(String logoCode);

	void createLogo(String logoCode);
}
