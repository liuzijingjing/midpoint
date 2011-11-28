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

package com.evolveum.midpoint.validator.test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.evolveum.midpoint.common.validator.EventHandler;
import com.evolveum.midpoint.common.validator.EventResult;
import com.evolveum.midpoint.common.validator.Validator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;

/**
 *
 * @author Radovan Semancik
 */
public class BasicValidatorTest {

    public static final String BASE_PATH = "src/test/resources/validator/";
	private static final String OBJECT_RESULT_OPERATION_NAME = BasicValidatorTest.class.getName() + ".validateObject";

    public BasicValidatorTest() {
    }

    @Test
    public void resource1Valid() throws Exception {
    	System.out.println("\n===[ resource1Valid ]=====");
    	
    	OperationResult result = new OperationResult(this.getClass().getName()+".resource1Valid");
    	
        validateFile("resource-1-valid.xml", result);
        AssertJUnit.assertTrue(result.isSuccess());

    }

    @Test
    public void handlerTest() throws Exception {
    	System.out.println("\n===[ handlerTest ]=====");

    	OperationResult result = new OperationResult(this.getClass().getName()+".handlerTest");
    	
        final List<String> postMarshallHandledOids = new ArrayList<String>();
        final List<String> preMarshallHandledOids = new ArrayList<String>();

        EventHandler handler = new EventHandler() {

			@Override
			public EventResult preMarshall(Element objectElement, Node postValidationTree, OperationResult objectResult) {
				preMarshallHandledOids.add(objectElement.getAttribute("oid"));
				return EventResult.cont();
			}

            @Override
            public EventResult postMarshall(ObjectType object, Element objectElement, OperationResult objectResult) {
            	System.out.println("Handler processing "+ObjectTypeUtil.toShortString(object)+", result:");
				System.out.println(objectResult.dump());
                postMarshallHandledOids.add(object.getOid());
                return EventResult.cont();
            }

			@Override
			public void handleGlobalError(OperationResult currentResult) {
				System.out.println("Handler got global error:");
				System.out.println(currentResult.dump());
			}

        };

        validateFile("three-objects.xml",handler,result);

        System.out.println(result.dump());
        AssertJUnit.assertTrue(result.isSuccess());
        AssertJUnit.assertTrue(postMarshallHandledOids.contains("c0c010c0-d34d-b33f-f00d-111111111111"));
        AssertJUnit.assertTrue(preMarshallHandledOids.contains("c0c010c0-d34d-b33f-f00d-111111111111"));
        AssertJUnit.assertTrue(postMarshallHandledOids.contains("c0c010c0-d34d-b33f-f00d-111111111112"));
        AssertJUnit.assertTrue(preMarshallHandledOids.contains("c0c010c0-d34d-b33f-f00d-111111111112"));
        AssertJUnit.assertTrue(postMarshallHandledOids.contains("c0c010c0-d34d-b33f-f00d-111111111113"));
        AssertJUnit.assertTrue(preMarshallHandledOids.contains("c0c010c0-d34d-b33f-f00d-111111111113"));
    }

    @Test
    public void notWellFormed() throws Exception {
    	System.out.println("\n===[ notWellFormed ]=====");

    	OperationResult result = new OperationResult(this.getClass().getName()+".notWellFormed");
    	
        validateFile("not-well-formed.xml",result);
        
        System.out.println(result.dump());
        AssertJUnit.assertFalse(result.isSuccess());
        AssertJUnit.assertTrue(result.getMessage().contains("Unexpected close tag"));
        // Check if line number is in the error
        AssertJUnit.assertTrue(result.getMessage().contains("48"));

    }

    @Test
    public void undeclaredPrefix() throws Exception {
    	System.out.println("\n===[ undeclaredPrefix ]=====");
    	
        OperationResult result = new OperationResult(this.getClass().getName()+".undeclaredPrefix");
        
        validateFile("undeclared-prefix.xml",result);
        
        System.out.println(result.dump());
        AssertJUnit.assertFalse(result.isSuccess());
        AssertJUnit.assertTrue(result.getMessage().contains("Undeclared namespace prefix"));
        // Check if line number is in the error
        AssertJUnit.assertTrue(result.getMessage().contains("42"));

    }

    @Test
    public void schemaViolation() throws Exception {
    	System.out.println("\n===[ schemaViolation ]=====");
    	
        OperationResult result = new OperationResult(this.getClass().getName()+".schemaViolation");
        
        validateFile("three-users-schema-violation.xml",result);
        
        System.out.println(result.dump());
        assertFalse(result.isSuccess());
        assertTrue(result.getSubresults().get(0).getMessage().contains("Invalid content was found starting with element 'i:foo'"));
        assertTrue(result.getSubresults().get(1).getMessage().contains("Invalid content was found starting with element 'i:familyName'"));
        assertTrue(result.getSubresults().get(2).getMessage().contains("Invalid content was found starting with element 'i:familyName'"));
    }

    /**
     * Same data as schemaViolation test, but this will set s lower threshold to stop after just two erros.
     */
    @Test
    public void testStopOnErrors() throws Exception {
    	System.out.println("\n===[ testStopOnErrors ]=====");
    	
        OperationResult result = new OperationResult(this.getClass().getName()+".testStopOnErrors");
        
        Validator validator = new Validator();
        validator.setVerbose(false);
        validator.setStopAfterErrors(2);
        
        validateFile("three-users-schema-violation.xml", null, validator, result);
        
        System.out.println(result.dump());
        assertFalse(result.isSuccess());
        assertEquals(2,result.getSubresults().size());
    }
    
    @Test
    public void noName() throws Exception {
    	System.out.println("\n===[ noName ]=====");

        OperationResult result = new OperationResult(this.getClass().getName()+".noName");
        
        validateFile("no-name.xml",result);

        System.out.println(result.dump());
        AssertJUnit.assertFalse(result.isSuccess());
        AssertJUnit.assertTrue(result.getSubresults().get(0).getSubresults().get(1).getMessage().contains("Empty property"));
        AssertJUnit.assertTrue(result.getSubresults().get(0).getSubresults().get(1).getMessage().contains("name"));

    }

    private void validateFile(String filename, OperationResult result) throws FileNotFoundException {
        validateFile(filename,null,result);
    }

    private void validateFile(String filename,EventHandler handler, OperationResult result) throws FileNotFoundException {
        Validator validator = new Validator();
        if (handler!=null) {
            validator.setHandler(handler);
        }
        validator.setVerbose(false);
        validateFile(filename, handler, validator, result);
    }
    
    private void validateFile(String filename,EventHandler handler, Validator validator, OperationResult result) throws FileNotFoundException {

        String filepath = BASE_PATH + filename;

        System.out.println("Validating " + filename);

        FileInputStream fis = null;

        File file = new File(filepath);
        fis = new FileInputStream(file);

		validator.validate(fis, result, OBJECT_RESULT_OPERATION_NAME);

        if (!result.isSuccess()) {
        	System.out.println("Errors:");
        	System.out.println(result.dump());
        } else {
            System.out.println("No errors");
            System.out.println(result.dump());
        }

    }

}
