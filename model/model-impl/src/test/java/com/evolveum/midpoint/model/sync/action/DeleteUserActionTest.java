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
package com.evolveum.midpoint.model.sync.action;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import java.io.File;

import javax.xml.bind.JAXBElement;

import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;

import com.evolveum.midpoint.model.sync.SynchronizationException;
import com.evolveum.midpoint.model.test.util.ModelTUtil;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.JAXBUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectChangeAdditionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowChangeDescriptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SynchronizationSituationType;

/**
 * 
 * @author lazyman
 * 
 */
@ContextConfiguration(locations = { "classpath:application-context-model.xml",
		"classpath:application-context-model-unit-test.xml", 
		"classpath:application-context-configuration-test-no-repo.xml",
		"classpath:application-context-task.xml" })
public class DeleteUserActionTest extends BaseActionTest {

	private static final File TEST_FOLDER = new File("./src/test/resources/sync/action/user");
	private static final Trace LOGGER = TraceManager.getTrace(DeleteUserActionTest.class);

	@BeforeMethod
	public void before() {
		Mockito.reset(provisioning, repository);
		before(new DeleteUserAction());
	}

	@Test(expectedExceptions = SynchronizationException.class)
	public void nullUserOid() throws Exception {
		testDeleteUser(null);
	}

	@Test(expectedExceptions = SynchronizationException.class)
	public void emptyUserOid() throws Exception {
		testDeleteUser("");
	}

	@Test(enabled = false)
	@SuppressWarnings("unchecked")
	private void testDeleteUser(String userOid) throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user-change.xml"))).getValue();

		OperationResult result = new OperationResult("Delete User Action Test");
		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.dump());
		}
	}

	@SuppressWarnings("unchecked")
	@Test(expectedExceptions = SynchronizationException.class)
	public void nonExistingUser() throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user-change.xml"))).getValue();
		String userOid = ModelTUtil.mockUser(repository, null, "1");
		
		OperationResult result = new OperationResult("Delete User Action Test");
		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.dump());
		}
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void correctDelete() throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "existing-user-change.xml"))).getValue();
		String userOid = ModelTUtil.mockUser(repository, new File(TEST_FOLDER, "existing-user.xml"), null);
		
		OperationResult result = new OperationResult("Delete User Action Test");
		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.dump());
		}
	}
}
