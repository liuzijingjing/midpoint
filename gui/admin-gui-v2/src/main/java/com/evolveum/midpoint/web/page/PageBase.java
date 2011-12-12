/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */

package com.evolveum.midpoint.web.page;

import com.evolveum.midpoint.web.MidPointApplication;
import com.evolveum.midpoint.web.component.login.LoginPanel;
import com.evolveum.midpoint.web.component.menu.top.TopMenu;
import com.evolveum.midpoint.web.component.menu.top.TopMenuItem;
import org.apache.wicket.devutils.debugbar.DebugBar;
import org.apache.wicket.markup.html.WebPage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
public abstract class PageBase extends WebPage {

    public PageBase() {
        initLayout();
    }

    private void initLayout() {
        DebugBar debugBar = new DebugBar("debugPanel");
        debugBar.setVisible(getApplication().usesDevelopmentConfig());
        add(debugBar);

        add(new LoginPanel("loginPanel"));

        List<TopMenuItem> topMenuItems = getTopMenuItems();
        if (topMenuItems == null) {
            topMenuItems = new ArrayList<TopMenuItem>();
        }
        add(new TopMenu("topMenu", topMenuItems));
    }

    public MidPointApplication getMidpointApplication() {
        return (MidPointApplication) getApplication();
    }

    public abstract List<TopMenuItem> getTopMenuItems();
}
