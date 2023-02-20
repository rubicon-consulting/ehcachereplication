/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package us.rubicon_consulting.controller;

import static us.rubicon_consulting.constants.EhcachereplicationConstants.PLATFORM_LOGO_CODE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import us.rubicon_consulting.service.EhcachereplicationService;


@Controller
public class EhcachereplicationHelloController
{
	@Autowired
	private EhcachereplicationService ehcachereplicationService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String printWelcome(final ModelMap model)
	{
		model.addAttribute("logoUrl", ehcachereplicationService.getHybrisLogoUrl(PLATFORM_LOGO_CODE));
		return "welcome";
	}
}
