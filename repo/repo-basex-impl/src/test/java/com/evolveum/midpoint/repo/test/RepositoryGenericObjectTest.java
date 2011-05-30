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
 * Portions Copyrighted 2010 Forgerock
 */

package com.evolveum.midpoint.repo.test;

import com.evolveum.midpoint.common.DOMUtil;
import com.evolveum.midpoint.common.jaxb.JAXBUtil;
import com.evolveum.midpoint.common.test.XmlAsserts;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_1.GenericObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectContainerType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.repository.repository_1.RepositoryPortType;
import com.evolveum.midpoint.xml.schema.SchemaConstants;
import java.io.File;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.Element;

import static org.junit.Assert.*;

/**
 *
 * @author Igor Farinic
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"../../../../../application-context-repository.xml", "../../../../../application-context-repository-test.xml"})
public class RepositoryGenericObjectTest {

    @Autowired(required = true)
    private RepositoryPortType repositoryService;

    public RepositoryPortType getRepositoryService() {
        return repositoryService;
    }

    public void setRepositoryService(RepositoryPortType repositoryService) {
        this.repositoryService = repositoryService;
    }

    public RepositoryGenericObjectTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    private void checkObject(GenericObjectType genericObject, GenericObjectType retrievedObject) throws Exception {
        assertEquals(genericObject.getOid(), retrievedObject.getOid());
        assertEquals(genericObject.getName(), retrievedObject.getName());
        assertEquals(genericObject.getObjectType(), retrievedObject.getObjectType());
        assertEquals(genericObject.getExtension().getAny().size(), retrievedObject.getExtension().getAny().size());
        assertEquals(genericObject.getExtension().getAny().get(1).getLocalName(), retrievedObject.getExtension().getAny().get(1).getLocalName());
        List<Element> extensionElements = genericObject.getExtension().getAny();
        int i = 0;
        for (Element element: extensionElements) {
    		XmlAsserts.assertPatch(DOMUtil.serializeDOMToString(element), DOMUtil.serializeDOMToString(retrievedObject.getExtension().getAny().get(i)));
    		i++;
		}
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testGenericObject() throws Exception {
        final String genericObjectOid = "c0c010c0-d34d-b33f-f00d-999111111111";
        try {
        	
        	//create object
            ObjectContainerType objectContainer = new ObjectContainerType();
            GenericObjectType genericObject = ((JAXBElement<GenericObjectType>) JAXBUtil.unmarshal(new File("src/test/resources/generic-object.xml"))).getValue();
            objectContainer.setObject(genericObject);
            repositoryService.addObject(objectContainer);
            
            //get object
            ObjectContainerType retrievedObjectContainer = repositoryService.getObject(genericObjectOid, new PropertyReferenceListType());
            checkObject(genericObject, (GenericObjectType) retrievedObjectContainer.getObject());
            
            //list objects of type
            ObjectListType objects = repositoryService.listObjects(QNameUtil.qNameToUri(SchemaConstants.I_GENERIC_OBJECT_TYPE), new PagingType());
            assertNotNull(objects);
            assertNotNull(objects.getObject());
            assertEquals(1, objects.getObject().size());
            checkObject(genericObject, (GenericObjectType) objects.getObject().get(0));
            
            //delete object
            repositoryService.deleteObject(genericObjectOid);
            
        } finally {
        	//to be sure try to delete the object as part of cleanup
        	try {
        		repositoryService.deleteObject(genericObjectOid);
        	} catch (Exception ex) {
        		//ignore exceptions during cleanup
        	}
        }
    }
}
