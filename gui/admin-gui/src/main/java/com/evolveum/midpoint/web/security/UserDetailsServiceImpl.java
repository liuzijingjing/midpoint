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

package com.evolveum.midpoint.web.security;

import java.math.BigInteger;
import java.util.GregorianCalendar;
import java.util.List;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.evolveum.midpoint.common.diff.CalculateXmlDiff;
import com.evolveum.midpoint.common.diff.DiffException;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.JAXBUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.model.RepositoryException;
import com.evolveum.midpoint.xml.ns._public.common.common_1.CredentialsType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectFactory;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PasswordType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.QueryType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;

/**
 * 
 * @author lazyman
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private static final Trace LOGGER = TraceManager.getTrace(UserDetailsServiceImpl.class);
	@Autowired(required = true)
	private transient ModelService modelService;

	@Override
	public PrincipalUser getUser(String principal) {
		PrincipalUser user = null;
		try {
			user = findByUsername(principal);
		} catch (Exception ex) {
			LOGGER.warn("Couldn't find user with name '{}', reason: {}.",
					new Object[] { principal, ex.getMessage() });
		}

		return user;
	}

	@Override
	public void updateUser(PrincipalUser user) {
		try {
			save(user);
		} catch (RepositoryException ex) {
			LOGGER.warn("Couldn't save user '{}, ({})', reason: {}.",
					new Object[] { user.getFullName(), user.getOid(), ex.getMessage() });
		}
	}

	private PrincipalUser findByUsername(String username) throws SchemaException, ObjectNotFoundException {
		QueryType query = new QueryType();
		query.setFilter(createQuery(username));
		LOGGER.trace("Looking for user, query:\n" + DOMUtil.printDom(query.getFilter()));

		List<UserType> list = modelService.searchObjects(UserType.class, query, new PagingType(),
				new OperationResult("Find by username"));
		if (list == null) {
			return null;
		}
		LOGGER.trace("Users found: {}.", new Object[] { list.size() });
		if (list.size() == 0 || list.size() > 1) {
			return null;
		}

		return createUser(list.get(0));
	}

	private PrincipalUser createUser(UserType userType) {
		boolean enabled = false;
		CredentialsType credentialsType = userType.getCredentials();
		if (credentialsType != null && credentialsType.isAllowedIdmGuiAccess() != null) {
			enabled = credentialsType.isAllowedIdmGuiAccess();
		}

		PrincipalUser user = new PrincipalUser(userType.getOid(), userType.getName(), enabled);
		user.setFamilyName(userType.getFamilyName());
		user.setFullName(userType.getFullName());
		user.setGivenName(userType.getGivenName());

		if (credentialsType != null && credentialsType.getPassword() != null) {
			PasswordType password = credentialsType.getPassword();

			Credentials credentials = user.getCredentials();
			credentials.setPassword(password.getProtectedString());
			if (password.getFailedLogins() == null || password.getFailedLogins().intValue() < 0) {
				credentials.setFailedLogins(0);
			} else {
				credentials.setFailedLogins(password.getFailedLogins().intValue());
			}
			XMLGregorianCalendar calendar = password.getLastFailedLoginTimestamp();
			if (calendar != null) {
				credentials.setLastFailedLoginAttempt(calendar.toGregorianCalendar().getTimeInMillis());
			} else {
				credentials.setLastFailedLoginAttempt(0);
			}
		}

		return user;
	}

	private Element createQuery(String username) {
		Document document = DOMUtil.getDocument();
		Element and = document.createElementNS(SchemaConstants.NS_C, "c:and");
		document.appendChild(and);

		Element type = document.createElementNS(SchemaConstants.NS_C, "c:type");
		type.setAttribute("uri", "http://midpoint.evolveum.com/xml/ns/public/common/common-1.xsd#UserType");
		and.appendChild(type);

		Element equal = document.createElementNS(SchemaConstants.NS_C, "c:equal");
		and.appendChild(equal);
		Element value = document.createElementNS(SchemaConstants.NS_C, "c:value");
		equal.appendChild(value);
		Element name = document.createElementNS(SchemaConstants.NS_C, "c:name");
		name.setTextContent(username);
		value.appendChild(name);

		return and;
	}

	private PrincipalUser save(PrincipalUser person) throws RepositoryException {
		try {
			UserType userType = getUserByOid(person.getOid());
			UserType oldUserType = (UserType) JAXBUtil.clone(userType);

			updateUserType(userType, person);

			ObjectModificationType modification = CalculateXmlDiff.calculateChanges(oldUserType, userType);
			if (modification != null && modification.getOid() != null) {
				modelService.modifyObject(UserType.class, modification, new OperationResult("Save user"));
			}

		} catch (DiffException ex) {
			throw new RepositoryException("Can't save user. Unexpected error: "
					+ "Couldn't create create diff.", ex);
		} catch (JAXBException ex) {
			// TODO: finish
		} catch (Exception ex) {
			throw new RepositoryException(ex.getMessage(), ex);
		}

		return null;
	}

	private UserType getUserByOid(String oid) throws ObjectNotFoundException, SchemaException {
		ObjectType object = modelService.getObject(UserType.class, oid, new PropertyReferenceListType(),
				new OperationResult("Get user by oid"));
		if (object != null && (object instanceof UserType)) {
			return (UserType) object;
		}

		return null;
	}

	private void updateUserType(UserType userType, PrincipalUser user) {
		CredentialsType credentials = userType.getCredentials();
		if (credentials == null) {
			credentials = new CredentialsType();
			userType.setCredentials(credentials);
		}
		PasswordType password = credentials.getPassword();
		if (password == null) {
			password = new PasswordType();
			credentials.setPassword(password);
		}

		password.setFailedLogins(new BigInteger(Integer.toString(user.getCredentials().getFailedLogins())));

		try {
			GregorianCalendar gc = new GregorianCalendar();
			gc.setTimeInMillis(user.getCredentials().getLastFailedLoginAttempt());
			XMLGregorianCalendar calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
			password.setLastFailedLoginTimestamp(calendar);
		} catch (DatatypeConfigurationException ex) {
			LOGGER.error("Can't save last failed login timestamp, reason: " + ex.getMessage());
		}
	}
}
